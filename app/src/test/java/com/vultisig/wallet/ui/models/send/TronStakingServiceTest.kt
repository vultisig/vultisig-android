@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.send

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import com.vultisig.wallet.data.blockchain.tron.GetTronFrozenBalancesUseCase
import com.vultisig.wallet.data.blockchain.tron.TronFrozenBalanceState
import com.vultisig.wallet.data.blockchain.tron.TronFrozenBalances
import com.vultisig.wallet.data.blockchain.tron.TronResourceType
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.ui.screens.v2.defi.model.DeFiNavActions
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.mockk
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class TronStakingServiceTest {

    private val scheduler = TestCoroutineScheduler()
    private val mainDispatcher = UnconfinedTestDispatcher(scheduler)

    private val uiState = MutableStateFlow(SendFormUiModel())
    private val tronFrozenBalances =
        MutableStateFlow<TronFrozenBalanceState>(TronFrozenBalanceState.Loading)
    private val tokenAmountFieldState = TextFieldState()
    private val fiatAmountFieldState = TextFieldState()
    private val memoFieldState = TextFieldState()

    private var defiType: DeFiNavActions? = null
    private var vault: Vault? = vaultWithTrx()

    private val vaultRepository: VaultRepository = mockk(relaxed = true)
    private val getTronFrozenBalances: GetTronFrozenBalancesUseCase = mockk(relaxed = true)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ──────── isStakingType ────────

    @Test
    fun `isStakingType true for FREEZE_TRX and UNFREEZE_TRX, false otherwise`() {
        val service = build(emptyScope())
        defiType = DeFiNavActions.FREEZE_TRX
        assertTrue(service.isStakingType())
        defiType = DeFiNavActions.UNFREEZE_TRX
        assertTrue(service.isStakingType())
        defiType = DeFiNavActions.BOND
        assertFalse(service.isStakingType())
        defiType = null
        assertFalse(service.isStakingType())
    }

    // ──────── currentFrozenBalance ────────

    @Test
    fun `currentFrozenBalance is null when uiState has no resource type`() {
        val service = build(emptyScope())
        tronFrozenBalances.value = TronFrozenBalanceState.Loaded(balances())
        assertNull(service.currentFrozenBalance())
    }

    @Test
    fun `currentFrozenBalance is null when state is Loading or Error`() {
        val service = build(emptyScope())
        uiState.value = uiState.value.copy(tronResourceType = TronResourceType.BANDWIDTH)

        tronFrozenBalances.value = TronFrozenBalanceState.Loading
        assertNull(service.currentFrozenBalance())

        tronFrozenBalances.value = TronFrozenBalanceState.Error
        assertNull(service.currentFrozenBalance())
    }

    @Test
    fun `currentFrozenBalance returns the balance for the active resource type`() {
        val service = build(emptyScope())
        tronFrozenBalances.value = TronFrozenBalanceState.Loaded(balances())

        uiState.value = uiState.value.copy(tronResourceType = TronResourceType.BANDWIDTH)
        assertEquals(BigDecimal("100"), service.currentFrozenBalance())

        uiState.value = uiState.value.copy(tronResourceType = TronResourceType.ENERGY)
        assertEquals(BigDecimal("50"), service.currentFrozenBalance())
    }

    // ──────── setResourceType ────────

    @Test
    fun `setResourceType is a no-op while a send is in flight`() {
        defiType = DeFiNavActions.FREEZE_TRX
        uiState.value = uiState.value.copy(isLoading = true)
        memoFieldState.setTextAndPlaceCursorAtEnd("untouched")
        val service = build(emptyScope())

        service.setResourceType(TronResourceType.ENERGY)

        assertNull(uiState.value.tronResourceType)
        assertEquals("untouched", memoFieldState.text.toString())
    }

    @Test
    fun `setResourceType is a no-op when the resource type has not changed`() {
        defiType = DeFiNavActions.FREEZE_TRX
        uiState.value =
            uiState.value.copy(
                tronResourceType = TronResourceType.BANDWIDTH,
                tronBalanceAvailableOverride = "stale",
            )
        val service = build(emptyScope())

        service.setResourceType(TronResourceType.BANDWIDTH)

        // The override would be cleared if we had taken the full update path — we did not.
        assertEquals("stale", uiState.value.tronBalanceAvailableOverride)
    }

    @Test
    fun `setResourceType FREEZE_TRX clears the input fields and applies the freeze memo`() {
        defiType = DeFiNavActions.FREEZE_TRX
        tokenAmountFieldState.setTextAndPlaceCursorAtEnd("123")
        fiatAmountFieldState.setTextAndPlaceCursorAtEnd("456")
        val service = build(emptyScope())

        service.setResourceType(TronResourceType.ENERGY)

        assertEquals(TronResourceType.ENERGY, uiState.value.tronResourceType)
        assertNull(uiState.value.tronBalanceAvailableOverride)
        assertNull(uiState.value.selectedAmountFraction)
        assertEquals("", tokenAmountFieldState.text.toString())
        assertEquals("", fiatAmountFieldState.text.toString())
        // Memo gets a non-empty staking-memo string. The exact bytes are the encoder's job to
        // test; here we just pin that the field was rewritten.
        assertTrue(memoFieldState.text.toString().isNotEmpty())
    }

    @Test
    fun `setResourceType UNFREEZE_TRX additionally rewrites the override from loaded balances`() {
        defiType = DeFiNavActions.UNFREEZE_TRX
        tronFrozenBalances.value = TronFrozenBalanceState.Loaded(balances())
        val service = build(emptyScope())

        service.setResourceType(TronResourceType.ENERGY)

        // 50 → "50" after stripTrailingZeros + toPlainString.
        assertEquals("50", uiState.value.tronBalanceAvailableOverride)
    }

    // ──────── initIfStakingType ────────

    @Test
    fun `initIfStakingType is a no-op when not staking`() =
        runTest(mainDispatcher) {
            defiType = null
            val service = build(backgroundScope)

            service.initIfStakingType()
            advanceUntilIdle()

            assertEquals("", memoFieldState.text.toString())
            confirmVerified(vaultRepository, getTronFrozenBalances)
        }

    @Test
    fun `initIfStakingType FREEZE_TRX applies the BANDWIDTH memo and does not load balances`() =
        runTest(mainDispatcher) {
            defiType = DeFiNavActions.FREEZE_TRX
            val service = build(backgroundScope)

            service.initIfStakingType()
            advanceUntilIdle()

            assertTrue(memoFieldState.text.toString().isNotEmpty())
            coVerify(exactly = 0) { getTronFrozenBalances(any()) }
        }

    @Test
    fun `initIfStakingType UNFREEZE_TRX loads frozen balances and publishes Loaded state`() =
        runTest(mainDispatcher) {
            defiType = DeFiNavActions.UNFREEZE_TRX
            coEvery { getTronFrozenBalances(TRX_ADDRESS) } returns balances()
            val service = build(backgroundScope)

            service.initIfStakingType()
            advanceUntilIdle()

            assertTrue(tronFrozenBalances.value is TronFrozenBalanceState.Loaded)
            assertFalse(uiState.value.hasTronFrozenBalancesError)
            assertFalse(uiState.value.isTronFrozenBalancesLoading)
        }

    @Test
    fun `initIfStakingType UNFREEZE_TRX flips to Error when the use case throws`() =
        runTest(mainDispatcher) {
            defiType = DeFiNavActions.UNFREEZE_TRX
            coEvery { getTronFrozenBalances(any()) } throws RuntimeException("network")
            val service = build(backgroundScope)

            service.initIfStakingType()
            advanceUntilIdle()

            assertEquals(TronFrozenBalanceState.Error, tronFrozenBalances.value)
            assertTrue(uiState.value.hasTronFrozenBalancesError)
        }

    @Test
    fun `initIfStakingType UNFREEZE_TRX flips to Error when the vault has no TRX coin`() =
        runTest(mainDispatcher) {
            defiType = DeFiNavActions.UNFREEZE_TRX
            vault = vaultWithoutTrx()
            val service = build(backgroundScope)

            service.initIfStakingType()
            advanceUntilIdle()

            assertEquals(TronFrozenBalanceState.Error, tronFrozenBalances.value)
            assertTrue(uiState.value.hasTronFrozenBalancesError)
        }

    // ──────── helpers ────────

    private fun build(scope: CoroutineScope) =
        TronStakingService(
            scope = scope,
            uiState = uiState,
            tronFrozenBalances = tronFrozenBalances,
            tokenAmountFieldState = tokenAmountFieldState,
            fiatAmountFieldState = fiatAmountFieldState,
            memoFieldState = memoFieldState,
            defiTypeProvider = { defiType },
            vaultProvider = { vault },
            vaultIdProvider = { "vault-id" },
            vaultRepository = vaultRepository,
            getTronFrozenBalances = getTronFrozenBalances,
        )

    private fun emptyScope(): CoroutineScope = CoroutineScope(mainDispatcher)

    private fun balances(): TronFrozenBalances =
        TronFrozenBalances(bandwidthTrx = BigDecimal("100"), energyTrx = BigDecimal("50"))

    private fun trxCoin(): Coin =
        Coin(
            chain = Chain.Tron,
            ticker = "TRX",
            logo = "",
            address = TRX_ADDRESS,
            decimal = 6,
            hexPublicKey = "",
            priceProviderID = "tron",
            contractAddress = "",
            isNativeToken = true,
        )

    private fun vaultWithTrx(): Vault =
        Vault(id = "vault-id", name = "test", coins = listOf(trxCoin()))

    private fun vaultWithoutTrx(): Vault =
        Vault(id = "vault-id", name = "test", coins = emptyList())

    private companion object {
        const val TRX_ADDRESS = "TQXf6jmaJzQNUC5tBvRiJZi7uCuSe7frMy"
    }
}
