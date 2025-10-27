package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.api.ChurnEntry
import com.vultisig.wallet.data.api.MidgardNetworkData
import com.vultisig.wallet.data.repositories.ThorchainBondRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import timber.log.Timber
import java.util.Date
import javax.inject.Inject

fun interface ThorchainBondUseCase {
    suspend operator fun invoke(address: String)
}

class ThorchainBondUseCaseImpl @Inject constructor(
    private val thorchainBondRepository: ThorchainBondRepository,
) : ThorchainBondUseCase {
    override suspend fun invoke(address: String) = supervisorScope {
        try {
            val networkInfoDeferred = async { getNetworkInfo() }
            val bondedNodes = thorchainBondRepository.getBondedNodes(address).nodes

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

    private suspend fun getNetworkInfo(): NetworkBondInfo {
        val network = thorchainBondRepository.getMidgardNetworkData()
        val apy = runCatching { network.bondingAPY.toDouble() }.getOrDefault(0.0)
        val nextChurnDate = estimateNextChurnETA(network)

        return NetworkBondInfo(
            apy = apy,
            nextChurnDate = nextChurnDate
        )
    }

    suspend fun estimateNextChurnETA(network: MidgardNetworkData): Date? = supervisorScope {
        val churnsDeferred = async { thorchainBondRepository.getChurns() }
        val healthDeferred = async { thorchainBondRepository.getMidgardHealthData() }

        val nextChurnHeight = network.nextChurnHeight.toInt()
        val currentHeight = healthDeferred.await().lastThorNode.height
        val currentTimeStamp = healthDeferred.await().lastThorNode.timestamp.toDouble()

        if (nextChurnHeight <= currentHeight) null

        // Derive avg block time from churn history; fallback if unavailable
        val avgBlockTime = averageBlockTimeFromChurns(churnsDeferred.await(), pairs = 8) ?: 6.0 // seconds per block

        val remainingBlocks = nextChurnHeight - currentHeight
        val etaSeconds = remainingBlocks * avgBlockTime

        Date((currentTimeStamp * 1000).toLong() + (etaSeconds * 1000).toLong())
    }

    fun averageBlockTimeFromChurns(churns: List<ChurnEntry>, pairs: Int = 6): Double? {
        val sorted = churns.sortedByDescending { it.height.toIntOrNull() ?: 0 }
        if (sorted.size < 2) return null

        var totalSeconds = 0.0
        var totalBlocks = 0

        for (i in 0 until minOf(pairs, sorted.size - 1)) {
            val hNew = sorted[i].height.toIntOrNull() ?: continue
            val hOld = sorted[i + 1].height.toIntOrNull() ?: continue
            val tNewNs = sorted[i].date.toLongOrNull() ?: continue
            val tOldNs = sorted[i + 1].date.toLongOrNull() ?: continue

            val dBlocks = hNew - hOld
            if (dBlocks <= 0) continue

            val dSeconds = (tNewNs - tOldNs) / 1_000_000_000.0
            if (dSeconds <= 0) continue

            totalSeconds += dSeconds
            totalBlocks += dBlocks
        }

        if (totalBlocks <= 0) return null
        return totalSeconds / totalBlocks
    }
}

internal data class NetworkBondInfo(
    val apy: Double,
    val nextChurnDate: Date?,
)

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