@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.deposit.load

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.repositories.MayachainBondRepository
import com.vultisig.wallet.data.usecases.GetThorChainLpPositionUseCase
import com.vultisig.wallet.ui.models.deposit.BondAssetsState
import com.vultisig.wallet.ui.models.deposit.BondedUnitsCeiling
import com.vultisig.wallet.ui.models.deposit.DepositFormUiModel
import com.vultisig.wallet.ui.models.deposit.DepositOption
import com.vultisig.wallet.ui.models.deposit.bondedUnitsCeiling
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
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
            current.bondableAssets shouldBe listOf("MAYA.CACAO", "ETH.ETH")
            current.selectedBondAsset shouldBe "MAYA.CACAO"
            current.bondAssetsState shouldBe
                BondAssetsState.Loaded(
                    BondedUnitsCeiling(nodeAddress = NODE, asset = "MAYA.CACAO", units = "500000")
                )
            assetsField.text.toString() shouldBe "MAYA.CACAO"
        }

    @Test
    fun `loadMayaBondedAssets includes a pool the vault is fully bonded to`() = runTest {
        // The Bond loader filters these out as having no surplus; Unbond exists to spend them.
        givenBonded(mapOf("MAYA.CACAO" to 500_000L))

        loader.loadMayaBondedAssets(NODE)
        awaitLoad()

        state.value.bondableAssets shouldBe listOf("MAYA.CACAO")
        state.value.bondedUnitsCeiling?.units shouldBe "500000"
    }

    @Test
    fun `loadMayaBondedAssets reports an empty node without calling it a failure`() = runTest {
        givenBonded(emptyMap())

        loader.loadMayaBondedAssets(NODE)
        awaitLoad()

        val current = state.value
        current.bondableAssets.shouldBeEmpty()
        // Loaded, not Failed: the node was asked and answered that it holds nothing.
        current.bondAssetsState shouldBe BondAssetsState.Loaded()
    }

    @Test
    fun `loadMayaBondedAssets flags a failed fetch rather than reporting an empty node`() =
        runTest {
            coEvery { bondRepository.getBondedLpUnitsOnNode(NODE, VAULT_ADDRESS) } throws
                RuntimeException("node api down")

            loader.loadMayaBondedAssets(NODE)
            awaitLoad()

            val current = state.value
            current.bondableAssets.shouldBeEmpty()
            current.bondAssetsState shouldBe BondAssetsState.Failed
        }

    @Test
    fun `loadMayaBondedAssets reports loading before it can claim the node is empty`() = runTest {
        val fetch = CompletableDeferred<Map<String, Long>>()
        coEvery { bondRepository.getBondedLpUnitsOnNode(NODE, VAULT_ADDRESS) } coAnswers
            {
                fetch.await()
            }

        loader.loadMayaBondedAssets(NODE)

        // The pool list is empty in flight exactly as it is on an empty node, so only the state
        // stops the form from telling the user this node holds nothing before it has been asked.
        state.value.bondableAssets.shouldBeEmpty()
        state.value.bondAssetsState shouldBe BondAssetsState.Loading

        fetch.complete(mapOf("MAYA.CACAO" to 500_000L))
        awaitLoad()

        state.value.bondedUnitsCeiling?.units shouldBe "500000"
    }

    @Test
    fun `loadMayaBondedAssets asks nothing while the node address is blank`() = runTest {
        loader.loadMayaBondedAssets("")
        awaitLoad()

        coVerify(exactly = 0) { bondRepository.getBondedLpUnitsOnNode(any(), any()) }
        state.value.bondableAssets.shouldBeEmpty()
        // Idle, not Failed: a form with no node named yet has nothing to retry.
        state.value.bondAssetsState shouldBe BondAssetsState.Idle
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

        state.value.bondedUnitsCeiling shouldBe null
        state.value.bondableAssets.shouldBeEmpty()
    }

    @Test
    fun `clearBondedAssets leaves no pool of the previous node selectable`() = runTest {
        givenBonded(mapOf("MAYA.CACAO" to 500_000L))
        loader.loadMayaBondedAssets(NODE)
        awaitLoad()
        lpUnitsField.setTextAndPlaceCursorAtEnd("500000")

        loader.clearBondedAssets()

        // The picker runs off bondableAssets and selecting one reads bondedCeilingFor, so both
        // have to be empty for the debounce window to hold no ceiling from the previous node.
        state.value.bondableAssets.shouldBeEmpty()
        state.value.selectedBondAsset shouldBe ""
        loader.bondedCeilingFor("MAYA.CACAO") shouldBe null
        state.value.bondAssetsState shouldBe BondAssetsState.Idle
        assetsField.text.toString() shouldBe ""
        lpUnitsField.text.toString() shouldBe ""
    }

    @Test
    fun `bondedCeilingFor answers only for the pools the loaded node holds`() = runTest {
        givenBonded(mapOf("MAYA.CACAO" to 500_000L))

        loader.loadMayaBondedAssets(NODE)
        awaitLoad()

        loader.bondedCeilingFor("MAYA.CACAO")?.units shouldBe "500000"
        loader.bondedCeilingFor("ETH.ETH") shouldBe null
    }

    @Test
    fun `setMaxLpUnits fills the bonded ceiling when unbonding`() = runTest {
        givenBonded(mapOf("MAYA.CACAO" to 500_000L))
        loader.loadMayaBondedAssets(NODE)
        awaitLoad()
        lpUnitsField.setTextAndPlaceCursorAtEnd("1")

        loader.setMaxLpUnits()

        lpUnitsField.text.toString() shouldBe "500000"
    }

    private fun givenBonded(pools: Map<String, Long>) {
        coEvery { bondRepository.getBondedLpUnitsOnNode(NODE, VAULT_ADDRESS) } returns pools
    }

    private companion object {
        const val NODE = "mayaNode"
        const val VAULT_ADDRESS = "vaultMayaAddress"
    }
}
