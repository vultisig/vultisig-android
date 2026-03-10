package com.vultisig.wallet.data.blockchain.maya

import com.vultisig.wallet.data.api.MayaChainApi
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.usecases.ValidateMayaTransactionHeightUseCase
import java.math.BigInteger
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.supervisorScope
import timber.log.Timber

data class MayaCacaoStakingDetails(
    val stakeAmount: BigInteger,
    val apr: Double?,
    val canUnstake: Boolean,
)

class MayaCacaoStakingService
@Inject
constructor(
    private val mayaChainApi: MayaChainApi,
    private val validateMayaTransactionHeightUseCase: ValidateMayaTransactionHeightUseCase,
) {
    fun getStakingDetails(address: String): Flow<MayaCacaoStakingDetails> =
        flow {
                try {
                    val details = supervisorScope {
                        val providerDeferred = async { mayaChainApi.getCacaoProvider(address) }
                        val networkDeferred = async { mayaChainApi.getMidgardNetworkData() }
                        val canUnstake = validateMayaTransactionHeightUseCase(address)

                        val provider = providerDeferred.await()
                        val network = networkDeferred.await()

                        val stakeAmount = provider.value.toBigIntegerOrNull() ?: BigInteger.ZERO
                        val apr = network.liquidityAPY.toDoubleOrNull()?.takeIf { it > 0.0 }

                        MayaCacaoStakingDetails(
                            stakeAmount = stakeAmount,
                            apr = apr,
                            canUnstake = canUnstake && stakeAmount > BigInteger.ZERO,
                        )
                    }
                    emit(details)
                } catch (e: Exception) {
                    Timber.e(
                        e,
                        "MayaCacaoStakingService: Error fetching CACAO staking for $address",
                    )
                    emit(
                        MayaCacaoStakingDetails(
                            stakeAmount = BigInteger.ZERO,
                            apr = null,
                            canUnstake = false,
                        )
                    )
                }
            }
            .flowOn(Dispatchers.IO)

    companion object {
        val COIN = Coins.MayaChain.CACAO
    }
}
