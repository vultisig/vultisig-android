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
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Stores a backup in place of the stored vault it restores, when that vault is one this device can
 * no longer open.
 *
 * Without this the backup is refused as a duplicate and the vault is unrecoverable in the app: the
 * orphaned row still resolves to a `Vault`, so the import's collision check finds it, and its
 * sealed keyshares keep every launch from then on in [PasscodeState.KeyUnavailable].
 */
interface SupersedeUnopenableVaultUseCase {
    /**
     * Returns true when [backup] was stored in place of a vault, and false when nothing changed.
     */
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
            // Two pointers to the row that just went, neither of them a foreign key. The ordering
            // row would outlive it — the delete flow removes it by hand for the same reason, and
            // the replacement is given its place in the list the way any new vault is. The
            // last-opened id is what the home screen resolves the vault it shows from, and it is
            // read once: left dangling, the screen behind the gate stays bound to a vault that no
            // longer exists, and sends and swaps route with its id.
            vaultOrderRepository.delete(parentId = null, name = superseded.id)
            lastOpenedVaultRepository.setLastOpenedVaultId(backup.id)
            Timber.i("Restored a vault whose keyshares this device could no longer open")
            true
        }

    /**
     * The one stored vault [backup] is allowed to replace, or null when there is none.
     *
     * This re-enables, in one bounded path, the replacement the import gate exists to refuse, so
     * every clause below is the difference between a recovery and a loss:
     * - **The credentials are confirmed gone, as of now.** The state is read back through
     *   [PasscodeRepository.retry] first, so a keystore that has come back since launch resolves to
     *   [PasscodeState.Locked] and refuses here rather than after the delete.
     *   [PasscodeState.StoreUnavailable] never qualifies: there the wrap is most likely still on
     *   disk and the shares open again once the keystore returns, so a replacement would destroy a
     *   vault that was only ever unreadable for a launch. Together these stand in for the "was the
     *   wrapped key absent" question iOS asks its keychain directly, which Android's store cannot
     *   answer — a value it fails to decrypt reads exactly like one that was never written.
     * - **Exactly one stored vault collides.** A backup matching two of them is a question this
     *   cannot answer, and answering it by picking one destroys the other's key material.
     * - **Every share of that vault is sealed, and it has at least one.** A vault with no shares
     *   has nothing to be orphaned about, and a row still holding one in the clear is one this
     *   device wrote after the credentials went — a vault it is keeping, not losing.
     * - **The backup *is* that vault, not merely overlapping with it.** Matching one public key is
     *   what makes something a duplicate; it takes every signing identity the stored row holds to
     *   make it a replacement. An identity the backup adds is fine, one it leaves out is not.
     * - **The backup carries a share for every key — its own and the stored row's.** An empty or
     *   blank-filled `keyshares` list round-trips perfectly well and would buy the deletion with
     *   nothing.
     *
     * Deliberately not done: nothing here parses a share to confirm its bytes really derive the
     * public key they are filed under. That is work behind the TSS boundary, and the ordinary
     * import path extends a backup the same trust — these clauses keep this path from extending it
     * any further.
     */
    private suspend fun supersededBy(backup: Vault): VaultEntity? {
        // Re-reads the keystore before anything is deleted rather than trusting the answer a launch
        // hours ago got. A no-op unless the state is already one of the unreadable two.
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

/**
 * Whether a signing identity the stored vault holds survives the replacement unchanged. One it does
 * not have is nothing to preserve; one it has and the backup omits or spells differently is key
 * material the replacement would take away with the row.
 */
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
