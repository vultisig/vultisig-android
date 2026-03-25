package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.api.MayaBondProvider
import com.vultisig.wallet.data.api.MayaBondProviders
import com.vultisig.wallet.data.api.MayaChainApi
import com.vultisig.wallet.data.api.MayaMemberDetails
import com.vultisig.wallet.data.api.MayaMemberPool
import com.vultisig.wallet.data.api.MayaNodeInfo
import com.vultisig.wallet.data.api.MayaNodePool
import io.mockk.coEvery
import io.mockk.mockk
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class MayachainBondRepositoryImplTest {

    private lateinit var api: MayaChainApi
    private lateinit var repository: MayachainBondRepositoryImpl

    @BeforeEach
    fun setUp() {
        api = mockk()
        repository = MayachainBondRepositoryImpl(api)
    }

    @Test
    fun `getLpBondableAssets returns intersection of bondable pools and user LP pools`() = runTest {
        coEvery { api.getMayaNodePools() } returns
            listOf(
                MayaNodePool(asset = "MAYA.CACAO", status = "Available", bondable = true),
                MayaNodePool(asset = "ETH.ETH", status = "Available", bondable = true),
                MayaNodePool(asset = "BTC.BTC", status = "Available", bondable = false),
            )
        coEvery { api.getMemberDetails("addr1") } returns
            MayaMemberDetails(
                pools = listOf(MayaMemberPool("MAYA.CACAO"), MayaMemberPool("BTC.BTC"))
            )

        val result = repository.getLpBondableAssets("addr1")

        assertEquals(listOf("MAYA.CACAO"), result)
    }

    @Test
    fun `getLpBondableAssets returns empty list when user has no LP positions`() = runTest {
        coEvery { api.getMayaNodePools() } returns
            listOf(MayaNodePool(asset = "MAYA.CACAO", status = "Available", bondable = true))
        coEvery { api.getMemberDetails("addr1") } returns MayaMemberDetails(pools = emptyList())

        val result = repository.getLpBondableAssets("addr1")

        assertTrue(result.isEmpty())
    }

    @Test
    fun `getLpBondableAssets returns empty list when no bondable pools exist`() = runTest {
        coEvery { api.getMayaNodePools() } returns
            listOf(MayaNodePool(asset = "MAYA.CACAO", status = "Available", bondable = false))
        coEvery { api.getMemberDetails("addr1") } returns
            MayaMemberDetails(pools = listOf(MayaMemberPool("MAYA.CACAO")))

        val result = repository.getLpBondableAssets("addr1")

        assertTrue(result.isEmpty())
    }

    @Test
    fun `getLpBondableAssets returns empty list when user LP pools have no overlap with bondable assets`() =
        runTest {
            coEvery { api.getMayaNodePools() } returns
                listOf(MayaNodePool(asset = "MAYA.CACAO", status = "Available", bondable = true))
            coEvery { api.getMemberDetails("addr1") } returns
                MayaMemberDetails(pools = listOf(MayaMemberPool("ETH.ETH")))

            val result = repository.getLpBondableAssets("addr1")

            assertTrue(result.isEmpty())
        }

    @Test
    fun `getLpBondableAssets returns all bondable assets when user LP covers all of them`() =
        runTest {
            coEvery { api.getMayaNodePools() } returns
                listOf(
                    MayaNodePool(asset = "MAYA.CACAO", status = "Available", bondable = true),
                    MayaNodePool(asset = "ETH.ETH", status = "Available", bondable = true),
                )
            coEvery { api.getMemberDetails("addr1") } returns
                MayaMemberDetails(
                    pools = listOf(MayaMemberPool("MAYA.CACAO"), MayaMemberPool("ETH.ETH"))
                )

            val result = repository.getLpBondableAssets("addr1")

            assertEquals(setOf("MAYA.CACAO", "ETH.ETH"), result.toSet())
        }

    @Test
    fun `getLpBondableAssets excludes unavailable pools even if bondable flag is true`() = runTest {
        coEvery { api.getMayaNodePools() } returns
            listOf(
                MayaNodePool(asset = "MAYA.CACAO", status = "Staged", bondable = true),
                MayaNodePool(asset = "ETH.ETH", status = "Available", bondable = true),
            )
        coEvery { api.getMemberDetails("addr1") } returns
            MayaMemberDetails(
                pools = listOf(MayaMemberPool("MAYA.CACAO"), MayaMemberPool("ETH.ETH"))
            )

        val result = repository.getLpBondableAssets("addr1")

        assertEquals(listOf("ETH.ETH"), result)
    }

    @Test
    fun `getLpBondableAssets propagates exception from getMayaNodePools`() = runTest {
        val error = RuntimeException("API failure")
        coEvery { api.getMayaNodePools() } throws error

        val thrown = assertFailsWith<RuntimeException> { repository.getLpBondableAssets("addr1") }
        assertEquals("API failure", thrown.message)
    }

    @Test
    fun `getLpBondableAssets propagates exception from getMemberDetails`() = runTest {
        coEvery { api.getMayaNodePools() } returns
            listOf(MayaNodePool(asset = "MAYA.CACAO", status = "Available", bondable = true))
        val error = RuntimeException("Member API failure")
        coEvery { api.getMemberDetails("addr1") } throws error

        val thrown = assertFailsWith<RuntimeException> { repository.getLpBondableAssets("addr1") }
        assertEquals("Member API failure", thrown.message)
    }

    // --- getLpBondableAssetsWithUnits ---

    private fun noopNodes() = emptyList<MayaNodeInfo>()

    private fun nodeWith(bondAddress: String, pools: Map<String, String>) =
        MayaNodeInfo(
            nodeAddress = "node1",
            status = "Active",
            bondProviders =
                MayaBondProviders(
                    nodeOperatorFee = "0",
                    providers =
                        listOf(
                            MayaBondProvider(
                                bondAddress = bondAddress,
                                bonded = true,
                                pools = pools,
                            )
                        ),
                ),
        )

    @Test
    fun `getLpBondableAssetsWithUnits returns available units with pool depth`() = runTest {
        coEvery { api.getMayaNodePools() } returns
            listOf(
                MayaNodePool(
                    asset = "MAYA.CACAO",
                    status = "Available",
                    bondable = true,
                    lpUnits = "1000000",
                    balanceCacao = "5000000000000",
                )
            )
        coEvery { api.getMemberDetails("addr1") } returns
            MayaMemberDetails(
                pools = listOf(MayaMemberPool("MAYA.CACAO", liquidityUnits = "500000"))
            )
        coEvery { api.getAllNodes() } returns noopNodes()

        val result = repository.getLpBondableAssetsWithUnits("addr1")

        assertEquals(1, result.size)
        val pool = result["MAYA.CACAO"]!!
        assertEquals("500000", pool.availableUnits)
        assertEquals(1000000L, pool.totalPoolLpUnits)
        assertEquals(5000000000000L, pool.poolCacaoDepth)
    }

    @Test
    fun `getLpBondableAssetsWithUnits subtracts already-bonded units`() = runTest {
        coEvery { api.getMayaNodePools() } returns
            listOf(
                MayaNodePool(
                    asset = "MAYA.CACAO",
                    status = "Available",
                    bondable = true,
                    lpUnits = "1000000",
                    balanceCacao = "5000000000000",
                )
            )
        coEvery { api.getMemberDetails("addr1") } returns
            MayaMemberDetails(
                pools = listOf(MayaMemberPool("MAYA.CACAO", liquidityUnits = "500000"))
            )
        coEvery { api.getAllNodes() } returns
            listOf(nodeWith(bondAddress = "addr1", pools = mapOf("MAYA.CACAO" to "200000")))

        val result = repository.getLpBondableAssetsWithUnits("addr1")

        assertEquals("300000", result["MAYA.CACAO"]!!.availableUnits)
    }

    @Test
    fun `getLpBondableAssetsWithUnits excludes pool when all units are bonded`() = runTest {
        coEvery { api.getMayaNodePools() } returns
            listOf(
                MayaNodePool(
                    asset = "MAYA.CACAO",
                    status = "Available",
                    bondable = true,
                    lpUnits = "1000000",
                    balanceCacao = "5000000000000",
                )
            )
        coEvery { api.getMemberDetails("addr1") } returns
            MayaMemberDetails(
                pools = listOf(MayaMemberPool("MAYA.CACAO", liquidityUnits = "500000"))
            )
        coEvery { api.getAllNodes() } returns
            listOf(nodeWith(bondAddress = "addr1", pools = mapOf("MAYA.CACAO" to "500000")))

        val result = repository.getLpBondableAssetsWithUnits("addr1")

        assertTrue(result.isEmpty())
    }

    @Test
    fun `getLpBondableAssetsWithUnits returns empty when member has no LP positions`() = runTest {
        coEvery { api.getMayaNodePools() } returns
            listOf(MayaNodePool(asset = "MAYA.CACAO", status = "Available", bondable = true))
        coEvery { api.getMemberDetails("addr1") } returns MayaMemberDetails(pools = emptyList())

        val result = repository.getLpBondableAssetsWithUnits("addr1")

        assertTrue(result.isEmpty())
    }

    @Test
    fun `getLpBondableAssetsWithUnits propagates exception from getAllNodes`() = runTest {
        coEvery { api.getMayaNodePools() } returns
            listOf(MayaNodePool(asset = "MAYA.CACAO", status = "Available", bondable = true))
        coEvery { api.getMemberDetails("addr1") } returns
            MayaMemberDetails(
                pools = listOf(MayaMemberPool("MAYA.CACAO", liquidityUnits = "100000"))
            )
        val error = RuntimeException("Nodes API failure")
        coEvery { api.getAllNodes() } throws error

        val thrown =
            assertFailsWith<RuntimeException> { repository.getLpBondableAssetsWithUnits("addr1") }
        assertEquals("Nodes API failure", thrown.message)
    }
}
