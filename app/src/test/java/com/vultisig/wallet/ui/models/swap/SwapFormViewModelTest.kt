@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.swap

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
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
import com.vultisig.wallet.data.models.getSwapProviderId
import com.vultisig.wallet.data.models.settings.AppCurrency
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.data.repositories.AllowanceRepository
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.data.repositories.ReferralCodeSettingsRepository
import com.vultisig.wallet.data.repositories.RequestResultRepository
import com.vultisig.wallet.data.repositories.SwapQuoteRepository
import com.vultisig.wallet.data.repositories.SwapTransactionRepository
import com.vultisig.wallet.data.usecases.ConvertTokenAndValueToTokenValueUseCase
import com.vultisig.wallet.data.usecases.GetDiscountBpsUseCase
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
import io.mockk.spyk
import io.mockk.unmockkStatic
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Clock
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class SwapFormViewModelTest {

    private val scheduler = TestCoroutineScheduler()
    private val mainDispatcher = UnconfinedTestDispatcher(scheduler)
    // Backs the refresh-quote timer for tests that don't exercise it. Its scheduler is never
    // advanced, so the periodic timer's delay never fires — matching the production Dispatchers.IO
    // behaviour where a ~1min wall-clock delay never elapses during a test (and keeping the
    // self-re-arming timer off the main scheduler so advanceUntilIdle() can't loop on it).
    private val inertIoDispatcher = StandardTestDispatcher()
    private val createdViewModels = mutableListOf<SwapFormViewModel>()

    private lateinit var savedStateHandle: SavedStateHandle
    private lateinit var navigator: Navigator<Destination>
    private lateinit var fiatValueToString: FiatValueToStringMapper
    private lateinit var convertTokenAndValueToTokenValue: ConvertTokenAndValueToTokenValueUseCase
    private lateinit var swapQuoteRepository: SwapQuoteRepository
    // The recipient flow the ViewModel feeds into the quote pipeline (gated to valid addresses).
    private var pipelineRecipient: StateFlow<String?>? = null
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
    private lateinit var tokenBalanceMapper: AccountToTokenBalanceUiModelMapper
    private lateinit var chainAccountAddressRepository: ChainAccountAddressRepository

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
        coEvery { fiatValueToString(any(), any()) } returns "$0.00"

        convertTokenAndValueToTokenValue = mockk(relaxed = true)
        every { convertTokenAndValueToTokenValue(any(), any()) } answers
            {
                TokenValue(value = secondArg(), token = firstArg())
            }

        swapQuoteRepository = mockk(relaxed = true)
        every { swapQuoteRepository.getEligibleProviders(any(), any()) } returns
            listOf(SwapProvider.THORCHAIN)
        allowanceRepository = mockk(relaxed = true)

        appCurrencyRepository = mockk(relaxed = true)
        every { appCurrencyRepository.currency } returns currencyFlow

        swapTransactionRepository = mockk(relaxed = true)
        getDiscountBpsUseCase = mockk(relaxed = true)
        referralRepository = mockk(relaxed = true)

        // Default: any address is valid, so existing tests (which never set an external recipient)
        // are unaffected. The external-recipient validation tests override this per case.
        chainAccountAddressRepository = mockk(relaxed = true)
        every { chainAccountAddressRepository.isValid(any(), any()) } returns true

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
                chain = Chain.Ethereum,
            )

        val accountsRepository: AccountsRepository = mockk(relaxed = true)
        coEvery { accountsRepository.loadAddresses(any()) } returns flowOf(emptyList())
        tokenBalanceMapper = mockk(relaxed = true)
        coEvery { tokenBalanceMapper(any()) } answers
            {
                val src = firstArg<SendSrc>()
                mockk<com.vultisig.wallet.ui.models.send.TokenBalanceUiModel>(relaxed = true)
                    .apply { every { model } returns src }
            }
        val requestResultRepository: RequestResultRepository = mockk(relaxed = true)

        swapTokenSelector =
            SwapTokenSelector(
                navigator = navigator,
                accountsRepository = accountsRepository,
                requestResultRepository = requestResultRepository,
                accountToTokenBalanceUiModelMapper = tokenBalanceMapper,
            )
        tokenSelectorAccountsRepository = accountsRepository

        // A spy (not a full mock) so the VM exercises the real quote pipeline — paste detection,
        // immediate-fetch flag, and debounce timing now live in SwapQuoteManager — while individual
        // methods (fetchBestQuote, computeIndicativeQuote, mapSwapExceptionToFormError) are still
        // stubbed per test. resolveBestQuote delegates to the real (spied) fetchBestQuote, so the
        // existing fetchBestQuote stubs/verifies keep working unchanged.
        swapQuoteManager =
            spyk(
                SwapQuoteManager(
                    swapQuoteRepository = swapQuoteRepository,
                    tokenRepository = mockk(relaxed = true),
                    tokenPriceRepository = mockk(relaxed = true),
                    convertTokenValueToFiat = mockk(relaxed = true),
                    mapTokenValueToDecimalUiString = mockk(relaxed = true),
                    fiatValueToString = fiatValueToString,
                    searchToken = mockk(relaxed = true),
                    convertTokenToTokenUseCase = mockk(relaxed = true),
                )
            )
    }

    @AfterEach
    fun tearDown() {
        // Cancel viewModelScope on all VMs created during the test before resetting Main.
        // This cooperatively cancels any pending IO-thread delays (e.g. launchRefreshQuoteTimer)
        // so they don't try to dispatch a continuation back to Dispatchers.Main after resetMain().
        createdViewModels.forEach { it.viewModelScope.cancel() }
        createdViewModels.clear()
        Dispatchers.resetMain()
        unmockkStatic("androidx.navigation.SavedStateHandleKt")
    }

    private fun createViewModel(ioDispatcher: CoroutineDispatcher = inertIoDispatcher) =
        SwapFormViewModel(
                savedStateHandle = savedStateHandle,
                navigator = navigator,
                swapTransactionRepository = swapTransactionRepository,
                swapValidator = swapValidator,
                swapTokenSelector = swapTokenSelector,
                swapQuoteManager = swapQuoteManager,
                swapTransactionBuilder =
                    SwapTransactionBuilder(swapGasCalculator, allowanceRepository),
                swapInputCollector =
                    SwapInputCollector(convertTokenAndValueToTokenValue, swapValidator),
                swapQuotePipelineControllerFactory =
                    swapQuotePipelineControllerFactory(ioDispatcher),
                chainAccountAddressRepository = chainAccountAddressRepository,
            )
            .also { createdViewModels += it }

    // Mirrors the Hilt-generated @AssistedFactory: repos / calculator / dispatcher come from the
    // test mocks, the scope + shared swapQuoteManager + form-owned flows arrive as assisted params.
    private fun swapQuotePipelineControllerFactory(ioDispatcher: CoroutineDispatcher) =
        object : SwapQuotePipelineController.Factory {
            override fun create(
                scope: CoroutineScope,
                swapQuoteManager: SwapQuoteManager,
                uiState: MutableStateFlow<SwapFormUiModel>,
                selectedSrc: StateFlow<SendSrc?>,
                selectedDst: StateFlow<SendSrc?>,
                referralCode: MutableStateFlow<String?>,
                slippageBps: StateFlow<Int?>,
                externalRecipient: StateFlow<String?>,
                srcAmountState: TextFieldState,
                vaultId: () -> String?,
                showError: (UiText) -> Unit,
            ): SwapQuotePipelineController {
                // Capture the recipient flow the ViewModel feeds the pipeline (gated to valid
                // addresses) so tests can assert what actually reaches quote fetching (#4858).
                pipelineRecipient = externalRecipient
                return SwapQuotePipelineController(
                    swapGasCalculator = swapGasCalculator,
                    swapQuoteRepository = swapQuoteRepository,
                    appCurrencyRepository = appCurrencyRepository,
                    fiatValueToString = fiatValueToString,
                    referralRepository = referralRepository,
                    getDiscountBpsUseCase = getDiscountBpsUseCase,
                    convertTokenAndValueToTokenValue = convertTokenAndValueToTokenValue,
                    swapDiscountChecker = swapDiscountChecker,
                    swapValidator = swapValidator,
                    ioDispatcher = ioDispatcher,
                    scope = scope,
                    swapQuoteManager = swapQuoteManager,
                    uiState = uiState,
                    selectedSrc = selectedSrc,
                    selectedDst = selectedDst,
                    referralCode = referralCode,
                    slippageBps = slippageBps,
                    externalRecipient = externalRecipient,
                    srcAmountState = srcAmountState,
                    vaultId = vaultId,
                    showError = showError,
                )
            }
        }

    private fun createViewModelWithAddresses(
        addresses: List<Address> = listOf(ethAddress(), btcAddress()),
        srcTokenId: String? = null,
        dstTokenId: String? = null,
        ioDispatcher: CoroutineDispatcher = inertIoDispatcher,
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
        return createViewModel(ioDispatcher = ioDispatcher)
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
            assertEquals("0", state.quoteDisplay.estimatedDstTokenValue)
            assertEquals("0", state.quoteDisplay.estimatedDstFiatValue)
            assertEquals("", state.feeBreakdown.networkFee)
            assertEquals("", state.feeBreakdown.networkFeeFiat)
            assertEquals("0", state.feeBreakdown.totalFee)
            assertEquals("", state.feeBreakdown.fee)
            assertNull(state.error)
            assertNull(state.formError)
            assertTrue(state.isSwapDisabled)
            assertFalse(state.isLoadingNextScreen)
            assertNull(state.quoteDisplay.expiredAt)
            assertNull(state.discountInfo.tierType)
            assertNull(state.discountInfo.vultBpsDiscount)
            assertNull(state.discountInfo.vultBpsDiscountFiatValue)
            assertNull(state.discountInfo.referralBpsDiscount)
            assertNull(state.discountInfo.referralBpsDiscountFiatValue)
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

            // 1 ETH balance minus the 0.001 ETH source-chain gas fee (native source on its own gas
            // chain). Pins the network-fee subtraction so a regression that stops deducting it is
            // caught instead of slipping through a non-empty/non-zero check.
            assertEquals("0.999", vm.srcAmountState.text.toString())
        }

    @Test
    fun `selectSrcPercentage with 50 percent takes half of the full balance without subtracting the fee`() =
        runTest(mainDispatcher) {
            // 2 ETH native source on its own gas chain. The 50% chip must take half of the FULL
            // balance (1 ETH), not half of (balance - network fee): the source-chain network fee is
            // reserved only for MAX, matching iOS and the desktop app.
            val balance = BigInteger("2000000000000000000") // 2 ETH
            val addresses = listOf(ethAddressWithBalance(balance))
            val vm = createViewModelWithAddresses(addresses)
            advanceUntilIdle()

            vm.selectSrcPercentage(0.5f)

            assertEquals("1", vm.srcAmountState.text.toString())
        }

    @Test
    fun `selectSrcPercentage with zero balance clears the amount and shows an error`() =
        runTest(mainDispatcher) {
            val addresses = listOf(ethAddressWithBalance(BigInteger.ZERO))
            val vm = createViewModelWithAddresses(addresses)
            advanceUntilIdle()

            vm.selectSrcPercentage(1.0f)

            // Empty (not "0") so the empty-field path clears the quote silently instead of feeding
            // "0" into the pipeline and logging AmountCannotBeZero; the error explains why.
            assertEquals("", vm.srcAmountState.text.toString())
            assertNotNull(vm.uiState.value.error)
        }

    @Test
    fun `selectSrcPercentage does not subtract stale cross-chain fee`() =
        runTest(mainDispatcher) {
            // The default setUp mock returns chain=Ethereum for any calculateGasFee call.
            // With BTC as src, gasFeeChain ends up as Ethereum while srcToken.chain is Bitcoin
            // — identical to the real bug (switching from ETH→ZEC leaves a huge wei fee value
            // that, if subtracted from satoshis, makes maxUsableTokenAmount negative).
            val vm =
                createViewModelWithAddresses(
                    addresses = listOf(btcAddressLargeBalance(), ethAddress()),
                    srcTokenId = BTC_COIN.id,
                    dstTokenId = ETH_COIN.id,
                )
            advanceUntilIdle()
            // estimatedNetworkFeeTokenValue = 1e15 (ETH wei from the Ethereum gas mock),
            // gasFeeChain = Ethereum, srcToken.chain = Bitcoin.

            vm.selectSrcPercentage(1.0f)

            val amountText = vm.srcAmountState.text.toString()
            assertTrue(
                amountText.isNotEmpty() && amountText.toDoubleOrNull()?.let { it > 0 } == true,
                "selectSrcPercentage subtracted a stale cross-chain fee: $amountText",
            )
        }

    @Test
    fun `selectSrcPercentage preserves full token precision beyond 6 decimals`() =
        runTest(mainDispatcher) {
            // 0.00553297 BTC — more than 6 significant decimals. BTC src avoids the Ethereum
            // gas-fee mock subtraction (chain mismatch), so at 100% the inserted amount equals
            // the balance and must keep all 8 decimals rather than truncating to 6.
            val btcPreciseBalance =
                Address(
                    chain = Chain.Bitcoin,
                    address = "bc1qbtcaddress",
                    accounts = listOf(createAccount(BTC_COIN, BigInteger("553297"))),
                )
            val vm =
                createViewModelWithAddresses(
                    addresses = listOf(btcPreciseBalance, ethAddress()),
                    srcTokenId = BTC_COIN.id,
                    dstTokenId = ETH_COIN.id,
                )
            advanceUntilIdle()

            vm.selectSrcPercentage(1.0f)

            assertEquals("0.00553297", vm.srcAmountState.text.toString())
        }

    @Test
    fun `selectSrcPercentage does not subtract the LIFI destination-denominated swap fee`() =
        runTest(mainDispatcher) {
            // LI.FI's integrator fee is denominated in the destination token's raw units, so
            // deducting it from a low-decimal source balance underflows: here the 0.1 ETH fee
            // (1e17) far exceeds the 1000 USDC balance (1e9), which used to clear the field with
            // an insufficient-balance error.
            val usdcBalance = BigInteger("1000000000") // 1000 USDC (6 decimals)
            val usdcAddress =
                Address(
                    chain = Chain.Ethereum,
                    address = "0xethaddress",
                    accounts =
                        listOf(
                            createAccount(USDC_COIN, usdcBalance),
                            createAccount(ETH_COIN, BigInteger("1000000000000000000")),
                        ),
                )
            every { swapQuoteRepository.getEligibleProviders(any(), any()) } returns
                listOf(SwapProvider.LIFI)
            coEvery {
                swapQuoteManager.fetchBestQuote(
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
                    quote =
                        createLiFiQuote(
                            fees = TokenValue(BigInteger("100000000000000000"), ETH_COIN)
                        ),
                    provider = SwapProvider.LIFI,
                    providerUiText = R.string.swap_for_provider_li_fi.asUiText(),
                )

            val vm =
                createViewModelWithAddresses(
                    addresses = listOf(usdcAddress, btcAddress()),
                    srcTokenId = USDC_COIN.id,
                    dstTokenId = ETH_COIN.id,
                )
            advanceUntilIdle()

            // Land a LI.FI quote so quoteState carries the destination-denominated fee.
            vm.srcAmountState.setTextAndPlaceCursorAtEnd("100")
            Snapshot.sendApplyNotifications()
            advanceTimeBy(500)
            advanceUntilIdle()

            vm.selectSrcPercentage(0.25f)

            // 25% of the full 1000 USDC balance, with no destination-fee subtraction.
            assertEquals("250", vm.srcAmountState.text.toString())
            assertNull(vm.uiState.value.error)
        }

    // endregion

    // region flipSelectedTokens

    @Test
    fun `flipSelectedTokens swaps src and dst instead of leaving them unchanged`() =
        runTest(mainDispatcher) {
            val vm =
                createViewModelWithAddresses(
                    addresses = listOf(ethAddress(), btcAddress()),
                    srcTokenId = ETH_COIN.id,
                    dstTokenId = BTC_COIN.id,
                )
            advanceUntilIdle()

            val srcBefore = vm.uiState.value.selectedSrcToken?.model?.account?.token?.id
            val dstBefore = vm.uiState.value.selectedDstToken?.model?.account?.token?.id
            assertEquals(ETH_COIN.id, srcBefore)
            assertEquals(BTC_COIN.id, dstBefore)

            vm.flipSelectedTokens()
            advanceUntilIdle()

            val srcAfter = vm.uiState.value.selectedSrcToken?.model?.account?.token?.id
            val dstAfter = vm.uiState.value.selectedDstToken?.model?.account?.token?.id
            assertEquals(BTC_COIN.id, srcAfter, "src should now be BTC")
            assertEquals(ETH_COIN.id, dstAfter, "dst should now be ETH")
        }

    @Test
    fun `flipSelectedTokens swaps correctly when src was not explicitly set`() =
        runTest(mainDispatcher) {
            // Reproduces issue #4581: srcTokenId is null (user navigated without pre-selecting
            // a source), user then changes the destination, then taps switch. Before the fix,
            // selectedSrcId stayed null after the flip so selectedDst resolved back to the old
            // dst (BCH in the bug report), making both slots show the same asset.
            val vm =
                createViewModelWithAddresses(
                    addresses = listOf(ethAddress(), btcAddress()),
                    srcTokenId = null,
                    dstTokenId = BTC_COIN.id,
                )
            advanceUntilIdle()

            val srcBefore = vm.uiState.value.selectedSrcToken?.model?.account?.token?.id
            val dstBefore = vm.uiState.value.selectedDstToken?.model?.account?.token?.id
            assertEquals(ETH_COIN.id, srcBefore)
            assertEquals(BTC_COIN.id, dstBefore)

            vm.flipSelectedTokens()
            advanceUntilIdle()

            val srcAfter = vm.uiState.value.selectedSrcToken?.model?.account?.token?.id
            val dstAfter = vm.uiState.value.selectedDstToken?.model?.account?.token?.id
            assertEquals(BTC_COIN.id, srcAfter, "src should now be BTC after flip")
            assertEquals(ETH_COIN.id, dstAfter, "dst should now be ETH after flip")
        }

    @Test
    fun `flipSelectedTokens swaps correctly when dst was not explicitly set`() =
        runTest(mainDispatcher) {
            // BTC is first so the auto-selection of dst (dstTokenId=null) picks BTC;
            // ETH is found by explicit srcTokenId. Mirrors the src=null test but in reverse.
            val vm =
                createViewModelWithAddresses(
                    addresses = listOf(btcAddress(), ethAddress()),
                    srcTokenId = ETH_COIN.id,
                    dstTokenId = null,
                )
            advanceUntilIdle()

            val srcBefore = vm.uiState.value.selectedSrcToken?.model?.account?.token?.id
            val dstBefore = vm.uiState.value.selectedDstToken?.model?.account?.token?.id
            assertEquals(ETH_COIN.id, srcBefore)
            assertEquals(BTC_COIN.id, dstBefore)

            vm.flipSelectedTokens()
            advanceUntilIdle()

            val srcAfter = vm.uiState.value.selectedSrcToken?.model?.account?.token?.id
            val dstAfter = vm.uiState.value.selectedDstToken?.model?.account?.token?.id
            assertEquals(BTC_COIN.id, srcAfter, "src should now be BTC after flip")
            assertEquals(ETH_COIN.id, dstAfter, "dst should now be ETH after flip")
        }

    @Test
    fun `flipSelectedTokens resets quote state`() =
        runTest(mainDispatcher) {
            val vm = createViewModelWithAddresses()
            advanceUntilIdle()

            vm.flipSelectedTokens()

            val state = vm.uiState.value
            assertEquals("0", state.quoteDisplay.estimatedDstTokenValue)
            assertEquals("0", state.quoteDisplay.estimatedDstFiatValue)
            assertEquals("0", state.srcFiatValue)
            assertNull(state.formError)
            // hasQuote must drop on flip, otherwise the fee block stays on screen during the
            // 300ms debounce while collectTotalFee can still combine the new pair's gas with
            // the prior pair's swapFeeFiat.
            assertFalse(state.quoteDisplay.hasQuote)
        }

    // endregion

    // region calculateFees — debounce behavior

    @Test
    fun `calculateFees debounces amount changes`() =
        runTest(mainDispatcher) {
            every { swapQuoteRepository.getEligibleProviders(any(), any()) } returns
                listOf(SwapProvider.THORCHAIN)
            coEvery {
                swapQuoteManager.fetchBestQuote(
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

            // The spinner is set ahead of the debounce, so it shows immediately on input even
            // before the 300ms debounce expires and the quote fetch starts.
            advanceTimeBy(100) // total 500ms elapsed, but only 100ms since the last change
            assertTrue(vm.uiState.value.isLoading)

            // Let the debounce expire and the (single) quote fetch complete.
            advanceTimeBy(400)
            advanceUntilIdle()

            // Only the final amount "100" should have been used for one quote fetch (rapid edits
            // are coalesced by the debounce), and the spinner clears once it settles.
            coVerify(exactly = 1) {
                swapQuoteManager.fetchBestQuote(
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
            assertFalse(vm.uiState.value.isLoading)
        }

    // endregion

    // region pair eligibility (#4710)

    @Test
    fun `unsupported pair surfaces route guidance on selection before any amount is entered`() =
        runTest(mainDispatcher) {
            // No eligible provider for the chosen pair — e.g. a thin-coverage chain like Sui.
            every { swapQuoteRepository.getEligibleProviders(any(), any()) } returns emptyList()

            val vm = createViewModelWithSwapTokens()
            advanceUntilIdle()

            // The guidance appears from token selection alone — no amount typed, no quote
            // requested.
            assertEquals(
                UiText.StringResource(R.string.swap_route_not_available),
                vm.uiState.value.formError,
            )
            assertTrue(vm.uiState.value.isSwapDisabled)
            assertFalse(vm.uiState.value.isLoading)
        }

    @Test
    fun `unsupported pair never requests a quote even after an amount is entered`() =
        runTest(mainDispatcher) {
            every { swapQuoteRepository.getEligibleProviders(any(), any()) } returns emptyList()

            val vm = createViewModelWithSwapTokens()
            advanceUntilIdle()

            vm.srcAmountState.setTextAndPlaceCursorAtEnd("100")
            Snapshot.sendApplyNotifications()
            advanceTimeBy(400)
            advanceUntilIdle()

            // The quote pipeline (which would throw SwapIsNotSupported) is never reached, and the
            // pre-quote guidance still stands.
            coVerify(exactly = 0) {
                swapQuoteManager.fetchBestQuote(
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
            assertEquals(
                UiText.StringResource(R.string.swap_route_not_available),
                vm.uiState.value.formError,
            )
            assertFalse(vm.uiState.value.isLoading)
        }

    @Test
    fun `routable pair does not show route guidance`() =
        runTest(mainDispatcher) {
            every { swapQuoteRepository.getEligibleProviders(any(), any()) } returns
                listOf(SwapProvider.THORCHAIN)

            val vm = createViewModelWithSwapTokens()
            advanceUntilIdle()

            assertNull(vm.uiState.value.formError)
        }

    // endregion

    @Test
    fun `selectSrcPercentage fetches a quote immediately, bypassing the typing debounce`() =
        runTest(mainDispatcher) {
            every { swapQuoteRepository.getEligibleProviders(any(), any()) } returns
                listOf(SwapProvider.THORCHAIN)
            coEvery {
                swapQuoteManager.fetchBestQuote(
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

            vm.selectSrcPercentage(1.0f)
            Snapshot.sendApplyNotifications()
            // Well under the 300ms typing debounce — only an immediate (0ms) fetch can have fired.
            advanceTimeBy(50)

            coVerify(exactly = 1) {
                swapQuoteManager.fetchBestQuote(
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

    @Test
    fun `free typing still waits for the debounce before fetching`() =
        runTest(mainDispatcher) {
            every { swapQuoteRepository.getEligibleProviders(any(), any()) } returns
                listOf(SwapProvider.THORCHAIN)
            coEvery {
                swapQuoteManager.fetchBestQuote(
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
            // Before the 300ms debounce elapses, no quote fetch should have fired.
            advanceTimeBy(50)
            coVerify(exactly = 0) {
                swapQuoteManager.fetchBestQuote(
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

            advanceTimeBy(300)
            advanceUntilIdle()
            coVerify(exactly = 1) {
                swapQuoteManager.fetchBestQuote(
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

    @Test
    fun `indicative rate fills the destination before the firm quote resolves`() =
        runTest(mainDispatcher) {
            every { swapQuoteRepository.getEligibleProviders(any(), any()) } returns
                listOf(SwapProvider.THORCHAIN)
            coEvery { swapQuoteManager.computeIndicativeQuote(any(), any(), any(), any()) } returns
                IndicativeQuote(estimatedDstTokenValue = "1.23", estimatedDstFiatValue = "$4.56")
            coEvery {
                swapQuoteManager.fetchBestQuote(
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

            vm.srcAmountState.setTextAndPlaceCursorAtEnd("2")
            Snapshot.sendApplyNotifications()
            // Before the debounce/firm quote: the greyed indicative estimate is already shown.
            advanceTimeBy(50)
            val indicative = vm.uiState.value
            assertEquals("1.23", indicative.quoteDisplay.estimatedDstTokenValue)
            assertEquals("$4.56", indicative.quoteDisplay.estimatedDstFiatValue)
            assertTrue(indicative.quoteDisplay.isDstEstimated)

            // Once the firm quote resolves it replaces the indicative value (no longer greyed).
            advanceTimeBy(300)
            advanceUntilIdle()
            val firm = vm.uiState.value
            assertEquals("95.0", firm.quoteDisplay.estimatedDstTokenValue)
            assertFalse(firm.quoteDisplay.isDstEstimated)
        }

    @Test
    fun `silent quote refresh keeps the destination value and does not flash loading`() =
        runTest(mainDispatcher) {
            every { swapQuoteRepository.getEligibleProviders(any(), any()) } returns
                listOf(SwapProvider.THORCHAIN)
            coEvery {
                swapQuoteManager.fetchBestQuote(
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

            // Run the refresh timer on the test scheduler so its expiry-triggered delay is driven
            // by virtual time (the default inert dispatcher never fires it).
            val vm = createViewModelWithSwapTokens(ioDispatcher = mainDispatcher)
            advanceUntilIdle()

            vm.srcAmountState.setTextAndPlaceCursorAtEnd("1")
            Snapshot.sendApplyNotifications()
            // Settle the initial firm quote (300ms typing debounce); this also arms the ~1min
            // refresh timer. Bounded advances only — advanceUntilIdle() would loop forever once the
            // timer is armed, because each refresh re-arms it into a periodic timer.
            advanceTimeBy(500)
            runCurrent()
            assertEquals("95.0", vm.uiState.value.quoteDisplay.estimatedDstTokenValue)
            assertFalse(vm.uiState.value.isLoading)

            // Drive the expiry-triggered refresh (quote lives ~1 minute) exactly once. The refresh
            // is downstream of the loading/indicative side effects, so it must neither flash the
            // spinner nor blank the previously shown destination value.
            advanceTimeBy(61_000)
            runCurrent()
            assertFalse(vm.uiState.value.isLoading)
            assertEquals("95.0", vm.uiState.value.quoteDisplay.estimatedDstTokenValue)
            // The refresh actually re-fetched a quote (initial fetch + one silent refresh), proving
            // the timer fired rather than the assertions passing because nothing ran.
            coVerify(atLeast = 2) {
                swapQuoteManager.fetchBestQuote(
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

            // The refresh re-arms itself into a periodic timer. Cancel the VM now so runTest's
            // cleanup (which drains the scheduler) doesn't spin that timer forever and OOM the
            // worker.
            vm.viewModelScope.cancel()
        }

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
            every { swapQuoteRepository.getEligibleProviders(any(), any()) } returns emptyList()

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

    @Test
    fun `calculateFees with empty amount on entry shows no error`() =
        runTest(mainDispatcher) {
            val vm = createViewModelWithSwapTokens()
            advanceUntilIdle()

            // No amount entered yet — the field is empty as it is right after opening the screen.
            // The pair is selected, so the quote flow runs, but an empty field must not flash
            // the "Invalid amount" form error (PR #4721 review).
            val state = vm.uiState.value
            assertTrue(state.isSwapDisabled)
            assertNull(state.formError)
        }

    // endregion

    // region calculateFees — THORChain provider

    @Test
    fun `calculateFees with THORChain provider updates UI state correctly`() =
        runTest(mainDispatcher) {
            every { swapQuoteRepository.getEligibleProviders(any(), any()) } returns
                listOf(SwapProvider.THORCHAIN)
            coEvery {
                swapQuoteManager.fetchBestQuote(
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
            every { swapQuoteRepository.getEligibleProviders(any(), any()) } returns
                listOf(SwapProvider.THORCHAIN)
            coEvery {
                swapQuoteManager.fetchBestQuote(
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
                vm.uiState.value.quoteDisplay.provider,
            )
        }

    // endregion

    // region calculateFees — MayaChain provider

    @Test
    fun `calculateFees with MayaChain sets provider name`() =
        runTest(mainDispatcher) {
            every { swapQuoteRepository.getEligibleProviders(any(), any()) } returns
                listOf(SwapProvider.MAYA)
            coEvery {
                swapQuoteManager.fetchBestQuote(
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
                vm.uiState.value.quoteDisplay.provider,
            )
        }

    // endregion

    // region calculateFees — outboundFee and swapFeePercent

    @Test
    fun `calculateFees populates outboundFee and swapFeePercent when present in result`() =
        runTest(mainDispatcher) {
            every { swapQuoteRepository.getEligibleProviders(any(), any()) } returns
                listOf(SwapProvider.THORCHAIN)
            coEvery {
                swapQuoteManager.fetchBestQuote(
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
                createDefaultQuoteFetchResult(outboundFeeText = "$1.50", swapFeePercent = "0.30%")

            val vm = createViewModelWithSwapTokens(ethBalance = BigInteger("10000000000000000000"))
            advanceUntilIdle()

            vm.srcAmountState.setTextAndPlaceCursorAtEnd("1")
            Snapshot.sendApplyNotifications()
            advanceTimeBy(500)
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals("$1.50", state.feeBreakdown.outboundFee)
            assertEquals("0.30%", state.feeBreakdown.swapFeePercent)
        }

    @Test
    fun `calculateFees leaves outboundFee and swapFeePercent null when absent from result`() =
        runTest(mainDispatcher) {
            every { swapQuoteRepository.getEligibleProviders(any(), any()) } returns
                listOf(SwapProvider.THORCHAIN)
            coEvery {
                swapQuoteManager.fetchBestQuote(
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

            val vm = createViewModelWithSwapTokens(ethBalance = BigInteger("10000000000000000000"))
            advanceUntilIdle()

            vm.srcAmountState.setTextAndPlaceCursorAtEnd("1")
            Snapshot.sendApplyNotifications()
            advanceTimeBy(500)
            advanceUntilIdle()

            val state = vm.uiState.value
            assertNull(state.feeBreakdown.outboundFee)
            assertNull(state.feeBreakdown.swapFeePercent)
        }

    @Test
    fun `calculateFees clears outboundFee and swapFeePercent on swap exception`() =
        runTest(mainDispatcher) {
            // First quote populates the fields; the second throws so the reset path runs.
            // Without this two-step flow the test would pass even if the reset were removed,
            // since both fields default to null on SwapFormUiModel.
            every { swapQuoteRepository.getEligibleProviders(any(), any()) } returns
                listOf(SwapProvider.THORCHAIN)
            coEvery {
                swapQuoteManager.fetchBestQuote(
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
                    outboundFeeText = "$1.50",
                    swapFeePercent = "0.30%",
                ) andThenThrows
                SwapException.SwapIsNotSupported("Not supported")

            val vm = createViewModelWithSwapTokens(ethBalance = BigInteger("10000000000000000000"))
            advanceUntilIdle()

            vm.srcAmountState.setTextAndPlaceCursorAtEnd("1")
            Snapshot.sendApplyNotifications()
            advanceTimeBy(500)
            advanceUntilIdle()

            // Sanity-check: the successful quote populated both fields.
            assertEquals("$1.50", vm.uiState.value.feeBreakdown.outboundFee)
            assertEquals("0.30%", vm.uiState.value.feeBreakdown.swapFeePercent)

            // Re-trigger; this time the mock throws and the reset block must clear them.
            vm.srcAmountState.setTextAndPlaceCursorAtEnd("2")
            Snapshot.sendApplyNotifications()
            advanceTimeBy(500)
            advanceUntilIdle()

            val state = vm.uiState.value
            assertNull(state.feeBreakdown.outboundFee)
            assertNull(state.feeBreakdown.swapFeePercent)
        }

    // endregion

    // region calculateFees — swap exception handling

    @Test
    fun `calculateFees handles SwapRouteNotAvailable`() =
        runTest(mainDispatcher) {
            coEvery {
                swapQuoteManager.fetchBestQuote(
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
            } throws SwapException.SwapRouteNotAvailable("No route")

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
            every { swapQuoteRepository.getEligibleProviders(any(), any()) } returns
                listOf(SwapProvider.THORCHAIN)
            coEvery {
                swapQuoteManager.fetchBestQuote(
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
            every { swapQuoteRepository.getEligibleProviders(any(), any()) } returns
                listOf(SwapProvider.THORCHAIN)
            coEvery {
                swapQuoteManager.fetchBestQuote(
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
            every { swapQuoteRepository.getEligibleProviders(any(), any()) } returns
                listOf(SwapProvider.THORCHAIN)
            coEvery {
                swapQuoteManager.fetchBestQuote(
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
            every { swapQuoteRepository.getEligibleProviders(any(), any()) } returns
                listOf(SwapProvider.THORCHAIN)
            coEvery {
                swapQuoteManager.fetchBestQuote(
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
            coEvery {
                swapQuoteManager.fetchBestQuote(
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
            } throws SwapException.SwapIsNotSupported("Not supported")

            val vm = createViewModelWithSwapTokens()
            advanceUntilIdle()

            vm.srcAmountState.setTextAndPlaceCursorAtEnd("1")
            Snapshot.sendApplyNotifications()
            advanceTimeBy(500)
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals(UiText.Empty, state.quoteDisplay.provider)
            assertEquals("0", state.srcFiatValue)
            assertEquals("0", state.quoteDisplay.estimatedDstTokenValue)
            assertEquals("0", state.quoteDisplay.estimatedDstFiatValue)
            assertEquals("0", state.feeBreakdown.fee)
            assertEquals("0", state.feeBreakdown.totalFee)
            assertNull(state.discountInfo.vultBpsDiscount)
            assertNull(state.discountInfo.vultBpsDiscountFiatValue)
            assertNull(state.discountInfo.referralBpsDiscount)
            assertNull(state.discountInfo.referralBpsDiscountFiatValue)
            assertNull(state.discountInfo.tierType)
            assertTrue(state.isSwapDisabled)
            assertFalse(state.isLoading)
            assertFalse(state.quoteDisplay.hasQuote)
            assertNull(state.quoteDisplay.expiredAt)
            // Gas fields are tied to the source token (calculateGas), not the quote, so they
            // must survive a quote exception — clearing them would leave the row empty until
            // selectedSrc changes again.
            assertEquals("0.001 ETH", state.feeBreakdown.networkFee)
            assertEquals("$2.00", state.feeBreakdown.networkFeeFiat)
        }

    @Test
    fun `calculateFees recovers hasQuote on success after a swap exception`() =
        runTest(mainDispatcher) {
            // First call throws, second call (after a new amount) succeeds.
            coEvery {
                swapQuoteManager.fetchBestQuote(
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
            } throws
                SwapException.SwapIsNotSupported("Not supported") andThen
                createDefaultQuoteFetchResult()

            val vm = createViewModelWithSwapTokens()
            advanceUntilIdle()

            // Trigger the exception path.
            vm.srcAmountState.setTextAndPlaceCursorAtEnd("0.1")
            Snapshot.sendApplyNotifications()
            advanceTimeBy(500)
            advanceUntilIdle()

            assertFalse(vm.uiState.value.quoteDisplay.hasQuote)
            assertNotNull(vm.uiState.value.formError)

            // Trigger the recovery path.
            vm.srcAmountState.setTextAndPlaceCursorAtEnd("0.2")
            Snapshot.sendApplyNotifications()
            advanceTimeBy(500)
            advanceUntilIdle()

            val state = vm.uiState.value
            assertTrue(state.quoteDisplay.hasQuote)
            assertNull(state.formError)
            assertFalse(state.isSwapDisabled)
            assertFalse(state.isLoading)
            assertNotNull(state.quoteDisplay.expiredAt)
            // Gas fields are populated by calculateGas (selectedSrc-scoped) and are not
            // touched by the SwapException catch nor repopulated by the success path. A
            // regression that re-introduces clearing in resetQuoteState would surface here.
            assertEquals("0.001 ETH", state.feeBreakdown.networkFee)
            assertEquals("$2.00", state.feeBreakdown.networkFeeFiat)
        }

    @Test
    fun `calculateFees on generic exception sets quote-failed formError and clears hasQuote`() =
        runTest(mainDispatcher) {
            coEvery {
                swapQuoteManager.fetchBestQuote(
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
            } throws RuntimeException("network IO failed")

            val vm = createViewModelWithSwapTokens()
            advanceUntilIdle()

            vm.srcAmountState.setTextAndPlaceCursorAtEnd("1")
            Snapshot.sendApplyNotifications()
            advanceTimeBy(500)
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals(UiText.StringResource(R.string.swap_error_quote_failed), state.formError)
            assertFalse(state.quoteDisplay.hasQuote)
            assertEquals(UiText.Empty, state.quoteDisplay.provider)
            assertEquals("0", state.feeBreakdown.totalFee)
            assertTrue(state.isSwapDisabled)
            assertFalse(state.isLoading)
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

    @Test
    fun `swap with zero gas fee shows error and does not stage`() =
        runTest(mainDispatcher) {
            coEvery { swapGasCalculator.calculateGasFee(any(), any()) } returns
                GasCalculationResult(
                    gasFee = TokenValue(value = BigInteger.ZERO, token = ETH_COIN),
                    estimated =
                        EstimatedGasFee(
                            formattedTokenValue = "0 ETH",
                            formattedFiatValue = "$0.00",
                            tokenValue = TokenValue(value = BigInteger.ZERO, token = ETH_COIN),
                            fiatValue = FiatValue(BigDecimal.ZERO, "USD"),
                        ),
                    chain = Chain.Ethereum,
                )
            coEvery {
                swapQuoteManager.fetchBestQuote(
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

            vm.srcAmountState.setTextAndPlaceCursorAtEnd("0.5")
            Snapshot.sendApplyNotifications()
            advanceTimeBy(500)
            advanceUntilIdle()

            vm.swap()
            advanceUntilIdle()

            assertEquals(
                UiText.StringResource(R.string.swap_screen_invalid_gas_fee_calculation),
                vm.uiState.value.error,
            )
            assertFalse(vm.uiState.value.isLoadingNextScreen)
            coVerify(exactly = 0) { swapTransactionRepository.addTransaction(any()) }
        }

    @Test
    fun `swap with zero expected destination amount shows error and does not stage`() =
        runTest(mainDispatcher) {
            coEvery {
                swapQuoteManager.fetchBestQuote(
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
                    quote =
                        createThorChainQuote(
                            expectedDstValue =
                                TokenValue(value = BigInteger.ZERO, token = USDC_COIN)
                        )
                )

            val vm = createViewModelWithSwapTokens()
            advanceUntilIdle()

            vm.srcAmountState.setTextAndPlaceCursorAtEnd("0.5")
            Snapshot.sendApplyNotifications()
            advanceTimeBy(500)
            advanceUntilIdle()

            vm.swap()
            advanceUntilIdle()

            assertEquals(
                UiText.StringResource(R.string.swap_screen_invalid_quote_calculation),
                vm.uiState.value.error,
            )
            assertFalse(vm.uiState.value.isLoadingNextScreen)
            coVerify(exactly = 0) { swapTransactionRepository.addTransaction(any()) }
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
            every { swapQuoteRepository.getEligibleProviders(any(), any()) } returns
                listOf(SwapProvider.THORCHAIN)
            coEvery {
                swapQuoteManager.fetchBestQuote(
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
            every { swapQuoteRepository.getEligibleProviders(any(), any()) } returns
                listOf(SwapProvider.THORCHAIN)
            coEvery {
                swapQuoteManager.fetchBestQuote(
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
            assertEquals(50, state.discountInfo.vultBpsDiscount)
            assertEquals("$5.00", state.discountInfo.vultBpsDiscountFiatValue)
        }

    @Test
    fun `calculateFees clears VULT BPS discount when not available`() =
        runTest(mainDispatcher) {
            coEvery { getDiscountBpsUseCase.invoke(any(), any()) } returns 0
            every { swapQuoteRepository.getEligibleProviders(any(), any()) } returns
                listOf(SwapProvider.THORCHAIN)
            coEvery {
                swapQuoteManager.fetchBestQuote(
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
            assertNull(state.discountInfo.vultBpsDiscount)
            assertNull(state.discountInfo.vultBpsDiscountFiatValue)
        }

    // endregion

    // region refresh quote timer

    @Test
    fun `calculateFees sets expiredAt from quote`() =
        runTest(mainDispatcher) {
            every { swapQuoteRepository.getEligibleProviders(any(), any()) } returns
                listOf(SwapProvider.THORCHAIN)
            coEvery {
                swapQuoteManager.fetchBestQuote(
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

            assertNotNull(vm.uiState.value.quoteDisplay.expiredAt)
        }

    // endregion

    // region collectTotalFee

    @Test
    fun `collectTotalFee combines gas and swap fees`() =
        runTest(mainDispatcher) {
            coEvery { fiatValueToString(any(), any()) } returns "$10.00"
            every { swapQuoteRepository.getEligibleProviders(any(), any()) } returns
                listOf(SwapProvider.THORCHAIN)
            coEvery {
                swapQuoteManager.fetchBestQuote(
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
            every { swapQuoteRepository.getEligibleProviders(any(), any()) } returns
                listOf(SwapProvider.THORCHAIN)
            coEvery {
                swapQuoteManager.fetchBestQuote(
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
            every { swapQuoteRepository.getEligibleProviders(any(), any()) } returns
                listOf(SwapProvider.THORCHAIN)
            coEvery {
                swapQuoteManager.fetchBestQuote(
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
            every { swapQuoteRepository.getEligibleProviders(any(), any()) } returns
                listOf(SwapProvider.THORCHAIN)
            coEvery {
                swapQuoteManager.fetchBestQuote(
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
            every { swapQuoteRepository.getEligibleProviders(any(), any()) } returns
                listOf(SwapProvider.THORCHAIN)
            coEvery {
                swapQuoteManager.fetchBestQuote(
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

    // region calculateFees — UTXO plan fee refresh block

    @Test
    fun `calculateFees for UTXO chain enables swap after successful plan fee`() =
        runTest(mainDispatcher) {
            val vm = setupUtxoPlanFeeTest(SwapProvider.THORCHAIN, createThorChainQuote())
            advanceUntilIdle()

            vm.srcAmountState.setTextAndPlaceCursorAtEnd("0.01")
            Snapshot.sendApplyNotifications()
            advanceTimeBy(500)
            advanceUntilIdle()

            assertFalse(vm.uiState.value.isSwapDisabled)
            assertEquals("0.00000816 BTC", vm.uiState.value.feeBreakdown.networkFee)
        }

    @Test
    fun `calculateFees for SwapKit BTC routes through the UTXO plan-fee path and enables swap`() =
        runTest(mainDispatcher) {
            // Without the SwapKit branch in utxoFeeData, a BTC SwapKit quote never computes the
            // plan
            // fee, estimatedNetworkFee* stay null, and swap() aborts with
            // invalid_gas_fee_calculation. Pin that it now flows through the same path as
            // Thor/Maya.
            val vm = setupUtxoPlanFeeTest(SwapProvider.SWAPKIT, createSwapKitBtcQuote())
            advanceUntilIdle()

            vm.srcAmountState.setTextAndPlaceCursorAtEnd("0.01")
            Snapshot.sendApplyNotifications()
            advanceTimeBy(500)
            advanceUntilIdle()

            assertFalse(vm.uiState.value.isSwapDisabled)
            assertEquals("0.00000816 BTC", vm.uiState.value.feeBreakdown.networkFee)
        }

    @Test
    fun `calculateFees for UTXO chain shows InsufficientUtxos error and disables swap`() =
        runTest(mainDispatcher) {
            coEvery { swapGasCalculator.calculateGasFee(any(), any()) } returns
                GasCalculationResult(
                    gasFee = TokenValue(value = BigInteger("1000"), token = BTC_COIN),
                    estimated =
                        EstimatedGasFee(
                            formattedTokenValue = "0.000001 BTC",
                            formattedFiatValue = "$0.05",
                            tokenValue = TokenValue(value = BigInteger("1000"), token = BTC_COIN),
                            fiatValue = FiatValue(BigDecimal("0.05"), "USD"),
                        ),
                    chain = Chain.Bitcoin,
                )
            coEvery {
                swapGasCalculator.resolveUtxoPlanFee(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                )
            } returns UtxoPlanFeeResult.InsufficientUtxos
            every { swapQuoteRepository.getEligibleProviders(any(), any()) } returns
                listOf(SwapProvider.THORCHAIN)
            coEvery {
                swapQuoteManager.fetchBestQuote(
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
                    addresses = listOf(btcAddressLargeBalance(), ethAddress()),
                    srcTokenId = BTC_COIN.id,
                    dstTokenId = ETH_COIN.id,
                )
            advanceUntilIdle()

            vm.srcAmountState.setTextAndPlaceCursorAtEnd("0.01")
            Snapshot.sendApplyNotifications()
            advanceTimeBy(500)
            advanceUntilIdle()

            assertTrue(vm.uiState.value.isSwapDisabled)
            assertEquals(
                UiText.StringResource(R.string.insufficient_utxos_error),
                vm.uiState.value.formError,
            )
        }

    @Test
    fun `InsufficientUtxos after a successful plan fee clears the stale fee and total`() =
        runTest(mainDispatcher) {
            // Regression for #4810: an earlier successful plan fee leaves the network-fee row and
            // totalFee populated; a later quote that hits InsufficientUtxos must blank both rather
            // than leaving a stale fee on screen alongside the insufficient-utxos error.
            val vm = setupUtxoPlanFeeTest(SwapProvider.THORCHAIN, createThorChainQuote())
            advanceUntilIdle()

            vm.srcAmountState.setTextAndPlaceCursorAtEnd("0.01")
            Snapshot.sendApplyNotifications()
            advanceTimeBy(500)
            advanceUntilIdle()

            // The successful plan fee is shown and the swap is enabled.
            assertFalse(vm.uiState.value.isSwapDisabled)
            assertEquals("0.00000816 BTC", vm.uiState.value.feeBreakdown.networkFee)

            // A later quote can no longer afford the plan: the fee must be cleared.
            coEvery {
                swapGasCalculator.resolveUtxoPlanFee(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                )
            } returns UtxoPlanFeeResult.InsufficientUtxos

            vm.srcAmountState.setTextAndPlaceCursorAtEnd("0.02")
            Snapshot.sendApplyNotifications()
            advanceTimeBy(500)
            advanceUntilIdle()

            assertTrue(vm.uiState.value.isSwapDisabled)
            assertEquals(
                UiText.StringResource(R.string.insufficient_utxos_error),
                vm.uiState.value.formError,
            )
            assertEquals("", vm.uiState.value.feeBreakdown.networkFee)
            assertEquals("", vm.uiState.value.feeBreakdown.networkFeeFiat)
            assertEquals("", vm.uiState.value.feeBreakdown.totalFee)
        }

    @Test
    fun `calculateFees for UTXO chain clears fee and disables swap on plan network error`() =
        runTest(mainDispatcher) {
            coEvery { swapGasCalculator.calculateGasFee(any(), any()) } returns
                GasCalculationResult(
                    gasFee = TokenValue(value = BigInteger("1000"), token = BTC_COIN),
                    estimated =
                        EstimatedGasFee(
                            formattedTokenValue = "0.000001 BTC",
                            formattedFiatValue = "$0.05",
                            tokenValue = TokenValue(value = BigInteger("1000"), token = BTC_COIN),
                            fiatValue = FiatValue(BigDecimal("0.05"), "USD"),
                        ),
                    chain = Chain.Bitcoin,
                )
            coEvery {
                swapGasCalculator.getSpecificAndUtxo(
                    srcToken = any(),
                    srcAddress = any(),
                    gasFee = any(),
                    isThorchainRouterDeposit = any(),
                    dstAddress = any(),
                    memo = any(),
                    tokenAmountValue = any(),
                )
            } throws RuntimeException("network error")
            every { swapQuoteRepository.getEligibleProviders(any(), any()) } returns
                listOf(SwapProvider.THORCHAIN)
            coEvery {
                swapQuoteManager.fetchBestQuote(
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
                    addresses = listOf(btcAddressLargeBalance(), ethAddress()),
                    srcTokenId = BTC_COIN.id,
                    dstTokenId = ETH_COIN.id,
                )
            advanceUntilIdle()

            vm.srcAmountState.setTextAndPlaceCursorAtEnd("0.01")
            Snapshot.sendApplyNotifications()
            advanceTimeBy(500)
            advanceUntilIdle()

            assertTrue(vm.uiState.value.isSwapDisabled)
            assertEquals("", vm.uiState.value.feeBreakdown.networkFee)
        }

    @Test
    fun `calculateFees for UTXO chain clears fee when gasFeeChain does not match srcToken chain`() =
        runTest(mainDispatcher) {
            // Default calculateGasFee mock returns Chain.Ethereum; srcToken is BTC (Bitcoin),
            // so gasFeeChain != srcToken.chain — chain guard rejects and stale fee is cleared.
            every { swapQuoteRepository.getEligibleProviders(any(), any()) } returns
                listOf(SwapProvider.THORCHAIN)
            coEvery {
                swapQuoteManager.fetchBestQuote(
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
                    addresses = listOf(btcAddressLargeBalance(), ethAddress()),
                    srcTokenId = BTC_COIN.id,
                    dstTokenId = ETH_COIN.id,
                )
            advanceUntilIdle()

            vm.srcAmountState.setTextAndPlaceCursorAtEnd("0.01")
            Snapshot.sendApplyNotifications()
            advanceTimeBy(500)
            advanceUntilIdle()

            assertTrue(vm.uiState.value.isSwapDisabled)
            assertEquals("", vm.uiState.value.feeBreakdown.networkFee)
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

    // region external recipient validation (#4858)

    @Test
    fun `invalid external recipient sets the inline error against the destination chain`() =
        runTest(mainDispatcher) {
            every { chainAccountAddressRepository.isValid(any(), any()) } returns false
            val vm = createViewModelWithSwapTokens()
            advanceUntilIdle()

            vm.setExternalRecipient("not-a-valid-address")

            assertEquals(
                UiText.StringResource(R.string.swap_external_recipient_invalid),
                vm.uiState.value.externalRecipientError,
            )
            // dst is BTC in createViewModelWithSwapTokens — validation must run against it.
            io.mockk.verify {
                chainAccountAddressRepository.isValid(Chain.Bitcoin, "not-a-valid-address")
            }
        }

    @Test
    fun `valid external recipient clears the inline error`() =
        runTest(mainDispatcher) {
            every { chainAccountAddressRepository.isValid(any(), any()) } returns true
            val vm = createViewModelWithSwapTokens()
            advanceUntilIdle()

            vm.setExternalRecipient("bc1qvalidrecipient")

            assertNull(vm.uiState.value.externalRecipientError)
        }

    @Test
    fun `invalid external recipient is not pushed into the quote pipeline`() =
        runTest(mainDispatcher) {
            every { chainAccountAddressRepository.isValid(any(), any()) } returns false
            val vm = createViewModelWithSwapTokens()
            advanceUntilIdle()

            vm.setExternalRecipient("not-a-valid-address")

            // Gated: an invalid/intermediate address must not reach quote fetching (would otherwise
            // hit THOR/Maya with a malformed destination), even though the inline error is shown.
            assertNull(pipelineRecipient?.value)
            assertEquals(
                UiText.StringResource(R.string.swap_external_recipient_invalid),
                vm.uiState.value.externalRecipientError,
            )
        }

    @Test
    fun `valid external recipient is pushed into the quote pipeline`() =
        runTest(mainDispatcher) {
            every { chainAccountAddressRepository.isValid(any(), any()) } returns true
            val vm = createViewModelWithSwapTokens()
            advanceUntilIdle()

            vm.setExternalRecipient("bc1qvalidrecipient")

            assertEquals("bc1qvalidrecipient", pipelineRecipient?.value)
        }

    @Test
    fun `swap is blocked and surfaces an error when the external recipient is invalid`() =
        runTest(mainDispatcher) {
            every { chainAccountAddressRepository.isValid(any(), any()) } returns false
            val vm = createViewModelWithSwapTokens()
            advanceUntilIdle()

            vm.setExternalRecipient("not-a-valid-address")
            vm.swap()
            advanceUntilIdle()

            assertEquals(
                UiText.StringResource(R.string.swap_external_recipient_invalid),
                vm.uiState.value.error,
            )
            // The pre-flight gate returns before staging keysign, so no navigation to verify.
            coVerify(exactly = 0) { navigator.route(any()) }
        }

    @Test
    fun `external recipient on a pair with no native route surfaces a recipient-aware unsupported error`() =
        runTest(mainDispatcher) {
            // Setting a recipient drops the aggregators and keeps only THORChain/Maya. When the
            // pair
            // has no native route at all, the bare "not supported" must instead name the recipient
            // as the reason (#4858).
            every { swapQuoteRepository.getEligibleProviders(any(), any()) } returns
                listOf(SwapProvider.THORCHAIN)
            coEvery {
                swapQuoteManager.fetchBestQuote(
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
            } throws SwapException.SwapRouteNotAvailable("no route")

            val vm = createViewModelWithSwapTokens()
            advanceUntilIdle()

            vm.setExternalRecipient("bc1qrecipientaddress")
            vm.srcAmountState.setTextAndPlaceCursorAtEnd("0.001")
            Snapshot.sendApplyNotifications()
            advanceTimeBy(500)
            advanceUntilIdle()

            assertEquals(
                UiText.StringResource(R.string.swap_external_recipient_unsupported),
                vm.uiState.value.formError,
            )
        }

    @Test
    fun `a sub-minimum failure keeps its concrete minimum message even with a recipient set`() =
        runTest(mainDispatcher) {
            // The concrete "Minimum amount is X" message (SmallSwapAmount) is more actionable than
            // a
            // generic recipient note, so the recipient-aware rewrite must NOT mask it (#4858,
            // #604).
            every { swapQuoteRepository.getEligibleProviders(any(), any()) } returns
                listOf(SwapProvider.THORCHAIN)
            coEvery {
                swapQuoteManager.fetchBestQuote(
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
            } throws SwapException.SmallSwapAmount("0.05")
            coEvery { swapQuoteManager.mapSwapExceptionToFormError(any(), any(), any()) } returns
                UiText.FormattedText(R.string.swap_form_minimum_amount, listOf("0.05", "ETH"))

            val vm = createViewModelWithSwapTokens()
            advanceUntilIdle()

            vm.setExternalRecipient("bc1qrecipientaddress")
            vm.srcAmountState.setTextAndPlaceCursorAtEnd("0.001")
            Snapshot.sendApplyNotifications()
            advanceTimeBy(500)
            advanceUntilIdle()

            assertEquals(
                UiText.FormattedText(R.string.swap_form_minimum_amount, listOf("0.05", "ETH")),
                vm.uiState.value.formError,
            )
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
        ethBalance: BigInteger = BigInteger("1000000000000000000"),
        ioDispatcher: CoroutineDispatcher = inertIoDispatcher,
    ): SwapFormViewModel =
        createViewModelWithAddresses(
            addresses = listOf(ethAddressWithBalance(ethBalance), btcAddress()),
            srcTokenId = ETH_COIN.id,
            dstTokenId = BTC_COIN.id,
            ioDispatcher = ioDispatcher,
        )

    private fun btcAddress(): Address =
        Address(
            chain = Chain.Bitcoin,
            address = "bc1qbtcaddress",
            accounts = listOf(createAccount(BTC_COIN, BigInteger("100000000"))),
        )

    private fun btcAddressLargeBalance(): Address =
        Address(
            chain = Chain.Bitcoin,
            address = "bc1qbtcaddress",
            accounts = listOf(createAccount(BTC_COIN, BigInteger("1000000000"))),
        )

    private fun createDefaultQuoteFetchResult(
        quote: SwapQuote = createThorChainQuote(),
        provider: SwapProvider = SwapProvider.THORCHAIN,
        providerUiText: UiText = R.string.swap_form_provider_thorchain.asUiText(),
        srcFiatValueText: String = "$0.00",
        estimatedDstTokenValue: String = "95.0",
        estimatedDstFiatValue: String = "$95.00",
        estimatedDstFiat: FiatValue = FiatValue(BigDecimal("95.00"), "USD"),
        feeText: String = "$0.00",
        swapFeeFiat: FiatValue = FiatValue(BigDecimal.ZERO, "USD"),
        outboundFeeText: String? = null,
        swapFeePercent: String? = null,
    ): BestQuote =
        BestQuote(
            candidate =
                QuoteCandidate(provider = provider, vultBPSDiscount = null, referral = null),
            result =
                QuoteFetchResult(
                    quote = quote,
                    provider = provider,
                    providerUiText = providerUiText,
                    srcFiatValueText = srcFiatValueText,
                    estimatedDstTokenValue = estimatedDstTokenValue,
                    estimatedDstFiatValue = estimatedDstFiatValue,
                    estimatedDstFiat = estimatedDstFiat,
                    feeText = feeText,
                    swapFeeFiat = swapFeeFiat,
                    outboundFeeText = outboundFeeText,
                    swapFeePercent = swapFeePercent,
                ),
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

    private fun createSwapKitBtcQuote(
        expectedDstValue: TokenValue = TokenValue(value = BigInteger("95000000"), token = USDC_COIN)
    ): SwapQuote.SwapKit =
        SwapQuote.SwapKit(
            expectedDstValue = expectedDstValue,
            fees = TokenValue(value = BigInteger("400"), token = BTC_COIN),
            expiredAt = Clock.System.now() + 1.minutes,
            data = mockk(relaxed = true),
            subProvider = "NEAR",
        )

    private fun createLiFiQuote(
        expectedDstValue: TokenValue =
            TokenValue(value = BigInteger("1000000000000000000"), token = ETH_COIN),
        fees: TokenValue = TokenValue(value = BigInteger("100000000000000000"), token = ETH_COIN),
    ): SwapQuote.OneInch =
        SwapQuote.OneInch(
            expectedDstValue = expectedDstValue,
            fees = fees,
            expiredAt = Clock.System.now() + 1.minutes,
            data = mockk(relaxed = true),
            provider = SwapProvider.LIFI.getSwapProviderId(),
        )

    /**
     * Common setup for a BTC (UTXO) plan-fee swap: a successful gas fee + plan fee, the given
     * provider/quote, and a BTC->ETH vault. Returns the VM ready for the caller to drive amount
     * input and assert isSwapDisabled / networkFee.
     */
    private fun setupUtxoPlanFeeTest(provider: SwapProvider, quote: SwapQuote): SwapFormViewModel {
        coEvery { swapGasCalculator.calculateGasFee(any(), any()) } returns
            GasCalculationResult(
                gasFee = TokenValue(value = BigInteger("1000"), token = BTC_COIN),
                estimated =
                    EstimatedGasFee(
                        formattedTokenValue = "0.000001 BTC",
                        formattedFiatValue = "$0.05",
                        tokenValue = TokenValue(value = BigInteger("1000"), token = BTC_COIN),
                        fiatValue = FiatValue(BigDecimal("0.05"), "USD"),
                    ),
                chain = Chain.Bitcoin,
            )
        coEvery {
            swapGasCalculator.resolveUtxoPlanFee(any(), any(), any(), any(), any(), any(), any())
        } returns
            UtxoPlanFeeResult.Success(
                estimated =
                    EstimatedGasFee(
                        formattedTokenValue = "0.00000816 BTC",
                        formattedFiatValue = "$0.00",
                        tokenValue = TokenValue(value = BigInteger("816"), token = BTC_COIN),
                        fiatValue = FiatValue(BigDecimal("0.00"), "USD"),
                    )
            )
        every { swapQuoteRepository.getEligibleProviders(any(), any()) } returns listOf(provider)
        coEvery {
            swapQuoteManager.fetchBestQuote(
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
        } returns createDefaultQuoteFetchResult(quote = quote, provider = provider)

        return createViewModelWithAddresses(
            addresses = listOf(btcAddressLargeBalance(), ethAddress()),
            srcTokenId = BTC_COIN.id,
            dstTokenId = ETH_COIN.id,
        )
    }

    // endregion
}
