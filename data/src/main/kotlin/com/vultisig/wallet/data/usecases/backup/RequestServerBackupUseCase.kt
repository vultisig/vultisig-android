package com.vultisig.wallet.data.usecases.backup

import com.vultisig.wallet.data.models.VaultId
import com.vultisig.wallet.data.repositories.ServerBackupResult
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.repositories.VultiSignerRepository
import javax.inject.Inject

/**
 * Requests the VultiSigner server to resend the encrypted vault backup share to the specified email
 * address. Resolves the vault's ECDSA public key from [VaultRepository] and delegates to
 * [VultiSignerRepository].
 */
fun interface RequestServerBackupUseCase {
    suspend operator fun invoke(
        vaultId: VaultId,
        email: String,
        password: String,
    ): ServerBackupResult
}

internal class RequestServerBackupUseCaseImpl
@Inject
constructor(
    private val vaultRepository: VaultRepository,
    private val vultiSignerRepository: VultiSignerRepository,
) : RequestServerBackupUseCase {

    override suspend fun invoke(
        vaultId: VaultId,
        email: String,
        password: String,
    ): ServerBackupResult {
        val vault =
            vaultRepository.get(vaultId)
                ?: return ServerBackupResult.Error(ServerBackupResult.ErrorType.UNKNOWN)

        return vultiSignerRepository.requestServerBackup(
            publicKeyEcdsa = vault.pubKeyECDSA,
            email = email,
            password = password,
        )
    }
}
