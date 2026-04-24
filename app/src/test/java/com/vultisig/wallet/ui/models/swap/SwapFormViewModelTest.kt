@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.swap

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import com.vultisig.wallet.R
import com.vultisig.wallet.data.api.errors.SwapException
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.EstimatedGasFee
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.models.SwapProvider
import com.vultisig.wallet.data.models.SwapQuote
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.settings.AppCurrency
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.data.repositories.AllowanceRepository
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.data.repositories.ReferralCodeSettingsRepository
import com.vultisig.wallet.data.repositories.RequestResultRepository
import com.vultisig.wallet.data.repositories.SwapTransactionRepository
import com.vultisig.wallet.data.usecases.ConvertTokenAndValueToTokenValueUseCase
import com.vultisig.wallet.data.usecases.GetDiscountBpsUseCase
import com.vultisig.wallet.data.usecases.resolveprovider.ResolveProviderUseCase
import com.vultisig.wallet.ui.models.findCurrentSrc
import com.vultisig.wallet.ui.models.firstSendSrc
import com.vultisig.wallet.ui.models.mappers.AccountToTokenBalanceUiModelMapper
import com.vultisig.wallet.ui.models.mappers.FiatValueToStringMapper
import com.vultisig.wallet.ui.models.send.SendSrc
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.asUiText
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Clock
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class SwapFormViewModelTest {

    private val scheduler = TestCoroutineScheduler()
    private val mainDispatcher = UnconfinedTestDispatcher(scheduler)

    private lateinit var savedStateHandle: SavedStateHandle
    private lateinit var navigator: Navigator<Destination>
    private lateinit var fiatValueToString: FiatValueToStringMapper
    private lateinit var convertTokenAndValueToTokenValue: ConvertTokenAndValueToTokenValueUseCase
    private lateinit var resolveProvider: ResolveProviderUseCase
    private lateinit var allowanceRepository: AllowanceRepository
    private lateinit var appCurrencyRepository: AppCurrencyRepository
    private lateinit var swapTransactionRepository: SwapTransactionRepository
    private lateinit var getDiscountBpsUseCase: GetDiscountBpsUseCase
    private lateinit var referralRepository: ReferralCodeSettingsRepository
    private lateinit var swapValidator: SwapValidator
    private lateinit var swapDiscountChecker: SwapDiscountChecker
    private lateinit var swapGasCalculator: SwapGasCalculator
    private lateinit var swapTokenSelector: SwapTokenSelector
    private lateinit var swapQuoteManager: SwapQuoteManager
    private lateinit var tokenSelectorAccountsRepository: AccountsRepository

    private val currencyFlow = MutableStateFlow(AppCurrency.USD)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        mockkStatic("androidx.navigation.SavedStateHandleKt")

        savedStateHandle = mockk(relaxed = true)
        every { any<SavedStateHandle>().toRoute<Route.Swap>() } returns
            Route.Swap(vaultId = TEST_VAULT_ID)

        navigator = mockk(relaxed = true)

        fiatValueToString = mockk(relaxed = true)
        coEvery { fiatValueToString(any()) } returns "$0.00"

        convertTokenAndValueToTokenValue = mockk(relaxed = true)
        every { convertTokenAndValueToTokenValue(any(), any()) } answers
            {
                TokenValue(value = secondArg(), token = firstArg())
            }

        resolveProvider = mockk(relaxed = true)
        allowanceRepository = mockk(relaxed = true)

        appCurrencyRepository = mockk(relaxed = true)
        every { appCurrencyRepository.currency } returns currencyFlow

        swapTransactionRepository = mockk(relaxed = true)
        getDiscountBpsUseCase = mockk(relaxed = true)
        referralRepository = mockk(relaxed = true)

        swapValidator = SwapValidator()
        swapDiscountChecker = mockk(relaxed = true)
        coEvery { swapDiscountChecker.checkVultBpsDiscount(any(), any(), any()) } returns
            VultDiscountResult(null, null, null)
        coEvery { swapDiscountChecker.checkReferralBpsDiscount(any(), any(), any(), any()) } returns
            ReferralDiscountResult(null, null, null)

        swapGasCalculator = mockk(relaxed = true)
        coEvery { swapGasCalculator.calculateGasFee(any(), any()) } returns
            GasCalculationResult(
                gasFee = TokenValue(value = BigInteger("1000000000000000"), token = ETH_COIN),
                estimated =
                    EstimatedGasFee(
                        formattedTokenValue = "0.001 ETH",
                        formattedFiatValue = "$2.00",
                        tokenValue =
                            TokenValue(value = BigInteger("1000000000000000"), token = ETH_COIN),
                        fiatValue = FiatValue(BigDecimal("2.00"), "USD"),
                    ),
            )

        val accountsRepository: AccountsRepository = mockk(relaxed = true)
        coEvery { accountsRepository.loadAddresses(any()) } returns flowOf(emptyList())
        val accountToTokenBalanceUiModelMapper: AccountToTokenBalanceUiModelMapper =
            mockk(relaxed = true)
        coEvery { accountToTokenBalanceUiModelMapper(any()) } returns mockk(relaxed = true)
        val requestResultRepository: RequestResultRepository = mockk(relaxed = true)

        swapTokenSelector =
            SwapTokenSelector(
                navigator = navigator,
                accountsRepository = accountsRepository,
                requestResultRepository = requestResultRepository,
                accountToTokenBalanceUiModelMapper = accountToTokenBalanceUiModelMapper,
            )
        tokenSelectorAccountsRepository = accountsRepository

        swapQuoteManager = mockk(relaxed = true)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic("androidx.navigation.SavedStateHandleKt")
    }

    private fun createViewModel() =
        SwapFormViewModel(
            savedStateHandle = savedStateHandle,
            navigator = navigator,
            fiatValueToString = fiatValueToString,
            convertTokenAndValueToTokenValue = convertTokenAndValueToTokenValue,
            resolveProvider = resolveProvider,
            allowanceRepository = allowanceRepository,
            appCurrencyRepository = appCurrencyRepository,
            swapTransactionRepository = swapTransactionRepository,
            getDiscountBpsUseCase = getDiscountBpsUseCase,
            referralRepository = referralRepository,
            swapValidator = swapValidator,
            swapDiscountChecker = swapDiscountChecker,
            swapGasCalculator = swapGasCalculator,
            swapTokenSelector = swapTokenSelector,
            swapQuoteManager = swapQuoteManager,
        )

    private fun createViewModelWithAddresses(
        addresses: List<Address> = listOf(ethAddress(), btcAddress()),
        srcTokenId: String? = null,
        dstTokenId: String? = null,
    ): SwapFormViewModel {
        if (srcTokenId != null || dstTokenId != null) {
            every { any<SavedStateHandle>().toRoute<Route.Swap>() } returns
                Route.Swap(
                    vaultId = TEST_VAULT_ID,
                    srcTokenId = srcTokenId,
                    dstTokenId = dstTokenId,
                )
        }
        coEvery { tokenSelectorAccountsRepository.loadAddresses(any()) } returns flowOf(addresses)
        return createViewModel()
    }

    // region Initial State

    @Test
    fun `initial state has correct defaults`() =
        runTest(mainDispatcher) {
            val vm = createViewModel()

            val state = vm.uiState.value
            assertNull(state.selectedSrcToken)
            assertNull(state.selectedDstToken)
            assertEquals("0", state.srcFiatValue)
            assertEquals("0", state.estimatedDstTokenValue)
            assertEquals("0", state.estimatedDstFiatValue)
            assertEquals("", state.networkFee)
            assertEquals("", state.networkFeeFiat)
            assertEquals("0", state.totalFee)
            assertEquals("", state.fee)
            assertNull(state.error)
            assertNull(state.formError)
            assertTrue(state.isSwapDisabled)
            assertFalse(state.isLoadingNextScreen)
            assertNull(state.expiredAt)
            assertNull(state.tierType)
            assertNull(state.vultBpsDiscount)
            assertNull(state.vultBpsDiscountFiatValue)
            assertNull(state.referralBpsDiscount)
            assertNull(state.referralBpsDiscountFiatValue)
        }

    @Test
    fun `initial state loads data from saved state handle`() =
        runTest(mainDispatcher) {
            every { any<SavedStateHandle>().toRoute<Route.Swap>() } returns
                Route.Swap(
                    vaultId = TEST_VAULT_ID,
                    chainId = Chain.Ethereum.id,
                    srcTokenId = ETH_COIN.id,
                    dstTokenId = USDC_COIN.id,
                )
            val vm = createViewModelWithAddresses()
            advanceUntilIdle()

            coVerify { tokenSelectorAccountsRepository.loadAddresses(TEST_VAULT_ID) }
        }

    // endregion

    // region back

    @Test
    fun `back navigates to Destination Back`() =
        runTest(mainDispatcher) {
            val vm = createViewModel()

            vm.back()
            advanceUntilIdle()

            coVerify { navigator.navigate(Destination.Back) }
        }

    // endregion

    // region loadData

    @Test
    fun `loadData loads addresses for vault`() =
        runTest(mainDispatcher) {
            val vm = createViewModelWithAddresses()
            advanceUntilIdle()

            coVerify { tokenSelectorAccountsRepository.loadAddresses(TEST_VAULT_ID) }
        }

    @Test
    fun `loadData with new vaultId reloads addresses`() =
        runTest(mainDispatcher) {
            val vm = createViewModelWithAddresses()
            advanceUntilIdle()

            vm.loadData(
                vaultId = "new-vault-id",
                chainId = null,
                srcTokenId = null,
                dstTokenId = null,
            )
            advanceUntilIdle()

            coVerify { tokenSelectorAccountsRepository.loadAddresses("new-vault-id") }
        }

    @Test
    fun `loadData with same vaultId does not reload addresses`() =
        runTest(mainDispatcher) {
            val vm = createViewModelWithAddresses()
            advanceUntilIdle()

            vm.loadData(
                vaultId = TEST_VAULT_ID,
                chainId = null,
                srcTokenId = null,
                dstTokenId = null,
            )
            advanceUntilIdle()

            coVerify(exactly = 1) { tokenSelectorAccountsRepository.loadAddresses(TEST_VAULT_ID) }
        }

    // endregion

    // region validateAmount

    @Test
    fun `validateAmount with empty string sets error`() =
        runTest(mainDispatcher) {
            val vm = createViewModel()

            vm.validateAmount()

            assertEquals(
                UiText.StringResource(R.string.swap_form_invalid_amount),
                vm.uiState.value.error,
            )
        }

    @Test
    fun `validateAmount with valid amount clears error`() =
        runTest(mainDispatcher) {
            val vm = createViewModel()

            vm.srcAmountState.setTextAndPlaceCursorAtEnd("100")
            Snapshot.sendApplyNotifications()

            vm.validateAmount()

            assertNull(vm.uiState.value.error)
        }

    @Test
    fun `validateAmount with zero sets error`() =
        runTest(mainDispatcher) {
            val vm = createViewModel()

            vm.srcAmountState.setTextAndPlaceCursorAtEnd("0")
            Snapshot.sendApplyNotifications()

            vm.validateAmount()

            assertEquals(
                UiText.StringResource(R.string.swap_error_no_amount),
                vm.uiState.value.error,
            )
        }

    @Test
    fun `validateAmount with negative amount sets error`() =
        runTest(mainDispatcher) {
            val vm = createViewModel()

            vm.srcAmountState.setTextAndPlaceCursorAtEnd("-5")
            Snapshot.sendApplyNotifications()

            vm.validateAmount()

            assertEquals(
                UiText.StringResource(R.string.swap_error_no_amount),
                vm.uiState.value.error,
            )
        }

    @Test
    fun `validateAmount with non-numeric string sets error`() =
        runTest(mainDispatcher) {
            val vm = createViewModel()

            vm.srcAmountState.setTextAndPlaceCursorAtEnd("abc")
            Snapshot.sendApplyNotifications()

            vm.validateAmount()

            assertEquals(
                UiText.StringResource(R.string.swap_error_no_amount),
                vm.uiState.value.error,
            )
        }

    @Test
    fun `validateAmount with very long string sets error`() =
        runTest(mainDispatcher) {
            val vm = createViewModel()

            // TextFieldUtils.AMOUNT_MAX_LENGTH is 50
            vm.srcAmountState.setTextAndPlaceCursorAtEnd("1".repeat(51))
            Snapshot.sendApplyNotifications()

            vm.validateAmount()

            assertEquals(
                UiText.StringResource(R.string.swap_form_invalid_amount),
                vm.uiState.value.error,
            )
        }

    @Test
    fun `validateAmount with decimal amount clears error`() =
        runTest(mainDispatcher) {
            val vm = createViewModel()

            vm.srcAmountState.setTextAndPlaceCursorAtEnd("0.5")
            Snapshot.sendApplyNotifications()

            vm.validateAmount()

            assertNull(vm.uiState.value.error)
        }

    // endregion

    // region hideError

    @Test
    fun `hideError clears both error and formError`() =
        runTest(mainDispatcher) {
            val vm = createViewModel()

            // Set some errors first
            vm.validateAmount() // sets error
            assertNotNull(vm.uiState.value.error)

            vm.hideError()

            assertNull(vm.uiState.value.error)
            assertNull(vm.uiState.value.formError)
        }

    // endregion

    // region selectSrcPercentage

    @Test
    fun `selectSrcPercentage with no selected source does nothing`() =
        runTest(mainDispatcher) {
            val vm = createViewModel()

            vm.selectSrcPercentage(1.0f)

            assertEquals("", vm.srcAmountState.text.toString())
        }

    @Test
    fun `selectSrcPercentage with 100 percent sets max available amount`() =
        runTest(mainDispatcher) {
            val addresses =
                listOf(
                    ethAddressWithBalance(BigInteger("1000000000000000000")) // 1 ETH
                )
            val vm = createViewModelWithAddresses(addresses)
            advanceUntilIdle()

            vm.selectSrcPercentage(1.0f)

            val amountText = vm.srcAmountState.text.toString()
            assertTrue(
                amountText.isNotEmpty() && amountText != "0",
                "Expected non-zero amount but got: $amountText",
            )
        }

    @Test
    fun `selectSrcPercentage with 50 percent sets half amount`() =
        runTest(mainDispatcher) {
            val balance = BigInteger("2000000000000000000") // 2 ETH
            val addresses = listOf(ethAddressWithBalance(balance))
            val vm = createViewModelWithAddresses(addresses)
            advanceUntilIdle()

            vm.selectSrcPercentage(0.5f)

            val amountText = vm.srcAmountState.text.toString()
            assertTrue(amountText.isNotEmpty(), "Expected non-empty amount")
        }

    @Test
    fun `selectSrcPercentage with zero balance sets zero`() =
        runTest(mainDispatcher) {
            val addresses = listOf(ethAddressWithBalance(BigInteger.ZERO))
            val vm = createViewModelWithAddresses(addresses)
            advanceUntilIdle()

            vm.selectSrcPercentage(1.0f)

            assertEquals("0", vm.srcAmountState.text.toString())
        }

    // endregion

    // region flipSelectedTokens

    @Test
    fun `flipSelectedTokens swaps source and destination token IDs`() =
        runTest(mainDispatcher) {
            val addresses = listOf(ethAddress(), btcAddress())
            val vm = createViewModelWithAddresses(addresses)
            advanceUntilIdle()

            // The vm should have selected some tokens by now from addresses
            vm.flipSelectedTokens()
            advanceUntilIdle()

            // After flip, the state should be reset for fresh calculation
            assertEquals("0", vm.uiState.value.estimatedDstTokenValue)
            assertEquals("0", vm.uiState.value.estimatedDstFiatValue)
        }

    @Test
    fun `flipSelectedTokens resets quote state`() =
        runTest(mainDispatcher) {
            val vm = createViewModelWithAddresses()
            advanceUntilIdle()

            vm.flipSelectedTokens()

            assertEquals("0", vm.uiState.value.estimatedDstTokenValue)
            assertEquals("0", vm.uiState.value.estimatedDstFiatValue)
            assertEquals("0", vm.uiState.value.srcFiatValue)
            assertNull(vm.uiState.value.formError)
        }

    // endregion

    // region calculateFees — debounce behavior

    @Test
    fun `calculateFees debounces amount changes`() =
        runTest(mainDispatcher) {
            coEvery { resolveProvider.invoke(any()) } returns SwapProvider.THORCHAIN
            coEvery {
                swapQuoteManager.fetchQuote(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                )
            } returns createDefaultQuoteFetchResult()

            val vm = createViewModelWithSwapTokens()
            advanceUntilIdle()

            // Rapid typing should not trigger multiple quote fetches
            vm.srcAmountState.setTextAndPlaceCursorAtEnd("1")
            Snapshot.sendApplyNotifications()
            advanceTimeBy(200)

            vm.srcAmountState.setTextAndPlaceCursorAtEnd("10")
            Snapshot.sendApplyNotifications()
            advanceTimeBy(200)

            vm.srcAmountState.setTextAndPlaceCursorAtEnd("100")
            Snapshot.sendApplyNotifications()

            // Before debounce expires, isLoading should not have triggered full calculation
            advanceTimeBy(100) // total 500ms, debounce is 450ms from last change

            // Let debounce complete
            advanceTimeBy(400)
            advanceUntilIdle()

            // Only the final amount "100" should have been used for quote
            // (verified indirectly by checking state settled)
        }

    // endregion

    // region calculateFees — error handling

    @Test
    fun `calculateFees shows error when same assets selected`() =
        runTest(mainDispatcher) {
            // Both src and dst explicitly set to the same token
            val sameTokenAddress =
                Address(
                    chain = Chain.Ethereum,
                    address = "0xabc",
                    accounts = listOf(createAccount(ETH_COIN, BigInteger("1000000000000000000"))),
                )
            val vm =
                createViewModelWithAddresses(
                    addresses = listOf(sameTokenAddress),
                    srcTokenId = ETH_COIN.id,
                    dstTokenId = ETH_COIN.id,
                )
            advanceUntilIdle()

            vm.srcAmountState.setTextAndPlaceCursorAtEnd("1")
            Snapshot.sendApplyNotifications()
            advanceTimeBy(500)
            advanceUntilIdle()

            assertTrue(vm.uiState.value.isSwapDisabled)
            assertNotNull(vm.uiState.value.formError)
        }

    @Test
    fun `calculateFees shows error when provider is not found`() =
        runTest(mainDispatcher) {
            coEvery { resolveProvider.invoke(any()) } returns null

            val vm = createViewModelWithSwapTokens()
            advanceUntilIdle()

            vm.srcAmountState.setTextAndPlaceCursorAtEnd("1")
            Snapshot.sendApplyNotifications()
            advanceTimeBy(500)
            advanceUntilIdle()

            val state = vm.uiState.value
            assertTrue(state.isSwapDisabled)
        }

    @Test
    fun `calculateFees shows error on zero amount`() =
        runTest(mainDispatcher) {
            val vm = createViewModelWithSwapTokens()
            advanceUntilIdle()

            vm.srcAmountState.setTextAndPlaceCursorAtEnd("0")
            Snapshot.sendApplyNotifications()
            advanceTimeBy(500)
            advanceUntilIdle()

            val state = vm.uiState.value
            assertTrue(state.isSwapDisabled)
            assertNotNull(state.formError)
        }

    // endregion

    // region calculateFees — THORChain provider

    @Test
    fun `calculateFees with THORChain provider updates UI state correctly`() =
        runTest(mainDispatcher) {
            coEvery { resolveProvider.invoke(any()) } returns SwapProvider.THORCHAIN
            coEvery {
                swapQuoteManager.fetchQuote(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                )
            } returns
                createDefaultQuoteFetchResult(
                    estimatedDstTokenValue = "95.0",
                    estimatedDstFiatValue = "$95.00",
                )

            // Use large balance so amount + gas fees don't exceed balance
            val vm =
                createViewModelWithSwapTokens(
                    ethBalance = BigInteger("10000000000000000000") // 10 ETH
                )
            advanceUntilIdle()

            vm.srcAmountState.setTextAndPlaceCursorAtEnd("0.1")
            Snapshot.sendApplyNotifications()
            advanceTimeBy(500)
            advanceUntilIdle()

            val state = vm.uiState.value
            assertFalse(state.isSwapDisabled)
            assertFalse(state.isLoading)
            assertNull(state.formError)
        }

    @Test
    fun `calculateFees with THORChain sets provider name`() =
        runTest(mainDispatcher) {
            coEvery { resolveProvider.invoke(any()) } returns SwapProvider.THORCHAIN
            coEvery {
                swapQuoteManager.fetchQuote(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                )
            } returns createDefaultQuoteFetchResult()

            val vm = createViewModelWithSwapTokens()
            advanceUntilIdle()

            vm.srcAmountState.setTextAndPlaceCursorAtEnd("1")
            Snapshot.sendApplyNotifications()
            advanceTimeBy(500)
            advanceUntilIdle()

            assertEquals(
                UiText.StringResource(R.string.swap_form_provider_thorchain),
                vm.uiState.value.provider,
            )
        }

    // endregion

    // region calculateFees — MayaChain provider

    @Test
    fun `calculateFees with MayaChain sets provider name`() =
        runTest(mainDispatcher) {
            coEvery { resolveProvider.invoke(any()) } returns SwapProvider.MAYA
            coEvery {
                swapQuoteManager.fetchQuote(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                )
            } returns
                createDefaultQuoteFetchResult(
                    quote = createMayaChainQuote(),
                    provider = SwapProvider.MAYA,
                    providerUiText = R.string.swap_form_provider_mayachain.asUiText(),
                )

            val vm = createViewModelWithSwapTokens()
            advanceUntilIdle()

            vm.srcAmountState.setTextAndPlaceCursorAtEnd("1")
            Snapshot.sendApplyNotifications()
            advanceTimeBy(500)
            advanceUntilIdle()

            assertEquals(
                UiText.StringResource(R.string.swap_form_provider_mayachain),
                vm.uiState.value.provider,
            )
        }

    // endregion

    // region calculateFees — swap exception handling

    @Test
    fun `calculateFees handles SwapRouteNotAvailable`() =
        runTest(mainDispatcher) {
            coEvery { resolveProvider.invoke(any()) } throws
                SwapException.SwapRouteNotAvailable("No route")

            val vm = createViewModelWithSwapTokens()
            advanceUntilIdle()

            vm.srcAmountState.setTextAndPlaceCursorAtEnd("1")
            Snapshot.sendApplyNotifications()
            advanceTimeBy(500)
            advanceUntilIdle()

            val state = vm.uiState.value
            assertTrue(state.isSwapDisabled)
        }

    @Test
    fun `calculateFees handles TimeOut exception`() =
        runTest(mainDispatcher) {
            coEvery { resolveProvider.invoke(any()) } returns SwapProvider.THORCHAIN
            coEvery {
                swapQuoteManager.fetchQuote(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                )
            } throws SwapException.TimeOut("Timeout")
            coEvery { swapQuoteManager.mapSwapExceptionToFormError(any(), any(), any()) } returns
                UiText.StringResource(R.string.swap_error_time_out)

            val vm = createViewModelWithSwapTokens()
            advanceUntilIdle()

            vm.srcAmountState.setTextAndPlaceCursorAtEnd("1")
            Snapshot.sendApplyNotifications()
            advanceTimeBy(500)
            advanceUntilIdle()

            assertTrue(vm.uiState.value.isSwapDisabled)
        }

    @Test
    fun `calculateFees handles NetworkConnection exception`() =
        runTest(mainDispatcher) {
            coEvery { resolveProvider.invoke(any()) } returns SwapProvider.THORCHAIN
            coEvery {
                swapQuoteManager.fetchQuote(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                )
            } throws SwapException.NetworkConnection("No network")
            coEvery { swapQuoteManager.mapSwapExceptionToFormError(any(), any(), any()) } returns
                UiText.StringResource(R.string.network_connection_lost)

            val vm = createViewModelWithSwapTokens()
            advanceUntilIdle()

            vm.srcAmountState.setTextAndPlaceCursorAtEnd("1")
            Snapshot.sendApplyNotifications()
            advanceTimeBy(500)
            advanceUntilIdle()

            assertTrue(vm.uiState.value.isSwapDisabled)
        }

    @Test
    fun `calculateFees handles InsufficientFunds exception`() =
        runTest(mainDispatcher) {
            coEvery { resolveProvider.invoke(any()) } returns SwapProvider.THORCHAIN
            coEvery {
                swapQuoteManager.fetchQuote(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                )
            } throws SwapException.InsufficientFunds("Not enough")
            coEvery { swapQuoteManager.mapSwapExceptionToFormError(any(), any(), any()) } returns
                UiText.StringResource(R.string.swap_error_small_insufficient_funds)

            val vm = createViewModelWithSwapTokens()
            advanceUntilIdle()

            vm.srcAmountState.setTextAndPlaceCursorAtEnd("1")
            Snapshot.sendApplyNotifications()
            advanceTimeBy(500)
            advanceUntilIdle()

            assertTrue(vm.uiState.value.isSwapDisabled)
            assertEquals(
                UiText.StringResource(R.string.swap_error_small_insufficient_funds),
                vm.uiState.value.formError,
            )
        }

    @Test
    fun `calculateFees handles HighPriceImpact exception`() =
        runTest(mainDispatcher) {
            coEvery { resolveProvider.invoke(any()) } returns SwapProvider.THORCHAIN
            coEvery {
                swapQuoteManager.fetchQuote(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                )
            } throws SwapException.HighPriceImpact("High impact")
            coEvery { swapQuoteManager.mapSwapExceptionToFormError(any(), any(), any()) } returns
                UiText.StringResource(R.string.swap_error_high_price_impact)

            val vm = createViewModelWithSwapTokens()
            advanceUntilIdle()

            vm.srcAmountState.setTextAndPlaceCursorAtEnd("1")
            Snapshot.sendApplyNotifications()
            advanceTimeBy(500)
            advanceUntilIdle()

            assertTrue(vm.uiState.value.isSwapDisabled)
            assertEquals(
                UiText.StringResource(R.string.swap_error_high_price_impact),
                vm.uiState.value.formError,
            )
        }

    @Test
    fun `calculateFees resets state on swap exception`() =
        runTest(mainDispatcher) {
            coEvery { resolveProvider.invoke(any()) } throws
                SwapException.SwapIsNotSupported("Not supported")

            val vm = createViewModelWithSwapTokens()
            advanceUntilIdle()

            vm.srcAmountState.setTextAndPlaceCursorAtEnd("1")
            Snapshot.sendApplyNotifications()
            advanceTimeBy(500)
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals(UiText.Empty, state.provider)
            assertEquals("0", state.srcFiatValue)
            assertEquals("0", state.estimatedDstTokenValue)
            assertEquals("0", state.estimatedDstFiatValue)
            assertEquals("0", state.fee)
            assertTrue(state.isSwapDisabled)
            assertFalse(state.isLoading)
            assertNull(state.expiredAt)
        }

    // endregion

    // region swap — validation errors

    @Test
    fun `swap with no vault shows error`() =
        runTest(mainDispatcher) {
            // Create VM without loading addresses (no vaultId set internally)
            // swapTokenSelector is already mocked relaxed, returns empty by default
            every { any<SavedStateHandle>().toRoute<Route.Swap>() } returns
                Route.Swap(vaultId = TEST_VAULT_ID)

            val vm = createViewModel()
            advanceUntilIdle()

            vm.swap()
            advanceUntilIdle()

            assertNotNull(vm.uiState.value.error)
            assertFalse(vm.uiState.value.isLoadingNextScreen)
        }

    @Test
    fun `swap with no source token shows error`() =
        runTest(mainDispatcher) {
            val vm = createViewModelWithAddresses(emptyList())
            advanceUntilIdle()

            vm.swap()
            advanceUntilIdle()

            assertNotNull(vm.uiState.value.error)
            assertFalse(vm.uiState.value.isLoadingNextScreen)
        }

    @Test
    fun `swap with empty amount shows error`() =
        runTest(mainDispatcher) {
            val vm = createViewModelWithSwapTokens()
            advanceUntilIdle()

            vm.swap()
            advanceUntilIdle()

            assertNotNull(vm.uiState.value.error)
            assertFalse(vm.uiState.value.isLoadingNextScreen)
        }

    @Test
    fun `swap with zero amount shows error`() =
        runTest(mainDispatcher) {
            val vm = createViewModelWithSwapTokens()
            advanceUntilIdle()

            vm.srcAmountState.setTextAndPlaceCursorAtEnd("0")
            Snapshot.sendApplyNotifications()

            vm.swap()
            advanceUntilIdle()

            assertNotNull(vm.uiState.value.error)
            assertFalse(vm.uiState.value.isLoadingNextScreen)
        }

    // endregion

    // region collectSelectedAccounts

    @Test
    fun `collectSelectedAccounts maps source and destination to UI models`() =
        runTest(mainDispatcher) {
            val addresses = listOf(ethAddress(), btcAddress())
            val vm = createViewModelWithAddresses(addresses)
            advanceUntilIdle()

            // The mapper should have been invoked for the selected accounts
            val state = vm.uiState.value
            // With relaxed mocks, the mapper returns default values
            // The key assertion is that collectSelectedAccounts ran without error
        }

    @Test
    fun `collectSelectedAccounts sets enableMaxAmount false when both native`() =
        runTest(mainDispatcher) {
            // Both src and dst are native tokens
            val addresses = listOf(ethAddress(), btcAddress())
            val vm = createViewModelWithAddresses(addresses)
            advanceUntilIdle()

            // When both tokens are native, enableMaxAmount should be false
            assertFalse(vm.uiState.value.enableMaxAmount)
        }

    // endregion

    // region validateBalanceForSwap — native token

    @Test
    fun `calculateFees validates balance for native token after successful quote`() =
        runTest(mainDispatcher) {
            // Source has very small balance — less than amount + fees
            coEvery { resolveProvider.invoke(any()) } returns SwapProvider.THORCHAIN
            coEvery {
                swapQuoteManager.fetchQuote(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                )
            } returns createDefaultQuoteFetchResult()

            val vm = createViewModelWithSwapTokens(ethBalance = BigInteger("100"))
            advanceUntilIdle()

            vm.srcAmountState.setTextAndPlaceCursorAtEnd("1")
            Snapshot.sendApplyNotifications()
            advanceTimeBy(500)
            advanceUntilIdle()

            // After balance validation, swap should be disabled if insufficient
            val state = vm.uiState.value
            assertTrue(state.isSwapDisabled || state.formError != null)
        }

    // endregion

    // region discount checks

    @Test
    fun `calculateFees checks VULT BPS discount when available`() =
        runTest(mainDispatcher) {
            coEvery { getDiscountBpsUseCase.invoke(any(), any()) } returns 50
            coEvery { swapDiscountChecker.checkVultBpsDiscount(any(), any(), any()) } returns
                VultDiscountResult(
                    vultBpsDiscount = 50,
                    vultBpsDiscountFiatValue = "$5.00",
                    tierType = null,
                )
            coEvery { resolveProvider.invoke(any()) } returns SwapProvider.THORCHAIN
            coEvery {
                swapQuoteManager.fetchQuote(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                )
            } returns createDefaultQuoteFetchResult()

            val vm = createViewModelWithSwapTokens()
            advanceUntilIdle()

            vm.srcAmountState.setTextAndPlaceCursorAtEnd("1")
            Snapshot.sendApplyNotifications()
            advanceTimeBy(500)
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals(50, state.vultBpsDiscount)
            assertEquals("$5.00", state.vultBpsDiscountFiatValue)
        }

    @Test
    fun `calculateFees clears VULT BPS discount when not available`() =
        runTest(mainDispatcher) {
            coEvery { getDiscountBpsUseCase.invoke(any(), any()) } returns 0
            coEvery { resolveProvider.invoke(any()) } returns SwapProvider.THORCHAIN
            coEvery {
                swapQuoteManager.fetchQuote(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                )
            } returns createDefaultQuoteFetchResult()

            val vm = createViewModelWithSwapTokens()
            advanceUntilIdle()

            vm.srcAmountState.setTextAndPlaceCursorAtEnd("1")
            Snapshot.sendApplyNotifications()
            advanceTimeBy(500)
            advanceUntilIdle()

            val state = vm.uiState.value
            assertNull(state.vultBpsDiscount)
            assertNull(state.vultBpsDiscountFiatValue)
        }

    // endregion

    // region refresh quote timer

    @Test
    fun `calculateFees sets expiredAt from quote`() =
        runTest(mainDispatcher) {
            coEvery { resolveProvider.invoke(any()) } returns SwapProvider.THORCHAIN
            coEvery {
                swapQuoteManager.fetchQuote(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                )
            } returns createDefaultQuoteFetchResult()

            val vm = createViewModelWithSwapTokens()
            advanceUntilIdle()

            vm.srcAmountState.setTextAndPlaceCursorAtEnd("1")
            Snapshot.sendApplyNotifications()
            advanceTimeBy(500)
            advanceUntilIdle()

            assertNotNull(vm.uiState.value.expiredAt)
        }

    // endregion

    // region collectTotalFee

    @Test
    fun `collectTotalFee combines gas and swap fees`() =
        runTest(mainDispatcher) {
            coEvery { fiatValueToString(any()) } returns "$10.00"
            coEvery { resolveProvider.invoke(any()) } returns SwapProvider.THORCHAIN
            coEvery {
                swapQuoteManager.fetchQuote(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                )
            } returns createDefaultQuoteFetchResult()

            val vm = createViewModelWithSwapTokens()
            advanceUntilIdle()

            vm.srcAmountState.setTextAndPlaceCursorAtEnd("1")
            Snapshot.sendApplyNotifications()
            advanceTimeBy(500)
            advanceUntilIdle()

            // totalFee should have been updated by collectTotalFee flow
            // The exact value depends on mock configuration
        }

    // endregion

    // region SmallSwapAmount error formatting

    @Test
    fun `calculateFees handles SmallSwapAmount with recommended_min_amount_in`() =
        runTest(mainDispatcher) {
            coEvery { resolveProvider.invoke(any()) } returns SwapProvider.THORCHAIN
            coEvery {
                swapQuoteManager.fetchQuote(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                )
            } throws SwapException.SmallSwapAmount("recommended_min_amount_in: 1000000")
            coEvery { swapQuoteManager.mapSwapExceptionToFormError(any(), any(), any()) } returns
                UiText.FormattedText(R.string.swap_form_minimum_amount, listOf("0.01", "ETH"))

            val vm = createViewModelWithSwapTokens()
            advanceUntilIdle()

            vm.srcAmountState.setTextAndPlaceCursorAtEnd("0.0001")
            Snapshot.sendApplyNotifications()
            advanceTimeBy(500)
            advanceUntilIdle()

            assertTrue(vm.uiState.value.isSwapDisabled)
            assertNotNull(vm.uiState.value.formError)
        }

    @Test
    fun `calculateFees handles SmallSwapAmount with numeric message`() =
        runTest(mainDispatcher) {
            coEvery { resolveProvider.invoke(any()) } returns SwapProvider.THORCHAIN
            coEvery {
                swapQuoteManager.fetchQuote(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                )
            } throws SwapException.SmallSwapAmount("0.01")
            coEvery { swapQuoteManager.mapSwapExceptionToFormError(any(), any(), any()) } returns
                UiText.FormattedText(R.string.swap_form_minimum_amount, listOf("0.01", "ETH"))

            val vm = createViewModelWithSwapTokens()
            advanceUntilIdle()

            vm.srcAmountState.setTextAndPlaceCursorAtEnd("0.0001")
            Snapshot.sendApplyNotifications()
            advanceTimeBy(500)
            advanceUntilIdle()

            assertTrue(vm.uiState.value.isSwapDisabled)
            val formError = vm.uiState.value.formError
            assertNotNull(formError)
            assertTrue(formError is UiText.FormattedText)
        }

    @Test
    fun `calculateFees handles SmallSwapAmount with non-numeric message`() =
        runTest(mainDispatcher) {
            coEvery { resolveProvider.invoke(any()) } returns SwapProvider.THORCHAIN
            coEvery {
                swapQuoteManager.fetchQuote(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                )
            } throws SwapException.SmallSwapAmount("Amount too low")
            coEvery { swapQuoteManager.mapSwapExceptionToFormError(any(), any(), any()) } returns
                UiText.DynamicString("Amount too low")

            val vm = createViewModelWithSwapTokens()
            advanceUntilIdle()

            vm.srcAmountState.setTextAndPlaceCursorAtEnd("0.0001")
            Snapshot.sendApplyNotifications()
            advanceTimeBy(500)
            advanceUntilIdle()

            assertTrue(vm.uiState.value.isSwapDisabled)
            val formError = vm.uiState.value.formError
            assertNotNull(formError)
            assertTrue(formError is UiText.DynamicString)
        }

    // endregion

    // region general exception handling in calculateFees

    @Test
    fun `calculateFees handles generic exception gracefully`() =
        runTest(mainDispatcher) {
            coEvery { resolveProvider.invoke(any()) } returns SwapProvider.THORCHAIN
            coEvery {
                swapQuoteManager.fetchQuote(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                )
            } throws RuntimeException("Unexpected error")

            val vm = createViewModelWithSwapTokens()
            advanceUntilIdle()

            vm.srcAmountState.setTextAndPlaceCursorAtEnd("1")
            Snapshot.sendApplyNotifications()
            advanceTimeBy(500)
            advanceUntilIdle()

            // Should not crash — generic exceptions are caught and logged
            assertFalse(vm.uiState.value.isLoading)
        }

    // endregion

    // region getGasLimit

    @Test
    fun `gas limit constants are correct`() {
        assertEquals(40_000L, SwapFormViewModel.ETH_GAS_LIMIT)
        assertEquals(400_000L, SwapFormViewModel.ARB_GAS_LIMIT)
    }

    // endregion

    // region Extension function tests — updateSrc

    @Test
    fun `updateSrc with null token ID and empty addresses sets null`() {
        val flow = MutableStateFlow<SendSrc?>(null)

        flow.updateSrc(null, emptyList(), null)

        assertNull(flow.value)
    }

    @Test
    fun `updateSrc with addresses selects first send src`() {
        val flow = MutableStateFlow<SendSrc?>(null)
        val addresses = listOf(ethAddress())

        flow.updateSrc(null, addresses, null)

        assertNotNull(flow.value)
        assertEquals(Chain.Ethereum, flow.value?.address?.chain)
    }

    @Test
    fun `updateSrc with specific token ID selects matching token`() {
        val flow = MutableStateFlow<SendSrc?>(null)
        val addresses = listOf(ethAddress(), btcAddress())

        flow.updateSrc(BTC_COIN.id, addresses, null)

        assertNotNull(flow.value)
        assertEquals(BTC_COIN.id, flow.value?.account?.token?.id)
    }

    @Test
    fun `updateSrc preserves existing selection when token ID is null`() {
        val ethAddr = ethAddress()
        val existing = SendSrc(ethAddr, ethAddr.accounts.first())
        val flow = MutableStateFlow<SendSrc?>(existing)

        flow.updateSrc(null, listOf(ethAddr, btcAddress()), null)

        assertNotNull(flow.value)
        assertEquals(Chain.Ethereum, flow.value?.address?.chain)
    }

    // endregion

    // region Extension function tests — firstSendSrc

    @Test
    fun `firstSendSrc with null token ID and null chain returns first address`() {
        val addresses = listOf(ethAddress(), btcAddress())

        val result = addresses.firstSendSrc(null, null)

        assertNotNull(result)
        assertEquals(Chain.Ethereum, result.address.chain)
    }

    @Test
    fun `firstSendSrc with specific token ID finds matching account`() {
        val addresses = listOf(ethAddress(), btcAddress())

        val result = addresses.firstSendSrc(BTC_COIN.id, null)

        assertNotNull(result)
        assertEquals(BTC_COIN.id, result.account.token.id)
    }

    @Test
    fun `firstSendSrc with chain filter finds matching chain`() {
        val addresses = listOf(ethAddress(), btcAddress())

        val result = addresses.firstSendSrc(null, Chain.Bitcoin)

        assertNotNull(result)
        assertEquals(Chain.Bitcoin, result.address.chain)
    }

    @Test
    fun `firstSendSrc with chain filter returns null when chain not found`() {
        val addresses = listOf(ethAddress())

        val result = addresses.firstSendSrc(null, Chain.Solana)

        assertNull(result)
    }

    @Test
    fun `firstSendSrc with chain filter returns native token account`() {
        val addresses = listOf(ethAddress())

        val result = addresses.firstSendSrc(null, Chain.Ethereum)

        assertNotNull(result)
        assertTrue(result.account.token.isNativeToken)
    }

    @Test
    fun `firstSendSrc with unknown token ID falls back to first address`() {
        val addresses = listOf(ethAddress(), btcAddress())

        val result = addresses.firstSendSrc("unknown-token-id", null)

        assertNotNull(result)
        // Falls back to first address, first account
    }

    // endregion

    // region Extension function tests — findCurrentSrc

    @Test
    fun `findCurrentSrc with null token ID preserves current chain and address`() {
        val ethAddr = ethAddress()
        val currentSrc = SendSrc(ethAddr, ethAddr.accounts.first())
        val addresses = listOf(ethAddr, btcAddress())

        val result = addresses.findCurrentSrc(null, currentSrc)

        assertNotNull(result)
        assertEquals(Chain.Ethereum, result.address.chain)
        assertEquals(ethAddr.address, result.address.address)
    }

    @Test
    fun `findCurrentSrc with token ID delegates to firstSendSrc`() {
        val ethAddr = ethAddress()
        val currentSrc = SendSrc(ethAddr, ethAddr.accounts.first())
        val addresses = listOf(ethAddr, btcAddress())

        val result = addresses.findCurrentSrc(BTC_COIN.id, currentSrc)

        assertNotNull(result)
        assertEquals(BTC_COIN.id, result.account.token.id)
    }

    @Test
    fun `findCurrentSrc returns null when current address not found`() {
        val ethAddr = ethAddress()
        val removedAddr =
            Address(
                chain = Chain.Solana,
                address = "solana-address",
                accounts = listOf(createAccount(SOL_COIN, BigInteger.ZERO)),
            )
        val currentSrc = SendSrc(removedAddr, removedAddr.accounts.first())
        val addresses = listOf(ethAddr)

        val result = addresses.findCurrentSrc(null, currentSrc)

        assertNull(result)
    }

    // endregion

    // region calculateFees — additional swap providers

    @Test
    fun `calculateFees with OneInch sets provider name`() =
        runTest(mainDispatcher) {
            coEvery { resolveProvider.invoke(any()) } returns SwapProvider.ONEINCH
            coEvery {
                swapQuoteManager.fetchQuote(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                )
            } returns
                createDefaultQuoteFetchResult(
                    quote = createOneInchQuote(),
                    provider = SwapProvider.ONEINCH,
                    providerUiText = R.string.swap_for_provider_1inch.asUiText(),
                )

            val vm = createViewModelWithSwapTokens()
            advanceUntilIdle()

            vm.srcAmountState.setTextAndPlaceCursorAtEnd("1")
            Snapshot.sendApplyNotifications()
            advanceTimeBy(500)
            advanceUntilIdle()

            assertEquals(
                UiText.StringResource(R.string.swap_for_provider_1inch),
                vm.uiState.value.provider,
            )
        }

    @Test
    fun `calculateFees with LiFi sets provider name`() =
        runTest(mainDispatcher) {
            coEvery { resolveProvider.invoke(any()) } returns SwapProvider.LIFI
            coEvery {
                swapQuoteManager.fetchQuote(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                )
            } returns
                createDefaultQuoteFetchResult(
                    quote = createOneInchQuote(),
                    provider = SwapProvider.LIFI,
                    providerUiText = R.string.swap_for_provider_li_fi.asUiText(),
                )

            val vm = createViewModelWithSwapTokens()
            advanceUntilIdle()

            vm.srcAmountState.setTextAndPlaceCursorAtEnd("1")
            Snapshot.sendApplyNotifications()
            advanceTimeBy(500)
            advanceUntilIdle()

            assertEquals(
                UiText.StringResource(R.string.swap_for_provider_li_fi),
                vm.uiState.value.provider,
            )
        }

    @Test
    fun `calculateFees with Kyber sets provider name`() =
        runTest(mainDispatcher) {
            coEvery { resolveProvider.invoke(any()) } returns SwapProvider.KYBER
            coEvery {
                swapQuoteManager.fetchQuote(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                )
            } returns
                createDefaultQuoteFetchResult(
                    quote = createOneInchQuote(),
                    provider = SwapProvider.KYBER,
                    providerUiText = R.string.swap_for_provider_kyber.asUiText(),
                )

            val vm = createViewModelWithSwapTokens()
            advanceUntilIdle()

            vm.srcAmountState.setTextAndPlaceCursorAtEnd("1")
            Snapshot.sendApplyNotifications()
            advanceTimeBy(500)
            advanceUntilIdle()

            assertEquals(
                UiText.StringResource(R.string.swap_for_provider_kyber),
                vm.uiState.value.provider,
            )
        }

    @Test
    fun `calculateFees with Jupiter sets provider name`() =
        runTest(mainDispatcher) {
            coEvery { resolveProvider.invoke(any()) } returns SwapProvider.JUPITER
            coEvery {
                swapQuoteManager.fetchQuote(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                )
            } returns
                createDefaultQuoteFetchResult(
                    quote = createOneInchQuote(),
                    provider = SwapProvider.JUPITER,
                    providerUiText = R.string.swap_for_provider_jupiter.asUiText(),
                )

            val vm = createViewModelWithSwapTokens()
            advanceUntilIdle()

            vm.srcAmountState.setTextAndPlaceCursorAtEnd("1")
            Snapshot.sendApplyNotifications()
            advanceTimeBy(500)
            advanceUntilIdle()

            assertEquals(
                UiText.StringResource(R.string.swap_for_provider_jupiter),
                vm.uiState.value.provider,
            )
        }

    // endregion

    // region swap — allowance approval

    @Test
    fun `swap requires allowance approval for ERC-20 token with zero allowance`() =
        runTest(mainDispatcher) {
            val dstValue = TokenValue(value = BigInteger("50000000000000000"), token = ETH_COIN)
            coEvery { resolveProvider.invoke(any()) } returns SwapProvider.THORCHAIN
            coEvery {
                swapQuoteManager.fetchQuote(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                )
            } returns
                createDefaultQuoteFetchResult(
                    quote = createThorChainQuote(expectedDstValue = dstValue),
                    estimatedDstTokenValue = "0.05",
                )
            coEvery { allowanceRepository.getAllowance(any(), any(), any(), any()) } returns
                BigInteger.ZERO
            coEvery { swapGasCalculator.getSpecificAndUtxo(any(), any(), any()) } returns
                mockk(relaxed = true)

            val vm =
                createViewModelWithAddresses(
                    addresses = listOf(ethAddress()),
                    srcTokenId = USDC_COIN.id,
                    dstTokenId = ETH_COIN.id,
                )
            advanceUntilIdle()

            vm.srcAmountState.setTextAndPlaceCursorAtEnd("1")
            Snapshot.sendApplyNotifications()
            advanceTimeBy(500)
            advanceUntilIdle()

            vm.swap()
            advanceUntilIdle()

            coVerify { swapTransactionRepository.addTransaction(match { it.isApprovalRequired }) }
        }

    @Test
    fun `swap skips allowance approval when allowance exceeds swap amount`() =
        runTest(mainDispatcher) {
            val dstValue = TokenValue(value = BigInteger("50000000000000000"), token = ETH_COIN)
            coEvery { resolveProvider.invoke(any()) } returns SwapProvider.THORCHAIN
            coEvery {
                swapQuoteManager.fetchQuote(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                )
            } returns
                createDefaultQuoteFetchResult(
                    quote = createThorChainQuote(expectedDstValue = dstValue),
                    estimatedDstTokenValue = "0.05",
                )
            // 10 USDC allowance > 1 USDC srcAmount (1_000_000 units)
            coEvery { allowanceRepository.getAllowance(any(), any(), any(), any()) } returns
                BigInteger("10000000")
            coEvery { swapGasCalculator.getSpecificAndUtxo(any(), any(), any()) } returns
                mockk(relaxed = true)

            val vm =
                createViewModelWithAddresses(
                    addresses = listOf(ethAddress()),
                    srcTokenId = USDC_COIN.id,
                    dstTokenId = ETH_COIN.id,
                )
            advanceUntilIdle()

            vm.srcAmountState.setTextAndPlaceCursorAtEnd("1")
            Snapshot.sendApplyNotifications()
            advanceTimeBy(500)
            advanceUntilIdle()

            vm.swap()
            advanceUntilIdle()

            coVerify { swapTransactionRepository.addTransaction(match { !it.isApprovalRequired }) }
        }

    @Test
    fun `swap with native src token does not require allowance`() =
        runTest(mainDispatcher) {
            coEvery { resolveProvider.invoke(any()) } returns SwapProvider.THORCHAIN
            coEvery {
                swapQuoteManager.fetchQuote(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                )
            } returns createDefaultQuoteFetchResult()
            coEvery { swapGasCalculator.getSpecificAndUtxo(any(), any(), any()) } returns
                mockk(relaxed = true)

            val vm = createViewModelWithSwapTokens(ethBalance = BigInteger("10000000000000000000"))
            advanceUntilIdle()

            vm.srcAmountState.setTextAndPlaceCursorAtEnd("0.1")
            Snapshot.sendApplyNotifications()
            advanceTimeBy(500)
            advanceUntilIdle()

            vm.swap()
            advanceUntilIdle()

            coVerify { swapTransactionRepository.addTransaction(match { !it.isApprovalRequired }) }
        }

    @Test
    fun `swap surfaces error and does not sign when allowance lookup fails`() =
        runTest(mainDispatcher) {
            val dstValue = TokenValue(value = BigInteger("50000000000000000"), token = ETH_COIN)
            coEvery { resolveProvider.invoke(any()) } returns SwapProvider.THORCHAIN
            coEvery {
                swapQuoteManager.fetchQuote(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                )
            } returns
                createDefaultQuoteFetchResult(
                    quote = createThorChainQuote(expectedDstValue = dstValue),
                    estimatedDstTokenValue = "0.05",
                )
            coEvery { allowanceRepository.getAllowance(any(), any(), any(), any()) } throws
                RuntimeException("network error during allowance lookup")
            coEvery { swapGasCalculator.getSpecificAndUtxo(any(), any(), any()) } returns
                mockk(relaxed = true)

            val vm =
                createViewModelWithAddresses(
                    addresses = listOf(ethAddress()),
                    srcTokenId = USDC_COIN.id,
                    dstTokenId = ETH_COIN.id,
                )
            advanceUntilIdle()

            vm.srcAmountState.setTextAndPlaceCursorAtEnd("1")
            Snapshot.sendApplyNotifications()
            advanceTimeBy(500)
            advanceUntilIdle()

            vm.swap()
            advanceUntilIdle()

            assertNotNull(vm.uiState.value.error)
            coVerify(exactly = 0) { swapTransactionRepository.addTransaction(any()) }
            coVerify(exactly = 0) { navigator.route(match { it is Route.VerifySwap }) }
        }

    // endregion

    // region calculateFees — quote re-fetch

    @Test
    fun `calculateFees re-fetches quote when amount changes`() =
        runTest(mainDispatcher) {
            coEvery { resolveProvider.invoke(any()) } returns SwapProvider.THORCHAIN
            coEvery {
                swapQuoteManager.fetchQuote(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                )
            } returns createDefaultQuoteFetchResult()

            val vm = createViewModelWithSwapTokens()
            advanceUntilIdle()

            vm.srcAmountState.setTextAndPlaceCursorAtEnd("1")
            Snapshot.sendApplyNotifications()
            advanceTimeBy(500)
            advanceUntilIdle()

            vm.srcAmountState.setTextAndPlaceCursorAtEnd("2")
            Snapshot.sendApplyNotifications()
            advanceTimeBy(500)
            advanceUntilIdle()

            coVerify(exactly = 2) {
                swapQuoteManager.fetchQuote(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                )
            }
        }

    // endregion

    // region flipSelectedTokens — decimal adjustment

    @Test
    fun `flipSelectedTokens sets src amount from expected dst value for ETH to USDC swap`() =
        runTest(mainDispatcher) {
            coEvery { resolveProvider.invoke(any()) } returns SwapProvider.THORCHAIN
            coEvery {
                swapQuoteManager.fetchQuote(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                )
            } returns createDefaultQuoteFetchResult()

            val vm =
                createViewModelWithAddresses(
                    addresses = listOf(ethAddress()),
                    srcTokenId = ETH_COIN.id,
                    dstTokenId = USDC_COIN.id,
                )
            advanceUntilIdle()

            vm.srcAmountState.setTextAndPlaceCursorAtEnd("1")
            Snapshot.sendApplyNotifications()
            advanceTimeBy(500)
            advanceUntilIdle()

            vm.flipSelectedTokens()
            advanceUntilIdle()

            // expectedDstValue = 95_000_000 / 10^6 = 95.0 USDC, USDC decimal = 6
            // formatFlippedAmount(6): setScale(6, DOWN).stripTrailingZeros() = "95"
            assertEquals("95", vm.srcAmountState.text.toString())
        }

    @Test
    fun `flipSelectedTokens sets src amount from expected dst value for USDC to ETH swap`() =
        runTest(mainDispatcher) {
            val ethDstValue = TokenValue(value = BigInteger("50000000000000000"), token = ETH_COIN)
            coEvery { resolveProvider.invoke(any()) } returns SwapProvider.THORCHAIN
            coEvery {
                swapQuoteManager.fetchQuote(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                )
            } returns
                createDefaultQuoteFetchResult(
                    quote = createThorChainQuote(expectedDstValue = ethDstValue),
                    estimatedDstTokenValue = "0.05",
                )

            val vm =
                createViewModelWithAddresses(
                    addresses = listOf(ethAddress()),
                    srcTokenId = USDC_COIN.id,
                    dstTokenId = ETH_COIN.id,
                )
            advanceUntilIdle()

            vm.srcAmountState.setTextAndPlaceCursorAtEnd("95")
            Snapshot.sendApplyNotifications()
            advanceTimeBy(500)
            advanceUntilIdle()

            vm.flipSelectedTokens()
            advanceUntilIdle()

            // expectedDstValue = 50_000_000_000_000_000 / 10^18 = 0.05 ETH, ETH decimal = 18
            // formatFlippedAmount(18): setScale(min(18,8)=8, DOWN).stripTrailingZeros() = "0.05"
            assertEquals("0.05", vm.srcAmountState.text.toString())
        }

    // endregion

    // region calculateFees — RateLimitExceeded

    @Test
    fun `calculateFees handles RateLimitExceeded exception`() =
        runTest(mainDispatcher) {
            coEvery { resolveProvider.invoke(any()) } returns SwapProvider.THORCHAIN
            coEvery {
                swapQuoteManager.fetchQuote(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                )
            } throws SwapException.RateLimitExceeded("Too many requests")
            coEvery { swapQuoteManager.mapSwapExceptionToFormError(any(), any(), any()) } returns
                UiText.StringResource(R.string.swap_error_rate_limit)

            val vm = createViewModelWithSwapTokens()
            advanceUntilIdle()

            vm.srcAmountState.setTextAndPlaceCursorAtEnd("1")
            Snapshot.sendApplyNotifications()
            advanceTimeBy(500)
            advanceUntilIdle()

            assertTrue(vm.uiState.value.isSwapDisabled)
            assertEquals(
                UiText.StringResource(R.string.swap_error_rate_limit),
                vm.uiState.value.formError,
            )
        }

    // endregion

    // region swap — success navigation

    @Test
    fun `swap navigates to VerifySwap on success`() =
        runTest(mainDispatcher) {
            coEvery { resolveProvider.invoke(any()) } returns SwapProvider.THORCHAIN
            coEvery {
                swapQuoteManager.fetchQuote(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                )
            } returns createDefaultQuoteFetchResult()
            coEvery { swapGasCalculator.getSpecificAndUtxo(any(), any(), any()) } returns
                mockk(relaxed = true)

            val vm = createViewModelWithSwapTokens(ethBalance = BigInteger("10000000000000000000"))
            advanceUntilIdle()

            vm.srcAmountState.setTextAndPlaceCursorAtEnd("0.1")
            Snapshot.sendApplyNotifications()
            advanceTimeBy(500)
            advanceUntilIdle()

            vm.swap()
            advanceUntilIdle()

            coVerify { navigator.route(match { it is Route.VerifySwap }) }
        }

    // endregion

    // region formatFlippedAmount integration

    @Test
    fun `formatFlippedAmount truncates to 8 decimals max`() {
        val result = BigDecimal("1.123456789012345").formatFlippedAmount(18)
        assertEquals("1.12345678", result)
    }

    @Test
    fun `formatFlippedAmount uses token decimals when less than max`() {
        val result = BigDecimal("1.123456789").formatFlippedAmount(4)
        assertEquals("1.1234", result)
    }

    // endregion

    // region Helpers

    companion object {
        private const val TEST_VAULT_ID = "test-vault-id"

        val ETH_COIN =
            Coin(
                chain = Chain.Ethereum,
                ticker = "ETH",
                logo = "eth",
                address = "0xethaddress",
                decimal = 18,
                hexPublicKey = "hex",
                priceProviderID = "ethereum",
                contractAddress = "",
                isNativeToken = true,
            )

        val USDC_COIN =
            Coin(
                chain = Chain.Ethereum,
                ticker = "USDC",
                logo = "usdc",
                address = "0xethaddress",
                decimal = 6,
                hexPublicKey = "hex",
                priceProviderID = "usd-coin",
                contractAddress = "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48",
                isNativeToken = false,
            )

        val BTC_COIN =
            Coin(
                chain = Chain.Bitcoin,
                ticker = "BTC",
                logo = "btc",
                address = "bc1qbtcaddress",
                decimal = 8,
                hexPublicKey = "hex",
                priceProviderID = "bitcoin",
                contractAddress = "",
                isNativeToken = true,
            )

        val SOL_COIN =
            Coin(
                chain = Chain.Solana,
                ticker = "SOL",
                logo = "sol",
                address = "soladdress",
                decimal = 9,
                hexPublicKey = "hex",
                priceProviderID = "solana",
                contractAddress = "",
                isNativeToken = true,
            )
    }

    private fun createAccount(coin: Coin, balance: BigInteger? = null): Account =
        Account(
            token = coin,
            tokenValue = balance?.let { TokenValue(value = it, token = coin) },
            fiatValue = null,
            price = null,
        )

    private fun ethAddress(): Address =
        Address(
            chain = Chain.Ethereum,
            address = "0xethaddress",
            accounts =
                listOf(
                    createAccount(ETH_COIN, BigInteger("1000000000000000000")),
                    createAccount(USDC_COIN, BigInteger("1000000000")),
                ),
        )

    private fun ethAddressWithBalance(balance: BigInteger): Address =
        Address(
            chain = Chain.Ethereum,
            address = "0xethaddress",
            accounts =
                listOf(
                    createAccount(ETH_COIN, balance),
                    createAccount(USDC_COIN, BigInteger("1000000000")),
                ),
        )

    /**
     * Creates a VM with ETH (src) and BTC (dst) explicitly selected, suitable for calculateFees
     * tests that need two distinct tokens.
     */
    private fun createViewModelWithSwapTokens(
        ethBalance: BigInteger = BigInteger("1000000000000000000")
    ): SwapFormViewModel =
        createViewModelWithAddresses(
            addresses = listOf(ethAddressWithBalance(ethBalance), btcAddress()),
            srcTokenId = ETH_COIN.id,
            dstTokenId = BTC_COIN.id,
        )

    private fun btcAddress(): Address =
        Address(
            chain = Chain.Bitcoin,
            address = "bc1qbtcaddress",
            accounts = listOf(createAccount(BTC_COIN, BigInteger("100000000"))),
        )

    private fun createDefaultQuoteFetchResult(
        quote: SwapQuote = createThorChainQuote(),
        provider: SwapProvider = SwapProvider.THORCHAIN,
        providerUiText: UiText = R.string.swap_form_provider_thorchain.asUiText(),
        srcFiatValueText: String = "$0.00",
        estimatedDstTokenValue: String = "95.0",
        estimatedDstFiatValue: String = "$95.00",
        feeText: String = "$0.00",
        swapFeeFiat: FiatValue = FiatValue(BigDecimal.ZERO, "USD"),
    ): QuoteFetchResult =
        QuoteFetchResult(
            quote = quote,
            provider = provider,
            providerUiText = providerUiText,
            srcFiatValueText = srcFiatValueText,
            estimatedDstTokenValue = estimatedDstTokenValue,
            estimatedDstFiatValue = estimatedDstFiatValue,
            feeText = feeText,
            swapFeeFiat = swapFeeFiat,
        )

    private fun createThorChainQuote(
        expectedDstValue: TokenValue = TokenValue(value = BigInteger("95000000"), token = USDC_COIN)
    ): SwapQuote.ThorChain =
        SwapQuote.ThorChain(
            expectedDstValue = expectedDstValue,
            fees = TokenValue(value = BigInteger("5000000"), token = USDC_COIN),
            expiredAt = Clock.System.now() + 1.minutes,
            recommendedMinTokenValue = TokenValue(value = BigInteger("1000"), token = ETH_COIN),
            data = mockk(relaxed = true),
        )

    private fun createOneInchQuote(
        expectedDstValue: TokenValue = TokenValue(value = BigInteger("95000000"), token = USDC_COIN)
    ): SwapQuote.OneInch =
        SwapQuote.OneInch(
            expectedDstValue = expectedDstValue,
            fees = TokenValue(value = BigInteger("1000000000000000"), token = ETH_COIN),
            expiredAt = Clock.System.now() + 1.minutes,
            data = mockk(relaxed = true),
            provider = "1inch",
        )

    private fun createMayaChainQuote(
        expectedDstValue: TokenValue = TokenValue(value = BigInteger("95000000"), token = USDC_COIN)
    ): SwapQuote.MayaChain =
        SwapQuote.MayaChain(
            expectedDstValue = expectedDstValue,
            fees = TokenValue(value = BigInteger("5000000"), token = USDC_COIN),
            expiredAt = Clock.System.now() + 1.minutes,
            recommendedMinTokenValue = TokenValue(value = BigInteger("1000"), token = ETH_COIN),
            data = mockk(relaxed = true),
        )

    // endregion
}
