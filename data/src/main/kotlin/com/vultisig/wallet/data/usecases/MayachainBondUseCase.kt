package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.api.MayaMidgardNetworkData
import com.vultisig.wallet.data.blockchain.model.BondedNodePosition
import com.vultisig.wallet.data.blockchain.model.BondedNodePosition.Companion.generateBondedId
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.repositories.ActiveBondedNodeRepository
import com.vultisig.wallet.data.repositories.MayachainBondRepository
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.supervisorScope
import timber.log.Timber

interface MayachainBondUseCase {
    suspend fun getActiveNodes(vaultId: String, address: String): Flow<List<BondedNodePosition>>

    suspend fun getActiveNodesRemote(address: String): List<BondedNodePosition>
}

class MayachainBondUseCaseImpl
@Inject
constructor(
    private val mayachainBondRepository: MayachainBondRepository,
    private val activeBondedNodeRepository: ActiveBondedNodeRepository,
) : MayachainBondUseCase {

    override suspend fun getActiveNodes(
        vaultId: String,
        address: String,
    ): Flow<List<BondedNodePosition>> =
        flow {
                try {
                    val cachedNodes = activeBondedNodeRepository.getBondedNodes(vaultId)
                    if (cachedNodes.isNotEmpty()) {
                        Timber.d(
                            "MayachainBondUseCase: Emitting ${cachedNodes.size} cached bonded nodes for vault $vaultId"
                        )
                        emit(cachedNodes)
                    }

                    val freshNodes = getActiveNodesRemote(address)

                    Timber.d(
                        "MayachainBondUseCase: Emitting ${freshNodes.size} fresh bonded nodes for vault $vaultId"
                    )

                    if (freshNodes.isEmpty()) {
                        activeBondedNodeRepository.deleteBondedNodes(vaultId)
                    } else {
                        activeBondedNodeRepository.deleteBondedNodes(vaultId)
                        activeBondedNodeRepository.saveBondedNodes(vaultId, freshNodes)
                    }

                    emit(freshNodes)
                } catch (e: Exception) {
                    Timber.e(
                        e,
                        "MayachainBondUseCase: Error fetching bonded nodes for vault $vaultId",
                    )

                    val cachedNodes = activeBondedNodeRepository.getBondedNodes(vaultId)
                    if (cachedNodes.isNotEmpty()) {
                        emit(cachedNodes)
                    } else {
                        throw e
                    }
                }
            }
            .flowOn(Dispatchers.IO)

    override suspend fun getActiveNodesRemote(address: String): List<BondedNodePosition> =
        supervisorScope {
            val activeNodes = mutableListOf<BondedNodePosition>()

            try {
                val networkInfoDeferred = async { getNetworkInfo() }
                val allNodes = mayachainBondRepository.getAllNodes()
                val networkInfo = networkInfoDeferred.await()

                for (node in allNodes) {
                    val myProvider =
                        node.bondProviders.providers.find { it.bondAddress == address } ?: continue

                    val myBondMetrics =
                        calculateBondMetrics(
                            nodeAddress = node.nodeAddress,
                            myBondAddress = address,
                            networkApy = networkInfo.apy,
                        )

                    val bondNode =
                        BondedNodePosition.BondedNode(
                            address = node.nodeAddress,
                            state = node.status,
                        )

                    val activeNode =
                        BondedNodePosition(
                            id = Coins.MayaChain.CACAO.generateBondedId(node.nodeAddress),
                            coin = Coins.MayaChain.CACAO,
                            node = bondNode,
                            amount = myBondMetrics.myBond,
                            apy = myBondMetrics.apy,
                            nextReward = myBondMetrics.myAward,
                            nextChurn = networkInfo.nextChurnDate,
                        )
                    activeNodes.add(activeNode)
                }
            } catch (t: Throwable) {
                Timber.e(t)
                throw t
            }

            return@supervisorScope activeNodes.toList()
        }

    private suspend fun getNetworkInfo(): MayaNetworkBondInfo {
        val network = mayachainBondRepository.getMidgardNetworkData()
        val apy = runCatching { network.bondingAPY.toBigDecimal().toDouble() }.getOrDefault(0.0)
        val nextChurnDate = estimateNextChurnETA(network)
        return MayaNetworkBondInfo(apy = apy, nextChurnDate = nextChurnDate)
    }

    private suspend fun estimateNextChurnETA(network: MayaMidgardNetworkData): Date? =
        supervisorScope {
            val health = mayachainBondRepository.getMidgardHealthData()

            val nextChurnHeight =
                network.nextChurnHeight.toIntOrNull() ?: return@supervisorScope null
            val currentHeight = health.lastMayaNode.height
            val currentTimestamp = health.lastMayaNode.timestamp.toDouble()

            if (nextChurnHeight <= currentHeight) return@supervisorScope null

            // Maya has approximately 5 seconds per block
            val avgBlockTime = 5.0
            val remainingBlocks = nextChurnHeight - currentHeight
            val etaSeconds = remainingBlocks * avgBlockTime

            Date((currentTimestamp * 1000).toLong() + (etaSeconds * 1000).toLong())
        }

    private suspend fun calculateBondMetrics(
        nodeAddress: String,
        myBondAddress: String,
        networkApy: Double,
    ): MayaBondMetrics {
        val nodeData = mayachainBondRepository.getNodeDetails(nodeAddress)
        val bondProviders = nodeData.bondProviders.providers

        var myBond = BigInteger.ZERO
        var totalBond = BigInteger.ZERO
        for (provider in bondProviders) {
            val providerBond =
                provider.pools.values.sumOf { it.toBigIntegerOrNull() ?: BigInteger.ZERO }
            if (provider.bondAddress == myBondAddress) {
                myBond = providerBond
            }
            totalBond += providerBond
        }

        val myBondOwnershipPercentage =
            if (totalBond > BigInteger.ZERO) {
                myBond.toBigDecimal().divide(totalBond.toBigDecimal(), 8, RoundingMode.DOWN)
            } else {
                BigDecimal.ZERO
            }

        val nodeOperatorFee =
            (nodeData.bondProviders.nodeOperatorFee.toBigDecimalOrNull() ?: BigDecimal.ZERO).divide(
                BigDecimal(10_000),
                8,
                RoundingMode.DOWN,
            )

        val currentAward =
            (nodeData.currentAward.toBigDecimalOrNull() ?: BigDecimal.ZERO) *
                (BigDecimal.ONE - nodeOperatorFee)
        val myAward = myBondOwnershipPercentage * currentAward

        return MayaBondMetrics(
            myBond = myBond,
            myAward = myAward.toDouble(),
            apy = networkApy,
            nodeStatus = nodeData.status,
        )
    }
}

internal data class MayaBondMetrics(
    val myBond: BigInteger,
    val myAward: Double,
    val apy: Double,
    val nodeStatus: String,
)

internal data class MayaNetworkBondInfo(val apy: Double, val nextChurnDate: Date?)
