package com.vultisig.wallet.data.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.vultisig.wallet.data.models.Tokens
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
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
    private val chainAccountAddressRepository: ChainAccountAddressRepository
) : CoroutineWorker(appContext, workerParams) {

        override suspend fun doWork(): Result {

                val inputVaultId = inputData.getString(ARG_VAULT_ID)
                val inputChainId = inputData.getString(ARG_CHAIN)

                val vaults = if (inputVaultId == null) {
                    vaultRepository.getAll()
                } else {
                    listOf(vaultRepository.get(inputVaultId)?: return Result.failure())
                }

                for (vault in vaults) {
                    val allVaultChains = vault.coins.map { it.chain }.toSet()
                    val chains = if (inputChainId == null) {
                        allVaultChains
                    } else {
                        allVaultChains.filter { it.id == inputChainId }
                    }
                    for (chain in chains) {
                        val (address, derivedPublicKey) = chainAccountAddressRepository.getAddress(
                            chain,
                            vault
                        )
                        try {
                            (tokenRepository
                                .getTokensWithBalance(chain, address) +
                                    enabledByDefaultTokens.getOrDefault(chain, emptyList()))
                                .filter { !it.isNativeToken }
                                .forEach { token ->
                                    val updatedToken = token.copy(
                                        address = address,
                                        hexPublicKey = derivedPublicKey
                                    )
                                    vaultRepository.addTokenToVault(vault.id, updatedToken)
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

    private val enabledByDefaultTokens = listOf(Tokens.tcy)
        .groupBy { it.chain }

    companion object {
        const val ARG_VAULT_ID = "vault_id"
        const val ARG_CHAIN = "chain_id"
    }
}