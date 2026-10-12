package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.api.MayaBondProvider
import com.vultisig.wallet.data.api.MayaBondProviders
import com.vultisig.wallet.data.api.MayaMidgardHealth
import com.vultisig.wallet.data.api.MayaMidgardNetworkData
import com.vultisig.wallet.data.api.MayaNodeInfo
import com.vultisig.wallet.data.repositories.ActiveBondedNodeRepository
import com.vultisig.wallet.data.repositories.MayachainBondRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class MayachainBondUseCaseTest {

    private lateinit var repository: MayachainBondRepository
    private lateinit var activeBondedNodeRepository: ActiveBondedNodeRepository
    private lateinit var useCase: MayachainBondUseCaseImpl

    @BeforeEach
    fun setUp() {
        repository = mockk()
        activeBondedNodeRepository = mockk()
        useCase = MayachainBondUseCaseImpl(repository, activeBondedNodeRepository)
    }

    @Test
    fun `estimateNextChurnETA uses Long arithmetic to avoid precision loss for large timestamps`() =
        runTest {
            // A large Unix timestamp where Double precision would cause rounding errors.
            // Double has ~15-16 significant digits; a millisecond timestamp like 1_700_000_000_000
            // is 13 digits, but multiplying seconds by 1000 in Double can still lose the last
            // digit.
            val timestampSeconds = 1_700_000_000L // seconds since epoch
            val currentHeight = 1000
            val nextChurnHeight = 1010 // 10 blocks away @ 5s/block = 50s ETA

            coEvery { repository.getMidgardNetworkData() } returns
                MayaMidgardNetworkData(
                    bondingAPY = "0.1",
                    nextChurnHeight = nextChurnHeight.toString(),
                )
            coEvery { repository.getMidgardHealthData() } returns
                MayaMidgardHealth(
                    lastMayaNode =
                        MayaMidgardHealth.MayaHeightInfo(
                            height = currentHeight.toLong(),
                            timestamp = timestampSeconds,
                        )
                )
            coEvery { repository.getAllNodes() } returns listOf(nodeWithProvider(MY_ADDRESS))

            val nodes = useCase.getActiveNodesRemote(MY_ADDRESS)

            val expectedMs = timestampSeconds * 1000L + (10 * BLOCK_TIME_SECONDS * 1000).toLong()
            assertEquals(expectedMs, nodes.single().nextChurn?.time)
        }

    @Test
    fun `estimateNextChurnETA returns null when nextChurnHeight is not parseable`() = runTest {
        coEvery { repository.getMidgardNetworkData() } returns
            MayaMidgardNetworkData(bondingAPY = "0.1", nextChurnHeight = "not-a-number")
        coEvery { repository.getMidgardHealthData() } returns
            MayaMidgardHealth(
                lastMayaNode =
                    MayaMidgardHealth.MayaHeightInfo(height = 1000L, timestamp = 1_700_000_000L)
            )
        coEvery { repository.getAllNodes() } returns listOf(nodeWithProvider(MY_ADDRESS))

        val nodes = useCase.getActiveNodesRemote(MY_ADDRESS)

        assertNull(nodes.single().nextChurn)
    }

    @Test
    fun `estimateNextChurnETA returns null when nextChurnHeight is already past`() = runTest {
        coEvery { repository.getMidgardNetworkData() } returns
            MayaMidgardNetworkData(
                bondingAPY = "0.1",
                nextChurnHeight = "999", // less than currentHeight
            )
        coEvery { repository.getMidgardHealthData() } returns
            MayaMidgardHealth(
                lastMayaNode =
                    MayaMidgardHealth.MayaHeightInfo(height = 1000L, timestamp = 1_700_000_000L)
            )
        coEvery { repository.getAllNodes() } returns listOf(nodeWithProvider(MY_ADDRESS))

        val nodes = useCase.getActiveNodesRemote(MY_ADDRESS)

        assertNull(nodes.single().nextChurn)
    }

    @Test
    fun `next reward is the provider's own reward row, not an LP-unit share of the node award`() =
        runTest {
            coEvery { repository.getMidgardNetworkData() } returns
                MayaMidgardNetworkData(bondingAPY = "0.1", nextChurnHeight = "0")
            coEvery { repository.getMidgardHealthData() } returns
                MayaMidgardHealth(
                    lastMayaNode = MayaMidgardHealth.MayaHeightInfo(height = 1, timestamp = 0)
                )
            // Live MAYANode reading at block 17904022: LP units in different pools are not
            // proportional to `bond`, so weighing by `pools` would pay 5948869444332 instead.
            coEvery { repository.getAllNodes() } returns
                listOf(
                    MayaNodeInfo(
                        nodeAddress = NODE_ADDRESS,
                        status = "Active",
                        reward = "7000694276124",
                        bondProviders =
                            MayaBondProviders(
                                nodeOperatorFee = "1500",
                                providers =
                                    listOf(
                                        MayaBondProvider(
                                            bondAddress = OTHER_ADDRESS,
                                            reward = "1050300962182",
                                            pools = mapOf("THOR.RUNE" to "100999092998"),
                                        ),
                                        MayaBondProvider(
                                            bondAddress = MY_ADDRESS,
                                            reward = "5950393313942",
                                            pools = mapOf("ARB.USDT" to "349179856971119"),
                                        ),
                                    ),
                            ),
                    )
                )

            val node = useCase.getActiveNodesRemote(MY_ADDRESS).single()

            assertEquals(5_950_393_313_942.0, node.nextReward)
        }

    @Test
    fun `a MAYANode node decodes its accruing award from the reward key`() {
        val node =
            Json { ignoreUnknownKeys = true }
                .decodeFromString<MayaNodeInfo>(
                    """
                    {"node_address":"$NODE_ADDRESS","status":"Active","bond":"1",
                     "reward":"567911856519686",
                     "bond_providers":{"node_operator_fee":"5000","providers":[]}}
                    """
                )

        assertEquals("567911856519686", node.reward)
    }

    private fun nodeWithProvider(address: String) =
        MayaNodeInfo(
            nodeAddress = NODE_ADDRESS,
            status = "Active",
            bondProviders =
                MayaBondProviders(
                    nodeOperatorFee = "0",
                    providers =
                        listOf(
                            MayaBondProvider(
                                bondAddress = address,
                                pools = mapOf("CACAO.CACAO" to "1000000"),
                            )
                        ),
                ),
        )

    private companion object {
        const val MY_ADDRESS = "maya1testaddress"
        const val OTHER_ADDRESS = "maya1otheraddress"
        const val NODE_ADDRESS = "maya1nodeaddress"

        // Must match avgBlockTime in MayachainBondUseCase.estimateNextChurnETA
        const val BLOCK_TIME_SECONDS = 5.0
    }
}
