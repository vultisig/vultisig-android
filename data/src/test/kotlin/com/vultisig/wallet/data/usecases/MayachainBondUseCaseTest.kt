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

            val expectedMs = timestampSeconds * 1000L + (10 * 5.0 * 1000).toLong()
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

    private fun nodeWithProvider(address: String) =
        MayaNodeInfo(
            nodeAddress = NODE_ADDRESS,
            status = "Active",
            currentAward = "0",
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
        const val NODE_ADDRESS = "maya1nodeaddress"
    }
}
