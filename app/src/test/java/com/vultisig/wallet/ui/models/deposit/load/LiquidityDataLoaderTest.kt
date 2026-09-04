@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.deposit.load

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.repositories.MayachainBondRepository
import com.vultisig.wallet.data.usecases.GetThorChainLpPositionUseCase
import com.vultisig.wallet.ui.models.deposit.BondedUnitsCeiling
import com.vultisig.wallet.ui.models.deposit.DepositFormUiModel
import com.vultisig.wallet.ui.models.deposit.DepositOption
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/** Covers the node-scoped Unbond load: what it publishes, and how it reports having failed. */
internal class LiquidityDataLoaderTest {

    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = UnconfinedTestDispatcher(scheduler)
    private val scopeJob = Job()
    private val scope = CoroutineScope(dispatcher + scopeJob)

    private val bondRepository: MayachainBondRepository = mockk()
    private val thorLpPosition: GetThorChainLpPositionUseCase = mockk(relaxed = true)

    private val state =
        MutableStateFlow(
            DepositFormUiModel(depositChain = Chain.MayaChain, depositOption = DepositOption.Unbond)
        )
    private val address =
        MutableStateFlow<Address?>(
            Address(chain = Chain.MayaChain, address = VAULT_ADDRESS, accounts = emptyList())
        )
    private val assetsField = TextFieldState()
    private val lpUnitsField = TextFieldState()

    private val loader =
        LiquidityDataLoader(
            mayachainBondRepository = bondRepository,
            getThorChainLpPositionUseCase = thorLpPosition,
            scope = scope,
            state = state,
            address = address,
            assetsFieldState = assetsField,
            lpUnitsFieldState = lpUnitsField,
            vaultId = { "vault-1" },
            lpPoolId = { null },
            resolvePairedAddress = { _, _, _ -> null },
        )

    /** Waits for the load coroutine, whose repository call hops off the test dispatcher. */
    private suspend fun awaitLoad() {
        scopeJob.children.forEach { it.join() }
    }

    @Test
    fun `loadMayaBondedAssets lists the node's bonded pools and takes its ceiling from the first`() =
        runTest {
            givenBonded(mapOf("MAYA.CACAO" to 500_000L, "ETH.ETH" to 250_000L))

            loader.loadMayaBondedAssets(NODE)
            awaitLoad()

            val current = state.value
            assertEquals(listOf("MAYA.CACAO", "ETH.ETH"), current.bondableAssets)
            assertEquals("MAYA.CACAO", current.selectedBondAsset)
            assertEquals(
                BondedUnitsCeiling(nodeAddress = NODE, asset = "MAYA.CACAO", units = "500000"),
                current.bondedUnitsCeiling,
            )
            assertEquals("MAYA.CACAO", assetsField.text.toString())
            assertFalse(current.bondAssetsLoadFailed)
        }

    @Test
    fun `loadMayaBondedAssets includes a pool the vault is fully bonded to`() = runTest {
        // The Bond loader filters these out as having no surplus; Unbond exists to spend them.
        givenBonded(mapOf("MAYA.CACAO" to 500_000L))

        loader.loadMayaBondedAssets(NODE)
        awaitLoad()

        assertEquals(listOf("MAYA.CACAO"), state.value.bondableAssets)
        assertEquals("500000", state.value.bondedUnitsCeiling?.units)
    }

    @Test
    fun `loadMayaBondedAssets reports an empty node without calling it a failure`() = runTest {
        givenBonded(emptyMap())

        loader.loadMayaBondedAssets(NODE)
        awaitLoad()

        val current = state.value
        assertTrue(current.bondableAssets.isEmpty())
        assertNull(current.bondedUnitsCeiling)
        assertFalse(current.bondAssetsLoadFailed)
    }

    @Test
    fun `loadMayaBondedAssets flags a failed fetch rather than reporting an empty node`() =
        runTest {
            coEvery { bondRepository.getBondedLpUnitsOnNode(NODE, VAULT_ADDRESS) } throws
                RuntimeException("node api down")

            loader.loadMayaBondedAssets(NODE)
            awaitLoad()

            val current = state.value
            assertTrue(current.bondableAssets.isEmpty())
            assertNull(current.bondedUnitsCeiling)
            assertTrue(current.bondAssetsLoadFailed)
        }

    @Test
    fun `loadMayaBondedAssets asks nothing while the node address is blank`() = runTest {
        loader.loadMayaBondedAssets("")
        awaitLoad()

        coVerify(exactly = 0) { bondRepository.getBondedLpUnitsOnNode(any(), any()) }
        assertTrue(state.value.bondableAssets.isEmpty())
        assertFalse(state.value.bondAssetsLoadFailed)
    }

    @Test
    fun `loadMayaBondedAssets drops the ceiling loaded for a previous node`() = runTest {
        givenBonded(mapOf("MAYA.CACAO" to 500_000L))
        loader.loadMayaBondedAssets(NODE)
        awaitLoad()

        coEvery { bondRepository.getBondedLpUnitsOnNode("otherNode", VAULT_ADDRESS) } returns
            emptyMap()
        loader.loadMayaBondedAssets("otherNode")
        awaitLoad()

        assertNull(state.value.bondedUnitsCeiling)
        assertTrue(state.value.bondableAssets.isEmpty())
    }

    @Test
    fun `bondedCeilingFor answers only for the pools the loaded node holds`() = runTest {
        givenBonded(mapOf("MAYA.CACAO" to 500_000L))

        loader.loadMayaBondedAssets(NODE)
        awaitLoad()

        assertEquals("500000", loader.bondedCeilingFor("MAYA.CACAO")?.units)
        assertNull(loader.bondedCeilingFor("ETH.ETH"))
    }

    @Test
    fun `setMaxLpUnits fills the bonded ceiling when unbonding`() = runTest {
        givenBonded(mapOf("MAYA.CACAO" to 500_000L))
        loader.loadMayaBondedAssets(NODE)
        awaitLoad()
        lpUnitsField.setTextAndPlaceCursorAtEnd("1")

        loader.setMaxLpUnits()

        assertEquals("500000", lpUnitsField.text.toString())
    }

    private fun givenBonded(pools: Map<String, Long>) {
        coEvery { bondRepository.getBondedLpUnitsOnNode(NODE, VAULT_ADDRESS) } returns pools
    }

    private companion object {
        const val NODE = "mayaNode"
        const val VAULT_ADDRESS = "vaultMayaAddress"
    }
}
