package com.vultisig.wallet.data.blockchain.maya

import com.vultisig.wallet.data.blockchain.DeFiService
import com.vultisig.wallet.data.blockchain.model.DeFiBalance
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.repositories.ActiveBondedNodeRepository
import com.vultisig.wallet.data.usecases.MayachainBondUseCase
import kotlinx.coroutines.flow.last
import timber.log.Timber

class MayaDeFiBalanceService(
    private val mayachainBondUseCase: MayachainBondUseCase,
    private val mayaCacaoStakingService: MayaCacaoStakingService,
    private val activeBondedNodeRepository: ActiveBondedNodeRepository,
) : DeFiService {

    override suspend fun getRemoteDeFiBalance(address: String, vaultId: String): List<DeFiBalance> {
        val results = mutableListOf<DeFiBalance>()

        try {
            val bondedNodes = mayachainBondUseCase.getActiveNodesRemote(address)
            val totalBonded = bondedNodes.sumOf { it.amount }
            results.add(
                DeFiBalance(
                    chain = Chain.MayaChain,
                    balances =
                        listOf(
                            DeFiBalance.Balance(coin = Coins.MayaChain.CACAO, amount = totalBonded)
                        ),
                )
            )
            Timber.d(
                "MayaDeFiBalanceService: bonded amount=$totalBonded from ${bondedNodes.size} nodes"
            )
        } catch (e: Exception) {
            Timber.e(e, "MayaDeFiBalanceService: Failed to fetch bonded nodes")
        }

        try {
            val stakingDetails = mayaCacaoStakingService.getStakingDetails(address).last()
            results.add(
                DeFiBalance(
                    chain = Chain.MayaChain,
                    balances =
                        listOf(
                            DeFiBalance.Balance(
                                coin = Coins.MayaChain.CACAO,
                                amount = stakingDetails.stakeAmount,
                            )
                        ),
                )
            )
            Timber.d("MayaDeFiBalanceService: staking amount=${stakingDetails.stakeAmount}")
        } catch (e: Exception) {
            Timber.e(e, "MayaDeFiBalanceService: Failed to fetch CACAO staking details")
        }

        return results
    }

    override suspend fun getCacheDeFiBalance(address: String, vaultId: String): List<DeFiBalance> {
        val bondedNodes = activeBondedNodeRepository.getBondedNodes(vaultId)
        if (bondedNodes.isEmpty()) return emptyList()

        val totalBonded = bondedNodes.sumOf { it.amount }
        return listOf(
            DeFiBalance(
                chain = Chain.MayaChain,
                balances =
                    listOf(DeFiBalance.Balance(coin = Coins.MayaChain.CACAO, amount = totalBonded)),
            )
        )
    }
}
