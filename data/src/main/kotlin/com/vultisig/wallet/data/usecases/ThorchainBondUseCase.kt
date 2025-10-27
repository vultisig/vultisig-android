package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.api.ChurnEntry
import com.vultisig.wallet.data.api.MidgardNetworkData
import com.vultisig.wallet.data.repositories.ThorchainBondRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import timber.log.Timber
import java.math.BigInteger
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
            val networkInfoDeferred = async { getNetworkInfo() }.await()
            val bondedNodes = thorchainBondRepository.getBondedNodes(address).nodes

            val activeNodes = mutableListOf<ActiveBondedNode>()
            val bondedNodeAddresses = mutableSetOf<String>()

            for (node in bondedNodes) {
                bondedNodeAddresses.add(node.address)

                try {
                    val myBondMetrics = calculateBondMetrics(
                        nodeAddress = node.address,
                        myBondAddress = address
                    )

                    println(myBondMetrics)

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
                    // If there is a failure withh specific one,
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

    private suspend fun calculateBondMetrics(
        nodeAddress: String,
        myBondAddress: String,
    ) {
        val nodeData = thorchainBondRepository.getNodeDetails(nodeAddress)
        val bondProviders = nodeData.bondProviders.providers

        // 2. Calculate my bond and total bond
        var myBond = BigInteger.ZERO
        var totalBond = BigInteger.ZERO

        for (provider in bondProviders) {
            val providerBond = provider.bond.toBigIntegerOrNull() ?: BigInteger.ZERO
            if (provider.bondAddress == myBondAddress) {
                myBond = providerBond
            }
            totalBond += providerBond
        }

        // Transform to decimal and get proper APR
        val myBondOwnershipPercentage = if (totalBond > BigInteger.ZERO) {
            myBond.divide(totalBond)
        } else {
            BigInteger.ZERO
        }

        println(myBondOwnershipPercentage)
        // 4.Calculate node operator fee (convert from basis points)
        /*val nodeOperatorFee =
            (nodeData.bondProviders.nodeOperatorFee.toBigDecimalOrNull() ?: BigDecimal.ZERO)
                .divide(BigDecimal(10_000), 18, RoundingMode.HALF_UP) */

    }

    /*
    let nodeData = try await getNodeDetails(nodeAddress: nodeAddress)
        let bondProviders = nodeData.bondProviders.providers

        // 2. Calculate my bond and total bond
        var myBond: Decimal = 0
        var totalBond: Decimal = 0

        for provider in bondProviders {
            let providerBond = Decimal(string: provider.bond) ?? 0
            if provider.bondAddress == myBondAddress {
                myBond = providerBond
            }
            totalBond += providerBond
        }

        // 3. Calculate ownership percentage
        let myBondOwnershipPercentage = totalBond > 0 ? myBond / totalBond : 0

        // 4. Calculate node operator fee (convert from basis points)
        let nodeOperatorFee = (Decimal(string: nodeData.bondProviders.nodeOperatorFee) ?? 0) / 10000

        // 5. Calculate current award after node operator fee
        let currentAward = (Decimal(string: nodeData.currentAward) ?? 0) * (1 - nodeOperatorFee)
        let myAward = myBondOwnershipPercentage * currentAward

        // 6. Get recent churn timestamp to calculate APY
        let churns = try await getChurns()
        guard let mostRecentChurn = churns.first,
              let recentChurnTimestampNanos = Double(mostRecentChurn.date) else {
            throw THORChainAPIError.invalidResponse
        }

        // Convert from nanoseconds to seconds
        let recentChurnTimestamp = recentChurnTimestampNanos / 1_000_000_000

        // 7. Calculate time since last churn
        let currentTime = Date().timeIntervalSince1970
        let timeDiff = currentTime - recentChurnTimestamp
        let timeDiffInYears = timeDiff / (60 * 60 * 24 * 365.25)

        // 8. Calculate APR and APY per node (matching JavaScript implementation)
        let apr = myBond > 0 && timeDiffInYears > 0 ? (myAward / myBond) / Decimal(timeDiffInYears) : 0

        // APY = (1 + APR/365)^365 - 1
        let aprDouble = Double(truncating: apr as NSNumber)
        let apy = pow(1 + aprDouble / 365, 365) - 1

        return BondMetrics(
            myBond: myBond,
            myAward: myAward,
            apy: apy,
            nodeStatus: nodeData.status
        )
     */
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