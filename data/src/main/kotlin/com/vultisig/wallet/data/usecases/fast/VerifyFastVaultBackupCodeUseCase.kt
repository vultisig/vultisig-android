package com.vultisig.wallet.data.usecases.fast

import com.vultisig.wallet.data.repositories.BackupCodeVerifyResult
import com.vultisig.wallet.data.repositories.VultiSignerRepository
import javax.inject.Inject

/**
 * Verifies the 4-digit Fast Vault PIN against the VultiSigner server. Returns a
 * [BackupCodeVerifyResult] so callers can distinguish a genuine wrong PIN from a transport or
 * server failure.
 */
fun interface VerifyFastVaultBackupCodeUseCase {
    suspend operator fun invoke(publicKeyEcdsa: String, code: String): BackupCodeVerifyResult
}

internal class VerifyFastVaultBackupCodeUseCaseImpl
@Inject
constructor(private val vultiSignerRepository: VultiSignerRepository) :
    VerifyFastVaultBackupCodeUseCase {
    override suspend fun invoke(publicKeyEcdsa: String, code: String): BackupCodeVerifyResult =
        vultiSignerRepository.isBackupCodeValid(publicKeyEcdsa = publicKeyEcdsa, code = code)
}
