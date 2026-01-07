package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.api.MayaChainApi
import com.vultisig.wallet.data.api.models.maya.MayaBondNode
import com.vultisig.wallet.data.api.models.maya.MayaBondedNodesResponse
import com.vultisig.wallet.data.blockchain.model.BondedNodePosition
import com.vultisig.wallet.data.blockchain.model.BondedNodePosition.Companion.generateBondedId
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.repositories.MayaBondRepository
import kotlinx.coroutines.supervisorScope
import timber.log.Timber
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

interface MayaBondUseCase {
    suspend fun getActiveNodesRemote(address: String): List<BondedNodePosition>
}

@Singleton
class MayaBondUseCaseImpl @Inject constructor(
    private val mayaChainApi: MayaChainApi,
    private val mayaBondRepository: MayaBondRepository,
) : MayaBondUseCase {
    override suspend fun getActiveNodesRemote(address: String): List<BondedNodePosition> =
        supervisorScope {
            val activeNodes = mutableListOf<BondedNodePosition>()
            val bondedNodeAddresses = mutableSetOf<String>()

            try {
                val bondedNodes = getBondedNodes(address)
                val nextChurn = estimateNextChurnETA()

                for (node in bondedNodes.nodes) {
                    bondedNodeAddresses += node.address
                    try {
                        val myBondMetrics = calculateBondMetrics(
                            nodeAddress = node.address,
                            myBondAddress = address
                        )

                        val bondNode = BondedNodePosition.BondedNode(
                            address = node.address,
                            state = node.status,
                        )

                        val activeNode = BondedNodePosition(
                            id = Coins.MayaChain.MAYA.generateBondedId(node.address),
                            coin = Coins.MayaChain.MAYA,
                            node = bondNode,
                            amount = myBondMetrics.myBond,
                            apy = myBondMetrics.apr,
                            nextReward = myBondMetrics.myAward.toDouble(), // TODO: Check rewards decimals
                            nextChurn = nextChurn,
                        )

                        activeNodes += activeNode
                    } catch (e: Exception) {
                        Timber.e(e, "Error calculating metrics for node ${node.address}")
                    }
                }
            } catch (t: Throwable) {
                Timber.e(t, "Error fetching bonded nodes for address: $address")
                throw t
            }

            return@supervisorScope activeNodes.toList()
        }

    private suspend fun getBondedNodes(address: String): MayaBondedNodesResponse {
        return try {
            val allNodes = mayaChainApi.getAllNodes()

            val bondedNodes = mutableListOf<MayaBondNode>()
            var totalBonded = BigDecimal.ZERO

            for (node in allNodes) {
                for (provider in node.bondProviders.providers) {
                    if (provider.bondAddress == address) {
                        val providerBond = provider.bond.toBigDecimalOrNull() ?: continue

                        val mayaNode = MayaBondNode(
                            status = node.status,
                            address = node.nodeAddress,
                            bond = provider.bond
                        )
                        bondedNodes.add(mayaNode)
                        totalBonded += providerBond
                    }
                }
            }

            MayaBondedNodesResponse(
                totalBonded = totalBonded.toPlainString(),
                nodes = bondedNodes
            )
        } catch (e: Exception) {
            Timber.e(e)
            throw e
        }
    }

    private suspend fun estimateNextChurnETA(): Date? {
        return try {
            val health = mayaBondRepository.getHealth()
            val network = mayaBondRepository.getNetworkInfo()

            val nextChurnHeight = network.nextChurnHeight
                ?.toLongOrNull()
                ?: return null

            val currentHeight = health.lastMayaNode.height
            val currentTimestamp = health.lastMayaNode.timestamp.toDouble()

            if (nextChurnHeight <= currentHeight) return null

            // Maya has approximately 5 seconds per block
            val avgBlockTimeSeconds = 5.0

            val remainingBlocks = nextChurnHeight - currentHeight
            val etaSeconds = remainingBlocks * avgBlockTimeSeconds

            val currentDate = Date((currentTimestamp * 1000).toLong())
            Date(currentDate.time + (etaSeconds * 1000).toLong())
        } catch (t: Throwable) {
            Timber.e(t)
            null
        }
    }

    suspend fun calculateBondMetrics(
        nodeAddress: String,
        myBondAddress: String
    ): MayaBondMetrics {
        val nodeData = mayaChainApi.getNodeDetails(nodeAddress)
        val bondProviders = nodeData.bondProviders.providers

        // 2. Calculate my bond and total bond
        var myBond = BigInteger.ZERO
        var totalBond = BigInteger.ZERO

        for (provider in bondProviders) {
            val providerBond = provider.bond
                .toBigIntegerOrNull()
                ?: BigInteger.ZERO

            if (provider.bondAddress == myBondAddress) {
                myBond = providerBond
            }
            totalBond = totalBond.add(providerBond)
        }

        // 3. Calculate ownership percentage
        val myBondOwnershipPercentage =
            if (totalBond > BigInteger.ZERO)
                myBond.toBigDecimal().divide(totalBond.toBigDecimal(), 18, RoundingMode.HALF_UP)
            else
                BigDecimal.ZERO

        // 4. Calculate node operator fee (basis points → percentage)
        val nodeOperatorFee = nodeData.bondProviders.nodeOperatorFee
            .toBigDecimalOrNull()
            ?.divide(BigDecimal("10000"), 18, RoundingMode.HALF_UP)
            ?: BigDecimal.ZERO

        // 5. Calculate current award after node operator fee
        val currentAward = nodeData.currentAward
            .toBigDecimalOrNull()
            ?.multiply(BigDecimal.ONE.subtract(nodeOperatorFee))
            ?: BigDecimal.ZERO

        val myAward = myBondOwnershipPercentage.multiply(currentAward)

        // 6. Get network info to estimate APR
        val network = mayaBondRepository.getNetworkInfo()
        val networkAPR = network.bondingAPY
            ?.toDoubleOrNull()
            ?: 0.0

        return MayaBondMetrics(
            myBond = myBond,
            myAward = myAward,
            apr = networkAPR,
            nodeStatus = nodeData.status
        )
    }
}

data class MayaBondMetrics(
    val myBond: BigInteger,
    val myAward: BigDecimal,
    val apr: Double,
    val nodeStatus: String
)

data class MayaNetworkBondInfo(
    val apr: Double,
    val nextChurnDate: Date?
)