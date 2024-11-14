package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.repositories.VaultRepository
import javax.inject.Inject

interface IsVaultHasFastSignByIdUseCase : suspend (String) -> Boolean

internal class IsVaultHasFastSignByIdUseCaseImpl @Inject constructor(
    private val vaultRepository: VaultRepository,
    private val isVaultHasFastSign: IsVaultHasFastSignUseCase,
): IsVaultHasFastSignByIdUseCase {
    override suspend fun invoke(vaultId: String): Boolean {
        val vault = requireNotNull(vaultRepository.get(vaultId))
        return isVaultHasFastSign(vault)
    }
}