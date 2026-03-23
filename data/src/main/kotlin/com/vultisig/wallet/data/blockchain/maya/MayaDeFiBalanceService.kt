package com.vultisig.wallet.data.blockchain.maya

import com.vultisig.wallet.data.blockchain.DeFiService
import com.vultisig.wallet.data.blockchain.model.DeFiBalance
import com.vultisig.wallet.data.blockchain.model.StakingDetails
import com.vultisig.wallet.data.blockchain.model.StakingDetails.Companion.generateId
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.repositories.ActiveBondedNodeRepository
import com.vultisig.wallet.data.repositories.StakingDetailsRepository
import com.vultisig.wallet.data.usecases.MayachainBondUseCase
import java.math.BigInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.supervisorScope
import timber.log.Timber

class MayaDeFiBalanceService(
    private val mayachainBondUseCase: MayachainBondUseCase,
    private val mayaCacaoStakingService: MayaCacaoStakingService,
    private val activeBondedNodeRepository: ActiveBondedNodeRepository,
    private val stakingDetailsRepository: StakingDetailsRepository,
) : DeFiService {

    override suspend fun getRemoteDeFiBalance(address: String, vaultId: String): List<DeFiBalance> =
        supervisorScope {
            val bondedDeferred = async {
                try {
                    val nodes = mayachainBondUseCase.getActiveNodes(vaultId, address).last()
                    val amount = nodes.sumOf { it.amount }
                    Timber.d(
                        "MayaDeFiBalanceService: bonded amount=$amount from ${nodes.size} nodes"
                    )
                    amount
                } catch (e: Exception) {
                    Timber.e(e, "MayaDeFiBalanceService: Failed to fetch bonded nodes")
                    activeBondedNodeRepository.getBondedNodes(vaultId).sumOf { it.amount }
                }
            }

            val stakingDeferred = async {
                try {
                    val details = mayaCacaoStakingService.getStakingDetails(address).last()
                    val amount = details.stakeAmount
                    Timber.d("MayaDeFiBalanceService: staking amount=$amount")
                    persistCacaoStakingDetails(vaultId, amount)
                    amount
                } catch (e: Exception) {
                    Timber.e(e, "MayaDeFiBalanceService: Failed to fetch CACAO staking details")
                    stakingDetailsRepository
                        .getStakingDetailsByCoindId(vaultId, Coins.MayaChain.CACAO.id)
                        ?.stakeAmount ?: BigInteger.ZERO
                }
            }

            val totalCacao = bondedDeferred.await() + stakingDeferred.await()
            listOf(
                DeFiBalance(
                    chain = Chain.MayaChain,
                    balances =
                        listOf(
                            DeFiBalance.Balance(coin = Coins.MayaChain.CACAO, amount = totalCacao)
                        ),
                )
            )
        }

    override suspend fun getCacheDeFiBalance(address: String, vaultId: String): List<DeFiBalance> {
        val bondedNodes = activeBondedNodeRepository.getBondedNodes(vaultId)
        val cachedStaking =
            stakingDetailsRepository.getStakingDetailsByCoindId(vaultId, Coins.MayaChain.CACAO.id)

        val totalBonded = bondedNodes.sumOf { it.amount }
        val stakingAmount = cachedStaking?.stakeAmount ?: BigInteger.ZERO
        val totalCacao = totalBonded + stakingAmount

        if (totalCacao == BigInteger.ZERO) return emptyList()

        return listOf(
            DeFiBalance(
                chain = Chain.MayaChain,
                balances =
                    listOf(DeFiBalance.Balance(coin = Coins.MayaChain.CACAO, amount = totalCacao)),
            )
        )
    }

    private suspend fun persistCacaoStakingDetails(vaultId: String, stakeAmount: BigInteger) {
        try {
            val details =
                StakingDetails(
                    id = Coins.MayaChain.CACAO.generateId(),
                    coin = Coins.MayaChain.CACAO,
                    stakeAmount = stakeAmount,
                    apr = null,
                    estimatedRewards = null,
                    nextPayoutDate = null,
                    rewards = null,
                    rewardsCoin = null,
                )
            val existing =
                stakingDetailsRepository.getStakingDetailsByCoindId(
                    vaultId,
                    Coins.MayaChain.CACAO.id,
                )
            if (existing == null) {
                stakingDetailsRepository.saveStakingDetails(vaultId, details)
            } else {
                stakingDetailsRepository.updateStakingDetails(vaultId, details)
            }
        } catch (e: Exception) {
            Timber.e(e, "MayaDeFiBalanceService: Failed to persist CACAO staking details")
        }
    }
}
