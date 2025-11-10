package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.api.ChurnEntry
import com.vultisig.wallet.data.api.MidgardNetworkData
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.repositories.ActiveBondedNodeRepository
import com.vultisig.wallet.data.repositories.ThorchainBondRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.supervisorScope
import timber.log.Timber
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.util.Date
import javax.inject.Inject
import kotlin.math.pow

interface ThorchainBondUseCase {

    suspend fun getActiveNodes(vaultId: String, address: String): Flow<List<ActiveBondedNode>>

    suspend fun getActiveNodesRemote(address: String): List<ActiveBondedNode>
}

class ThorchainBondUseCaseImpl @Inject constructor(
    private val thorchainBondRepository: ThorchainBondRepository,
    private val activeBondedNodeRepository: ActiveBondedNodeRepository,
) : ThorchainBondUseCase {

    override suspend fun getActiveNodes(vaultId: String, address: String): Flow<List<ActiveBondedNode>> =
        flow {
            try {
                // First get cache nodes and emit
                val cachedNodes = activeBondedNodeRepository.getBondedNodes(vaultId)
                if (cachedNodes.isNotEmpty()) {
                    Timber.d("Emitting ${cachedNodes.size} cached bonded nodes for vault $vaultId")
                    emit(cachedNodes)
                }

                // Fetch remote and update cache if require
                val freshNodes = getActiveNodesRemote(address)

                Timber.d("Emitting ${freshNodes.size} fresh bonded nodes for vault $vaultId")

                if (freshNodes.isEmpty()) {
                    Timber.d("Clearing bonded nodes cache for vault $vaultId (remote is empty)")
                    activeBondedNodeRepository.deleteBondedNodes(vaultId)
                } else {
                    // Replace cache with new data
                    activeBondedNodeRepository.deleteBondedNodes(vaultId)
                    activeBondedNodeRepository.saveBondedNodes(vaultId, freshNodes)
                }

                emit(freshNodes)
            } catch (e: Exception) {
                Timber.e(e, "Error fetching bonded nodes for vault $vaultId")

                val cachedNodes = activeBondedNodeRepository.getBondedNodes(vaultId)
                if (cachedNodes.isNotEmpty()) {
                    emit(cachedNodes)
                } else {
                    throw e
                }
            }
        }

    override suspend fun getActiveNodesRemote(address: String): List<ActiveBondedNode> =
        supervisorScope {
            val activeNodes = mutableListOf<ActiveBondedNode>()
            val bondedNodeAddresses = mutableSetOf<String>()

            try {
                val networkInfoDeferred = async { getNetworkInfo() }
                val bondedNodes = thorchainBondRepository.getBondedNodes(address).nodes

                for (node in bondedNodes) {
                    bondedNodeAddresses.add(node.address)

                    try {
                        val myBondMetrics = calculateBondMetrics(
                            nodeAddress = node.address,
                            myBondAddress = address
                        )

                        val bondNode = ActiveBondedNode.BondedNode(
                            address = node.address,
                            state = node.status,
                        )

                        val activeNode = ActiveBondedNode(
                            coinId = Coins.ThorChain.RUNE.id,
                            node = bondNode,
                            amount = myBondMetrics.myBond,
                            apy = myBondMetrics.apy,
                            nextReward = myBondMetrics.myAward,
                            nextChurn = networkInfoDeferred.await().nextChurnDate,
                        )
                        activeNodes.add(activeNode)
                    } catch (e: Exception) {
                        Timber.e(e)
                    }
                }

            } catch (e: Exception) {
                Timber.e(e)
            }

            return@supervisorScope activeNodes.toList()
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

        if (nextChurnHeight <= currentHeight) {
            return@supervisorScope null
        }

        // Derive avg block time from churn history; fallback if unavailable
        val avgBlockTime = averageBlockTimeFromChurns(churnsDeferred.await(), pairs = 8)
            ?: 6.0 // seconds per block

        val remainingBlocks = nextChurnHeight - currentHeight
        val etaSeconds = remainingBlocks * avgBlockTime

        Date((currentTimeStamp * 1000).toLong() + (etaSeconds * 1000).toLong())
    }

    fun averageBlockTimeFromChurns(churns: List<ChurnEntry>, pairs: Int = 6): Double? {
        try {
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
        } catch (t: Throwable) {
            Timber.e(t)
            return 6.0 // Fallback to default
        }
    }

    private suspend fun calculateBondMetrics(
        nodeAddress: String,
        myBondAddress: String,
    ): BondMetrics {
        // 1. Fetch node details
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

        // 3. Calculate ownership percentage
        val myBondOwnershipPercentage = if (totalBond > BigInteger.ZERO) {
            myBond.toBigDecimal().divide(totalBond.toBigDecimal(), 8, RoundingMode.HALF_UP)
        } else {
            BigDecimal.ZERO
        }

        // 4. Calculate node operator fee
        val nodeOperatorFee = (nodeData.bondProviders.nodeOperatorFee.toBigDecimalOrNull()
            ?: BigDecimal.ZERO).divide(BigDecimal(10_000), 8, RoundingMode.HALF_UP)

        // 5. Calculate current award after node operator fee
        val currentAward =
            (nodeData.currentAward.toBigDecimalOrNull()
                ?: BigDecimal.ZERO) * (BigDecimal.ONE - nodeOperatorFee)
        val myAward = myBondOwnershipPercentage * currentAward

        // 6. Get recent churn timestamp to calculate APY
        val churns = thorchainBondRepository.getChurns()
        val mostRecentChurn = churns.firstOrNull() ?: error("Can't get churns")
        val recentChurnTimestampNanos = mostRecentChurn.date.toDoubleOrNull()
            ?: error("Can't calculate churn")

        // 7. convert nanoseconds to seconds
        val recentChurnTimestamp = recentChurnTimestampNanos / 1_000_000_000.0

        // 8. Get current time in seconds since epoch
        val currentTime = System.currentTimeMillis() / 1000.0
        val timeDiff = currentTime - recentChurnTimestamp
        val timeDiffInYears = timeDiff / (60 * 60 * 24 * 365.25)

        // 9. Calculate APR & APY
        val apr = if (myBond > BigInteger.ZERO && timeDiffInYears > 0) {
            (myAward.divide(myBond.toBigDecimal(), 18, RoundingMode.HALF_UP))
                .divide(BigDecimal.valueOf(timeDiffInYears), 18, RoundingMode.HALF_UP)
        } else {
            BigDecimal.ZERO
        }

        val aprDouble = apr.toDouble()
        val apy = (1.0 + aprDouble / 365.0).pow(365.0) - 1.0

        return BondMetrics(
            myBond = myBond,
            myAward = myAward.toDouble(),
            apy = apy,
            nodeStatus = nodeData.status,
        )
    }
}

internal data class BondMetrics(
    val myBond: BigInteger,
    val myAward: Double,
    val apy: Double,
    val nodeStatus: String,
)

internal data class NetworkBondInfo(
    val apy: Double,
    val nextChurnDate: Date?,
)

data class ActiveBondedNode(
    val node: BondedNode,
    val amount: BigInteger,
    val coinId: String,
    val apy: Double,
    val nextReward: Double,
    val nextChurn: Date?,
) {
    data class BondedNode(
        val address: String,
        val state: String,
    )
}