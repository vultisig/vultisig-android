package com.vultisig.wallet.data.blockchain.maya

import com.vultisig.wallet.data.api.MayaChainApi
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.usecases.GetMayaCacaoMaturityStatusUseCase
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
    // null when there is no stake position, the position is already mature, or the
    // maturity fetch failed. UI uses this only to render a "Unlocks in N days, H hours"
    // hint next to the disabled Unstake action.
    val unstakeUnlocksInSeconds: Long? = null,
    // true when the position exists but the maturity read returned UNKNOWN (transient RPC
    // failure). Lets the staking tab surface the same "Couldn't verify position" hint the
    // UnstakeCacao screen already renders, so a disabled Unstake button isn't unexplained.
    val isUnstakeMaturityUnknown: Boolean = false,
)

class MayaCacaoStakingService
@Inject
constructor(
    private val mayaChainApi: MayaChainApi,
    private val getMayaCacaoMaturityStatus: GetMayaCacaoMaturityStatusUseCase,
) {
    fun getStakingDetails(address: String): Flow<MayaCacaoStakingDetails> =
        flow {
                try {
                    val details = supervisorScope {
                        val providerDeferred = async { mayaChainApi.getCacaoProvider(address) }
                        val networkDeferred = async { mayaChainApi.getMidgardNetworkData() }
                        val maturityDeferred = async { getMayaCacaoMaturityStatus(address) }

                        val provider = providerDeferred.await()
                        val network = networkDeferred.await()
                        val maturity = maturityDeferred.await()

                        val stakeAmount = provider.value.toBigIntegerOrNull() ?: BigInteger.ZERO
                        val apr = network.liquidityAPY.toDoubleOrNull()?.takeIf { it > 0.0 }
                        val hasPosition = stakeAmount > BigInteger.ZERO

                        MayaCacaoStakingDetails(
                            stakeAmount = stakeAmount,
                            apr = apr,
                            canUnstake = hasPosition && maturity.isMature,
                            unstakeUnlocksInSeconds =
                                if (
                                    hasPosition &&
                                        !maturity.isMature &&
                                        !maturity.isUnknown &&
                                        maturity.remainingSeconds > 0L
                                )
                                    maturity.remainingSeconds
                                else null,
                            isUnstakeMaturityUnknown = hasPosition && maturity.isUnknown,
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
