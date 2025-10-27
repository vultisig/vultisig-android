package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.repositories.ThorchainBondRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import timber.log.Timber
import javax.inject.Inject


fun interface ThorchainBondUseCase {
    suspend operator fun invoke(address: String)
}

class ThorchainBondUseCaseImpl @Inject constructor(
    private val thorchainBondRepository: ThorchainBondRepository,
) : ThorchainBondUseCase {
    override suspend fun invoke(address: String) = supervisorScope {
        try {
            val networkInfoDeferred = async { thorchainBondRepository.getMidgardNetworkData() }
            val bondedNodes = thorchainBondRepository.getBondedNodes(address)?.nodes
                ?: error("Can't fetch bonded nodes RPC Error")

            val activeNodes = mutableListOf<ActiveBondedNode>()
            val bondedNodeAddresses = mutableSetOf<String>()

            for (node in bondedNodes) {
                bondedNodeAddresses.add(node.address)

                try {
                    val myBondMetrics = thorChainApi.calculateBondMetrics(
                        nodeAddress = node.address,
                        myBondAddress = address
                    )

                    /*val nodeState = BondNodeState.fromApiStatus(myBondMetrics.nodeStatus)
                        ?: BondNodeState.Standby

                    val bondNode = BondNode(
                        address = node.address,
                        state = nodeState
                    )

                    val activeNode = ActiveBondedNode(
                        node = bondNode,
                        amount = myBondMetrics.myBond,
                        apy = myBondMetrics.apy,
                        nextReward = myBondMetrics.myAward,
                        nextChurn = networkInfo.nextChurnDate
                    )

                    activeNodes.add(activeNode) */
                } catch (e: Exception) {
                    Timber.e(e)
                }
            }
        } catch (e: Exception) {
            println("Failed to load bonded nodes: ${e.message}")
        }
    }
}

internal data class ActiveBondedNode(
    var id: String,
    val node: String,
    val amount: String,
    val apy: Double,
    val nextReward: String,
    val nextChurn: String,
) {
    data class BondedNode(
        val address: String,
        val state: String,
    )
}