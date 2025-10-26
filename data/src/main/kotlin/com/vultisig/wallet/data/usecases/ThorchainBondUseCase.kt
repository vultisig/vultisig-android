package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.api.ThorChainApi
import timber.log.Timber
import javax.inject.Inject


fun interface ThorchainBondUseCase {
    suspend operator fun invoke()
}

class ThorchainBondUseCaseImpl @Inject constructor(
    private val thorChainApi: ThorChainApi,
): ThorchainBondUseCase {
    override suspend fun invoke() {
        try {
            val networkInfo = thorChainApi.getNetworkBondInfo()
            val bondedNodes = thorChainApi.getBondedNodes(runeCoinAddress)

            val activeNodes = mutableListOf<ActiveBondedNode>()
            val bondedNodeAddresses = mutableSetOf<String>()

            for (node in bondedNodes.nodes) {
                bondedNodeAddresses.add(node.address)

                try {
                    val myBondMetrics = thorChainApi.calculateBondMetrics(
                        nodeAddress = node.address,
                        myBondAddress = runeCoinAddress
                    )

                    val nodeState = BondNodeState.fromApiStatus(myBondMetrics.nodeStatus)
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

                    activeNodes.add(activeNode)
                } catch (e: Exception) {
                   Timber.e(e)
                }
            }

            val availableNodesList = vultiNodeAddresses
                .filterNot { bondedNodeAddresses.contains(it) }
                .map { address ->
                    BondNode(address = address, state = BondNodeState.Active)
                }
        } catch (e: Exception) {
                println("Failed to load bonded nodes: ${e.message}")
            }
        }
    }
}