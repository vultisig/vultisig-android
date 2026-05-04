@file:OptIn(
    ExperimentalCoroutinesApi::class,
    ExperimentalStdlibApi::class,
    kotlinx.coroutines.FlowPreview::class,
)

package com.vultisig.wallet.ui.models.send

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import com.vultisig.wallet.data.models.settings.AppCurrency
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.data.repositories.TransactionRepository
import com.vultisig.wallet.ui.models.mappers.AccountToTokenBalanceUiModelMapper
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

internal class SendFormViewModelSubmitEvmTest {

    private val scheduler = TestCoroutineScheduler()
    private val mainDispatcher = UnconfinedTestDispatcher(scheduler)

    private val savedStateHandle: SavedStateHandle = mockk(relaxed = true)
    private val appCurrencyRepository: AppCurrencyRepository = mockk(relaxed = true)
    private val navigator: Navigator<Destination> = mockk(relaxed = true)
    private val transactionRepository: TransactionRepository = mockk(relaxed = true)

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
    fun `send with blank address expands the Address section and emits ADDRESS focus`() = runTest {
        val vm = buildViewModel()
        advanceUntilIdle()
        // tokenAmountFieldState is blank too, but the address check runs first.

        vm.send()
        advanceUntilIdle()

        assertEquals(SendSections.Address, vm.uiState.value.expandedSection)
        val focused = vm.focusFieldFlow.timeout(1.seconds).first()
        assertEquals(SendFocusField.ADDRESS, focused)
    }

    @Test
    fun `send with non-blank address but blank amount expands Amount and emits AMOUNT focus`() =
        runTest {
            val vm = buildViewModel()
            advanceUntilIdle()
            vm.addressFieldState.setTextAndPlaceCursorAtEnd("0xabc")

            vm.send()
            advanceUntilIdle()

            assertEquals(SendSections.Amount, vm.uiState.value.expandedSection)
            val focused = vm.focusFieldFlow.timeout(1.seconds).first()
            assertEquals(SendFocusField.AMOUNT, focused)
        }

    @Test
    fun `send with no selected account surfaces an error and does not navigate`() = runTest {
        val vm = buildViewModel()
        advanceUntilIdle()
        vm.addressFieldState.setTextAndPlaceCursorAtEnd("0xabc")
        vm.tokenAmountFieldState.setTextAndPlaceCursorAtEnd("0.1")

        vm.send()
        advanceUntilIdle()

        assertNotNull(vm.uiState.value.errorText)
        assertFalse(vm.uiState.value.isLoading)
        coVerify(exactly = 0) { navigator.route(any(), any()) }
        coVerify(exactly = 0) { transactionRepository.addTransaction(any()) }
    }

    @Test
    fun `onClickContinue with no defiType routes to send (validation path)`() = runTest {
        // Indirectly verifies the dispatch table: with defiType = null and blank inputs,
        // onClickContinue must reach send() — observable via the same expandSection(Address)
        // behavior tested above.
        val vm = buildViewModel()
        advanceUntilIdle()

        vm.onClickContinue()
        advanceUntilIdle()

        assertEquals(SendSections.Address, vm.uiState.value.expandedSection)
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
            transactionRepository = transactionRepository,
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
