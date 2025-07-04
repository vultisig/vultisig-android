package com.vultisig.wallet.data.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.vultisig.wallet.data.repositories.TokenRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.delay
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

            for (chain in chains) {
                try {
                    tokenRepository.getRefreshTokens(chain, vault)
                        .filter {
                            disabledCoinIds.none { disabledId ->
                                disabledId == it.id
                            }
                        }
                        .forEach {
                            vaultRepository.addTokenToVault(vault.id, it)
                        }
                } catch (e: Exception) {
                    Timber.e(e)
                }

                if (chains.size > 1 && vaults.size > 1)
                    delay(1000) //TODO remove when we will use api without rate limit
            }
        }
        return Result.success()
    }


    companion object {
        const val ARG_VAULT_ID = "vault_id"
        const val ARG_CHAIN = "chain_id"
    }
}