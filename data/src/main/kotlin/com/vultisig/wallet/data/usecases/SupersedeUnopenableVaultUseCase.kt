package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.IoDispatcher
import com.vultisig.wallet.data.db.dao.VaultDao
import com.vultisig.wallet.data.db.models.KeyShareEntity
import com.vultisig.wallet.data.db.models.VaultEntity
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.passcode.KeyShareCipher
import com.vultisig.wallet.data.passcode.PasscodeRepository
import com.vultisig.wallet.data.passcode.PasscodeState
import com.vultisig.wallet.data.repositories.LastOpenedVaultRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.repositories.order.VaultOrderRepository
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Stores a backup in place of the stored vault it restores, when that vault is one this device can
 * no longer open.
 *
 * Without it the backup is refused as a duplicate: the orphaned row still resolves to a `Vault`, so
 * the import's collision check finds it, while its sealed keyshares hold every later launch in
 * [PasscodeState.KeyUnavailable].
 */
interface SupersedeUnopenableVaultUseCase {
    /** True when [backup] was stored in place of a vault, false when nothing changed. */
    suspend operator fun invoke(backup: Vault): Boolean
}

internal class SupersedeUnopenableVaultUseCaseImpl
@Inject
constructor(
    private val vaultDao: VaultDao,
    private val vaultRepository: VaultRepository,
    private val vaultOrderRepository: VaultOrderRepository,
    private val lastOpenedVaultRepository: LastOpenedVaultRepository,
    private val keyShareCipher: KeyShareCipher,
    private val passcodeRepository: PasscodeRepository,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) : SupersedeUnopenableVaultUseCase {

    override suspend fun invoke(backup: Vault): Boolean =
        withContext(dispatcher) {
            val superseded = supersededBy(backup) ?: return@withContext false

            vaultRepository.replace(superseded.id, backup)
            // Two pointers at the row that just went, neither of them a foreign key. Home reads the
            // last-opened id once, so leaving it dangling keeps that screen — and the sends it
            // routes — on a vault that is gone. The row is deleted by the time these run, so they
            // are not the caller's to cancel.
            withContext(NonCancellable) {
                vaultOrderRepository.delete(parentId = null, name = superseded.id)
                lastOpenedVaultRepository.setLastOpenedVaultId(backup.id)
            }
            Timber.i("Restored a vault whose keyshares this device could no longer open")
            true
        }

    /**
     * The one stored vault [backup] is allowed to replace, or null when there is none.
     *
     * This re-enables the replacement the import gate exists to refuse, so every clause is the
     * difference between a recovery and a loss:
     * - Two colliding vaults is a question this cannot answer, and picking one destroys the other's
     *   key material.
     * - A row holding a share in the clear is one this device can still read.
     * - Matching one public key makes a duplicate; it takes every identity the row holds to make a
     *   replacement. An identity the backup adds is fine, one it leaves out is key material lost.
     * - An empty or blank-filled `keyshares` list round-trips perfectly well and would buy the
     *   deletion with nothing.
     *
     * Nothing here parses a share to confirm its bytes derive the key it is filed under: that is
     * work behind the TSS boundary, and the ordinary import path extends a backup the same trust.
     */
    private suspend fun supersededBy(backup: Vault): VaultEntity? {
        // Re-read before anything is deleted rather than trusting what a launch hours ago found; a
        // keystore that has come back resolves to Locked here instead of after the row is gone.
        // [PasscodeState.StoreUnavailable] must never qualify — there the wrap is probably still on
        // disk. Together these stand in for the "was the wrapped key absent" question iOS asks its
        // keychain, which this store cannot answer: a value it fails to decrypt reads like one that
        // was never written.
        passcodeRepository.retry()
        if (passcodeRepository.state.value != PasscodeState.KeyUnavailable) return null

        val stored =
            vaultDao.loadAllVaults().filter { it.collidesWith(backup) }.singleOrNull()
                ?: return null

        val storedShares = vaultDao.loadKeyShares(stored.id)
        if (
            storedShares.isEmpty() || storedShares.any { !keyShareCipher.isEncrypted(it.keyShare) }
        ) {
            return null
        }

        val preservesEveryIdentity =
            preserves(stored.pubKeyEcdsa, backup.pubKeyECDSA) &&
                preserves(stored.pubKeyEddsa, backup.pubKeyEDDSA) &&
                preserves(stored.pubKeyMldsa, backup.pubKeyMLDSA)

        return stored.takeIf { preservesEveryIdentity && backup.carriesSharesFor(storedShares) }
    }
}

/** Whether [backup] names any of the same signing identities, which no other vault can. */
private fun VaultEntity.collidesWith(backup: Vault): Boolean =
    pubKeyEcdsa.sameAs(backup.pubKeyECDSA) ||
        pubKeyEddsa.sameAs(backup.pubKeyEDDSA) ||
        pubKeyMldsa.sameAs(backup.pubKeyMLDSA)

/** An identity the stored vault does not hold is nothing to preserve. */
private fun preserves(stored: String, backup: String): Boolean =
    stored.isBlank() || stored == backup

/** Whether this backup carries a usable keyshare for every key it and [stored] declare. */
private fun Vault.carriesSharesFor(stored: List<KeyShareEntity>): Boolean {
    if (keyshares.isEmpty() || keyshares.any { it.keyShare.isBlank() }) return false
    val carried = keyshares.mapTo(mutableSetOf()) { it.pubKey }
    val declared = listOf(pubKeyECDSA, pubKeyEDDSA, pubKeyMLDSA).filter { it.isNotBlank() }
    return carried.containsAll(declared) && carried.containsAll(stored.map { it.pubKey })
}

/** Public keys identify a vault only when both sides actually carry one. */
private fun String.sameAs(other: String): Boolean = isNotBlank() && this == other
