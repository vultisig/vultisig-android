@file:OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)

package com.vultisig.wallet.ui.models.send

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.settings.AppCurrency
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.ui.models.mappers.AccountToTokenBalanceUiModelMapper
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.navigation.back
import com.vultisig.wallet.ui.utils.UiText
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.timeout
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class SendFormViewModelAmountTest {

    private val scheduler = TestCoroutineScheduler()
    private val mainDispatcher = UnconfinedTestDispatcher(scheduler)

    private val savedStateHandle: SavedStateHandle = mockk(relaxed = true)
    private val appCurrencyRepository: AppCurrencyRepository = mockk(relaxed = true)
    private val navigator: Navigator<Destination> = mockk(relaxed = true)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        mockkStatic("androidx.navigation.SavedStateHandleKt")
        every { savedStateHandle.toRoute<Route.Send>() } returns Route.Send(vaultId = VAULT_ID)
        every { appCurrencyRepository.currency } returns flowOf(AppCurrency.USD)
        every { appCurrencyRepository.defaultCurrency } returns AppCurrency.USD
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic("androidx.navigation.SavedStateHandleKt")
    }

    @Test
    fun `validateTokenAmount sets error for empty amount`() = runTest {
        val vm = buildViewModel()
        advanceUntilIdle()

        vm.validateTokenAmount()

        assertNotNull(vm.uiState.value.tokenAmountError)
    }

    @Test
    fun `validateTokenAmount sets error for zero`() = runTest {
        val vm = buildViewModel()
        advanceUntilIdle()
        vm.tokenAmountFieldState.setTextAndPlaceCursorAtEnd("0")

        vm.validateTokenAmount()

        assertNotNull(vm.uiState.value.tokenAmountError)
    }

    @Test
    fun `validateTokenAmount sets error for non-numeric input`() = runTest {
        val vm = buildViewModel()
        advanceUntilIdle()
        vm.tokenAmountFieldState.setTextAndPlaceCursorAtEnd("abc")

        vm.validateTokenAmount()

        assertNotNull(vm.uiState.value.tokenAmountError)
    }

    @Test
    fun `validateTokenAmount clears error for positive amount`() = runTest {
        val vm = buildViewModel()
        advanceUntilIdle()
        vm.tokenAmountFieldState.setTextAndPlaceCursorAtEnd("1.5")

        vm.validateTokenAmount()

        assertNull(vm.uiState.value.tokenAmountError)
    }

    @Test
    fun `toggleAmountInputType reflects the requested mode`() = runTest {
        val vm = buildViewModel()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.usingTokenAmountInput) // default

        vm.toggleAmountInputType(usingTokenAmountInput = false)
        assertFalse(vm.uiState.value.usingTokenAmountInput)

        vm.toggleAmountInputType(usingTokenAmountInput = true)
        assertTrue(vm.uiState.value.usingTokenAmountInput)
    }

    @Test
    fun `expandSection updates the expanded section`() = runTest {
        val vm = buildViewModel()
        advanceUntilIdle()

        vm.expandSection(SendSections.Address)
        assertEquals(SendSections.Address, vm.uiState.value.expandedSection)

        vm.expandSection(SendSections.Amount)
        assertEquals(SendSections.Amount, vm.uiState.value.expandedSection)
    }

    @Test
    fun `leaving Asset marks the asset step complete, also after going back`() = runTest {
        val vm = buildViewModel()
        advanceUntilIdle()
        vm.uiState.value.isAssetStepComplete shouldBe false

        vm.expandSection(SendSections.Address)
        vm.expandSection(SendSections.Asset)

        vm.uiState.value.isAssetStepComplete shouldBe true
        vm.uiState.value.expandedSection shouldBe SendSections.Asset
    }

    @Test
    fun `plain-send Continue moves from Asset to Address without submitting`() = runTest {
        val vm = buildViewModel()
        advanceUntilIdle()
        val eth = assetRow(Coins.Ethereum.ETH)
        vm.uiState.value = vm.uiState.value.copy(selectedCoin = eth, assetRows = listOf(eth))

        vm.onClickContinue()

        vm.uiState.value.expandedSection shouldBe SendSections.Address
        vm.uiState.value.isLoading shouldBe false
    }

    @Test
    fun `plain-send Continue stays on Asset while a chip hides the selected coin`() = runTest {
        val vm = buildViewModel()
        advanceUntilIdle()
        val eth = assetRow(Coins.Ethereum.ETH)
        val btc = assetRow(Coins.Bitcoin.BTC)
        vm.uiState.value =
            vm.uiState.value.copy(
                selectedCoin = eth,
                assetChainFilter = Chain.Bitcoin,
                assetRows = listOf(btc),
            )

        vm.onClickContinue()

        vm.uiState.value.expandedSection shouldBe SendSections.Asset
    }

    @Test
    fun `plain-send Continue on Address stays there until the recipient is valid`() = runTest {
        val vm = buildViewModel()
        advanceUntilIdle()
        vm.expandSection(SendSections.Address)

        vm.onClickContinue()
        vm.uiState.value.expandedSection shouldBe SendSections.Address

        vm.uiState.value = vm.uiState.value.copy(isDstAddressValid = true)
        vm.onClickContinue()
        vm.uiState.value.expandedSection shouldBe SendSections.Amount
    }

    @Test
    fun `selectStep ignores a step whose earlier steps are not complete`() = runTest {
        val vm = buildViewModel()
        advanceUntilIdle()

        vm.selectStep(SendSections.Amount)
        vm.uiState.value.expandedSection shouldBe SendSections.Asset

        vm.selectStep(SendSections.Address)
        vm.uiState.value.expandedSection shouldBe SendSections.Asset

        vm.uiState.value = vm.uiState.value.copy(isAssetStepComplete = true)
        vm.selectStep(SendSections.Address)
        vm.uiState.value.expandedSection shouldBe SendSections.Address
    }

    @Test
    fun `picking a chain chip clears the asset search`() = runTest {
        val vm = buildViewModel()
        advanceUntilIdle()
        vm.assetSearchFieldState.setTextAndPlaceCursorAtEnd("usdc")

        vm.selectAssetChainFilter(Chain.Ethereum)

        vm.assetSearchFieldState.text.toString() shouldBe ""
        vm.uiState.value.assetChainFilter shouldBe Chain.Ethereum
    }

    @Test
    fun `picking an asset moves on to Address and completes the asset step`() = runTest {
        val vm = buildViewModel()
        advanceUntilIdle()

        vm.selectAsset(Coins.Ethereum.ETH)

        vm.uiState.value.expandedSection shouldBe SendSections.Address
        vm.uiState.value.isAssetStepComplete shouldBe true
    }

    @Test
    fun `Continue on a blank Address holds the focus request until the step applies it`() =
        runTest {
            val vm = buildViewModel()
            advanceUntilIdle()
            vm.expandSection(SendSections.Address)

            vm.onClickContinue()
            vm.uiState.value.pendingFocusField shouldBe SendFocusField.ADDRESS

            vm.onPendingFocusApplied()
            vm.uiState.value.pendingFocusField shouldBe null
        }

    @Test
    fun `a DeFi form still focuses through the focus channel`() = runTest {
        every { savedStateHandle.toRoute<Route.Send>() } returns
            Route.Send(vaultId = VAULT_ID, type = "STAKE_TON")
        val vm = buildViewModel()
        advanceUntilIdle()

        vm.onClickContinue()
        advanceUntilIdle()

        vm.focusFieldFlow.timeout(1.seconds).first() shouldBe SendFocusField.ADDRESS
        vm.uiState.value.pendingFocusField shouldBe null
    }

    @Test
    fun `toggling asset and fiat flips the shown unit and leaves both amounts as typed`() =
        runTest {
            val vm = buildViewModel()
            advanceUntilIdle()
            vm.tokenAmountFieldState.setTextAndPlaceCursorAtEnd("0.5")
            vm.fiatAmountFieldState.setTextAndPlaceCursorAtEnd("1000")

            vm.toggleAmountInputType(usingTokenAmountInput = false)
            vm.uiState.value.usingTokenAmountInput shouldBe false
            vm.toggleAmountInputType(usingTokenAmountInput = true)
            advanceUntilIdle()

            vm.uiState.value.usingTokenAmountInput shouldBe true
            vm.tokenAmountFieldState.text.toString() shouldBe "0.5"
            vm.fiatAmountFieldState.text.toString() shouldBe "1000"
        }

    @Test
    fun `dismissError clears any previously set errorText`() = runTest {
        val vm = buildViewModel()
        advanceUntilIdle()
        vm.uiState.value = vm.uiState.value.copy(errorText = UiText.DynamicString("boom"))

        vm.dismissError()

        assertNull(vm.uiState.value.errorText)
    }

    @Test
    fun `back delegates to the navigator`() = runTest {
        val vm = buildViewModel()
        advanceUntilIdle()

        vm.back()
        advanceUntilIdle()

        coVerify { navigator.back() }
    }

    @Test
    fun `chooseMaxTokenAmount marks F100 as the selected fraction`() = runTest {
        // With no vault loaded, calculatePercentageWithAccurateFee short-circuits to ZERO,
        // but the fraction selection state mutation is the observable behavior we want to lock in.
        val vm = buildViewModel()
        advanceUntilIdle()

        vm.chooseMaxTokenAmount()
        advanceUntilIdle()

        assertEquals(AmountFraction.F100, vm.uiState.value.selectedAmountFraction)
        assertFalse(vm.uiState.value.isAmountSelectionLoading)
    }

    @Test
    fun `choosePercentageAmount marks F25 as the selected fraction`() = runTest {
        val vm = buildViewModel()
        advanceUntilIdle()

        vm.choosePercentageAmount(AmountFraction.F25)
        advanceUntilIdle()

        assertEquals(AmountFraction.F25, vm.uiState.value.selectedAmountFraction)
        assertFalse(vm.uiState.value.isAmountSelectionLoading)
    }

    @Test
    fun `choosePercentageAmount marks F50 as the selected fraction`() = runTest {
        val vm = buildViewModel()
        advanceUntilIdle()

        vm.choosePercentageAmount(AmountFraction.F50)
        advanceUntilIdle()

        assertEquals(AmountFraction.F50, vm.uiState.value.selectedAmountFraction)
    }

    @Test
    fun `choosePercentageAmount marks F75 as the selected fraction`() = runTest {
        val vm = buildViewModel()
        advanceUntilIdle()

        vm.choosePercentageAmount(AmountFraction.F75)
        advanceUntilIdle()

        assertEquals(AmountFraction.F75, vm.uiState.value.selectedAmountFraction)
    }

    @Test
    fun `default amountFractionEntries cover 25 50 75 max`() = runTest {
        val vm = buildViewModel()
        advanceUntilIdle()

        val entries = vm.uiState.value.amountFractionEntries
        assertEquals(
            listOf(AmountFraction.F25, AmountFraction.F50, AmountFraction.F75, AmountFraction.F100),
            entries,
        )
    }

    private fun buildViewModel(): SendFormViewModel {
        val tokenBalanceMapper = mockk<AccountToTokenBalanceUiModelMapper>()
        coEvery { tokenBalanceMapper.invoke(any()) } returns
            TokenBalanceUiModel(
                model = mockk(relaxed = true),
                title = "",
                balance = "0",
                fiatValue = "0",
                isNativeToken = true,
                isLayer2 = false,
                tokenStandard = null,
                tokenLogo = "",
                chainLogo = 0,
            )
        return SendFormViewModel(
            savedStateHandle = savedStateHandle,
            navigator = navigator,
            accountToTokenBalanceUiModelMapper = tokenBalanceMapper,
            mapTokenValueToString = mockk(relaxed = true),
            requestQrScan = mockk(relaxed = true),
            accountsRepository = mockk(relaxed = true),
            appCurrencyRepository = appCurrencyRepository,
            chainAccountAddressRepository = mockk(relaxed = true),
            tokenPriceRepository = mockk(relaxed = true),
            blockChainSpecificRepository = mockk(relaxed = true),
            requestResultRepository = mockk(relaxed = true),
            addressParserRepository = mockk(relaxed = true),
            getAvailableTokenBalance = mockk(relaxed = true),
            gasFeeToEstimatedFee = mockk(relaxed = true),
            advanceGasUiRepository = mockk(relaxed = true),
            vaultRepository = mockk(relaxed = true),
            tokenRepository = mockk(relaxed = true),
            stakingDetailsRepository = mockk(relaxed = true),
            defaultStakingPositionService = mockk(relaxed = true),
            rujiStakingService = mockk(relaxed = true),
            feeServiceComposite = mockk(relaxed = true),
            chainValidationService = mockk(relaxed = true),
            requestAddressBookEntry = mockk(relaxed = true),
            enableToken = mockk(relaxed = true),
            getTronFrozenBalances = mockk(relaxed = true),
            defaultDispatcher = mainDispatcher,
            sendStrategyFactory = fakeSendStrategyFactory(),
        )
    }

    private fun assetRow(coin: Coin): TokenBalanceUiModel {
        val account = Account(token = coin, tokenValue = null, fiatValue = null, price = null)
        return TokenBalanceUiModel(
            model = SendSrc(Address(coin.chain, "addr", listOf(account)), account),
            title = coin.ticker,
            balance = null,
            fiatValue = null,
            isNativeToken = coin.isNativeToken,
            isLayer2 = false,
            tokenStandard = null,
            tokenLogo = "",
            chainLogo = 0,
        )
    }

    private companion object {
        const val VAULT_ID = "vault-1"
    }
}
