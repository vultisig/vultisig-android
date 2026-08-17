package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.data.repositories.VaultDataStoreRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import timber.log.Timber

/**
 * Stores a vault parsed out of a backup file, and does the two things that only an import owes it.
 *
 * Shared by the import screen and by the recovery the passcode gate offers when a device can no
 * longer open its own keyshares — the second cannot route to the first, because the import screen
 * draws in the window the gate covers.
 */
internal interface StoreImportedVaultUseCase {
    /**
     * @param fileName the name the backup was read from, or null when it is not known.
     * @return the vault as stored, which is what later steps must refer to.
     * @throws DuplicateVaultException when the device already holds this vault and can open it.
     */
    suspend operator fun invoke(vault: Vault, fileName: String?): Vault
}

internal class StoreImportedVaultUseCaseImpl
@Inject
constructor(
    private val saveVault: SaveVaultUseCase,
    private val vaultRepository: VaultRepository,
    private val vaultDataStoreRepository: VaultDataStoreRepository,
    private val chainAccountAddressRepository: ChainAccountAddressRepository,
) : StoreImportedVaultUseCase {

    // saveVault is the point of no return. Every step after it runs best-effort so a datastore or
    // derivation glitch can't surface as an import failure over a vault that is already stored.
    override suspend fun invoke(vault: Vault, fileName: String?): Vault {
        val adjusted = vault.withInferredLibType(fileName)

        saveVault(adjusted, false)
        runBestEffort("Failed to set backup status") {
            vaultDataStoreRepository.setBackupStatus(adjusted.id, true)
        }
        if (adjusted.pubKeyMLDSA.isNotBlank()) attachQbtcToken(adjusted)

        return adjusted
    }

    private suspend fun attachQbtcToken(vault: Vault) =
        runBestEffort("Failed to add QBTC token") {
            val qbtc = Coins.Qbtc.QBTC
            val (address, pubKey) = chainAccountAddressRepository.getAddress(qbtc, vault)
            vaultRepository.addTokenToVault(
                vault.id,
                qbtc.copy(address = address, hexPublicKey = pubKey),
            )
        }

    private suspend inline fun runBestEffort(message: String, block: () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, message)
        }
    }

    // Older DKLS backups were sometimes persisted with libType=GG20. Recover the real type from the
    // share-NofM filename convention, but leave KeyImport vaults alone — they also use that naming
    // and must keep their declared libType.
    private fun Vault.withInferredLibType(fileName: String?): Vault =
        if (libType == SigningLibType.GG20 && fileName?.contains(SHARE_FILENAME_REGEX) == true) {
            copy(libType = SigningLibType.DKLS)
        } else {
            this
        }

    private companion object {
        private val SHARE_FILENAME_REGEX = "share\\d+of\\d+".toRegex()
    }
}
