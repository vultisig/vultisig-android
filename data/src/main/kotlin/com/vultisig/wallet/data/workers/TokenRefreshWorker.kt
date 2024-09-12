package com.vultisig.wallet.data.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.gson.Gson
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.data.repositories.TokenRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.delay
import timber.log.Timber

@HiltWorker
class TokenRefreshWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val gson: Gson,
    private val tokenRepository: TokenRepository,
    private val vaultRepository: VaultRepository,
    private val chainAccountAddressRepository: ChainAccountAddressRepository
) : CoroutineWorker(appContext, workerParams) {

        override suspend fun doWork(): Result {

                val defaultVaultId = inputData.getString(VAULT_ID)
                val defaultNativeTokenJson = inputData.getString(NATIVE_TOKEN)

                val vaults = if (defaultVaultId == null) {
                    vaultRepository.getAll()
                } else {
                    listOf(vaultRepository.get(defaultVaultId))
                }.filterNotNull()

                for (vault in vaults) {
                    val nativeTokens = if (defaultNativeTokenJson == null) {
                        vault.coins.filter { it.isNativeToken }
                    } else {
                        listOf(gson.fromJson(defaultNativeTokenJson, Coin::class.java))
                    }
                    for (nativeToken in nativeTokens) {
                        val (address, derivedPublicKey) = chainAccountAddressRepository.getAddress(
                            nativeToken,
                            vault
                        )
                        try {
                        tokenRepository
                            .getTokensWithBalance(nativeToken.chain, address)
                            .filter { token -> token.id != nativeToken.id }
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
                        delay(1000) //TODO remove when we will use api without rate limit
                    }
                }
            return Result.success()
        }

    companion object {
        val VAULT_ID = "vaultId"
        val NATIVE_TOKEN = "native_token"
    }
}