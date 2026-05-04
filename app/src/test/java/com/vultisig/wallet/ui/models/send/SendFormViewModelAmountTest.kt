@file:OptIn(ExperimentalCoroutinesApi::class, ExperimentalStdlibApi::class)

package com.vultisig.wallet.ui.models.send

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import com.vultisig.wallet.data.models.settings.AppCurrency
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.ui.models.mappers.AccountToTokenBalanceUiModelMapper
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.navigation.back
import com.vultisig.wallet.ui.utils.UiText
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
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
            transactionRepository = mockk(relaxed = true),
            blockChainSpecificRepository = mockk(relaxed = true),
            requestResultRepository = mockk(relaxed = true),
            addressParserRepository = mockk(relaxed = true),
            getAvailableTokenBalance = mockk(relaxed = true),
            gasFeeToEstimatedFee = mockk(relaxed = true),
            advanceGasUiRepository = mockk(relaxed = true),
            vaultRepository = mockk(relaxed = true),
            tokenRepository = mockk(relaxed = true),
            depositTransactionRepository = mockk(relaxed = true),
            stakingDetailsRepository = mockk(relaxed = true),
            feeServiceComposite = mockk(relaxed = true),
            chainValidationService = mockk(relaxed = true),
            requestAddressBookEntry = mockk(relaxed = true),
            getTronFrozenBalances = mockk(relaxed = true),
        )
    }

    private companion object {
        const val VAULT_ID = "vault-1"
    }
}
