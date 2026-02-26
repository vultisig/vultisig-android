package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.isFastVault
import com.vultisig.wallet.data.models.isServerVault
import com.vultisig.wallet.data.repositories.VultiSignerRepository
import javax.inject.Inject

/**
 * Determines whether the given [Vault] supports fast signing
 */
interface IsVaultHasFastSignUseCase : suspend (Vault) -> Boolean

/**
 * Fast signing is available for non-server vaults.
 *
 * If the vault already supports fast signing structurally, we return true.
 * Otherwise, we check with the backend to see if a fast-sign record exists.
 */
internal class IsVaultHasFastSignUseCaseImpl @Inject constructor(
    private val repository: VultiSignerRepository,
) : IsVaultHasFastSignUseCase {

    override suspend fun invoke(vault: Vault): Boolean =
        when {
            vault.isServerVault() -> false
            vault.isFastVault() -> true
            else -> repository.hasFastSign(vault.pubKeyECDSA)
        }
}
