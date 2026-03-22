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
import kotlinx.coroutines.flow.last
import timber.log.Timber

class MayaDeFiBalanceService(
    private val mayachainBondUseCase: MayachainBondUseCase,
    private val mayaCacaoStakingService: MayaCacaoStakingService,
    private val activeBondedNodeRepository: ActiveBondedNodeRepository,
    private val stakingDetailsRepository: StakingDetailsRepository,
) : DeFiService {

    override suspend fun getRemoteDeFiBalance(address: String, vaultId: String): List<DeFiBalance> {
        var totalBonded = BigInteger.ZERO
        var stakingAmount = BigInteger.ZERO

        try {
            val bondedNodes = mayachainBondUseCase.getActiveNodesRemote(address)
            totalBonded = bondedNodes.sumOf { it.amount }
            Timber.d(
                "MayaDeFiBalanceService: bonded amount=$totalBonded from ${bondedNodes.size} nodes"
            )
        } catch (e: Exception) {
            Timber.e(e, "MayaDeFiBalanceService: Failed to fetch bonded nodes")
        }

        try {
            val stakingDetails = mayaCacaoStakingService.getStakingDetails(address).last()
            stakingAmount = stakingDetails.stakeAmount
            Timber.d("MayaDeFiBalanceService: staking amount=$stakingAmount")
            persistCacaoStakingDetails(vaultId, stakingAmount)
        } catch (e: Exception) {
            Timber.e(e, "MayaDeFiBalanceService: Failed to fetch CACAO staking details")
        }

        val totalCacao = totalBonded + stakingAmount
        return listOf(
            DeFiBalance(
                chain = Chain.MayaChain,
                balances =
                    listOf(DeFiBalance.Balance(coin = Coins.MayaChain.CACAO, amount = totalCacao)),
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
