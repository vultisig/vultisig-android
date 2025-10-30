package com.vultisig.wallet.data.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.repositories.TokenRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import timber.log.Timber

@HiltWorker
internal class TokenRefreshWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val tokenRepository: TokenRepository,
    private val vaultRepository: VaultRepository,
) : CoroutineWorker(appContext, workerParams) {


    override suspend fun doWork(): Result {

        val inputVaultId = inputData.getString(ARG_VAULT_ID)
        val inputChainId = inputData.getString(ARG_CHAIN)

        val vaults = if (inputVaultId == null) {
            vaultRepository.getAll()
        } else {
            listOf(vaultRepository.get(inputVaultId) ?: return Result.failure())
        }

        for (vault in vaults) {
            val allVaultChains = vault.coins.map { it.chain }.toSet()
            val chains = if (inputChainId == null) {
                allVaultChains
            } else {
                allVaultChains.filter { it.id == inputChainId }
            }

            val disabledCoinIds = vaultRepository.getDisabledCoinIds(vaultId = vault.id)
            val enabledCoinIds = try {
                vaultRepository
                    .getEnabledTokens(vaultId = vault.id)
                    .first()
            } catch (e: Exception) {
                Timber.e(e)
                return Result.failure()
            }

            for (chain in chains) {
                try {
                    tokenRepository.getRefreshTokens(chain, vault)
                        .filter {
                            disabledCoinIds.none { disabledId ->
                                disabledId == it.id
                            }
                        }
                        .forEach { refreshToken ->
                            addRefreshTokenToVault(enabledCoinIds, refreshToken, vault)
                        }
                } catch (e: Exception) {
                    Timber.e(e)
                }

                if (chains.size > 1 && vaults.size > 1)
                    delay(1000) //should be removed when we use api without rate limit
            }
        }
        return Result.success()
    }

    private suspend fun addRefreshTokenToVault(
        enabledCoinIds: List<Coin>,
        refreshToken: Coin,
        vault: Vault
    ) {
        val isTokenExist = enabledCoinIds.any { enabledCoinId ->
            enabledCoinId.id.equals(refreshToken.id, ignoreCase = true)
        }
        if (isTokenExist)
            return

        val withSameContractCoin = enabledCoinIds.firstOrNull { enabledCoin ->
            enabledCoin.contractAddress == refreshToken.contractAddress
        }

        withSameContractCoin?.let {
            vaultRepository.deleteTokenFromVault(vault.id, it)
        }

        vaultRepository.addTokenToVault(vault.id, refreshToken)
    }


    companion object {
        const val ARG_VAULT_ID = "vault_id"
        const val ARG_CHAIN = "chain_id"
    }
}