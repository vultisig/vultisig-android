package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.isServerVault
import com.vultisig.wallet.data.repositories.VultiSignerRepository
import javax.inject.Inject


interface IsVaultHasFastSignUseCase : suspend (Vault) -> Boolean

internal class IsVaultHasFastSignUseCaseImpl @Inject constructor(
    private val vultiSignerRepository: VultiSignerRepository
): IsVaultHasFastSignUseCase {
    override suspend fun invoke(vault: Vault): Boolean {
        return !vault.isServerVault() && vultiSignerRepository.hasFastSign(vault.pubKeyECDSA)
    }
}
