package com.vultisig.wallet.data.usecases.fast

import com.vultisig.wallet.data.repositories.VultiSignerRepository
import javax.inject.Inject

fun interface VerifyFastVaultBackupCodeUseCase {
    suspend operator fun invoke(
        publicKeyEcdsa: String,
        code: String,
    ): Boolean
}

internal class VerifyFastVaultBackupCodeUseCaseImpl @Inject constructor(
    private val vultiSignerRepository: VultiSignerRepository,
) : VerifyFastVaultBackupCodeUseCase {
    override suspend fun invoke(
        publicKeyEcdsa: String,
        code: String,
    ): Boolean = vultiSignerRepository.isBackupCodeValid(
        publicKeyEcdsa = publicKeyEcdsa,
        code = code,
    )
}