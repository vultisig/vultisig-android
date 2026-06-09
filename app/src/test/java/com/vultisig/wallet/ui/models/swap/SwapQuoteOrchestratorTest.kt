@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.swap

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.settings.AppCurrency
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.data.repositories.ReferralCodeSettingsRepository
import com.vultisig.wallet.data.repositories.SwapQuoteRepository
import com.vultisig.wallet.data.usecases.ConvertTokenAndValueToTokenValueUseCase
import com.vultisig.wallet.data.usecases.GetDiscountBpsUseCase
import com.vultisig.wallet.ui.models.mappers.FiatValueToStringMapper
import com.vultisig.wallet.ui.models.send.SendSrc
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.textAsFlow
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Focused unit tests for the [SwapQuoteOrchestrator] collaborator extracted from
 * `SwapFormViewModel.calculateFees()` (#4735). The full success/UTXO/discount matrix is already
 * covered end-to-end through `SwapFormViewModelTest`, which now drives this orchestrator; these
 * tests assert the orchestrator's behaviour in isolation behind its own [SwapQuoteContext].
 */
internal class SwapQuoteOrchestratorTest {

    private val scheduler = TestCoroutineScheduler()
    private val mainDispatcher = UnconfinedTestDispatcher(scheduler)
    // Inert: its scheduler is never advanced, so the refresh-quote timer's delay never fires.
    private val inertIoDispatcher = StandardTestDispatcher()

    private lateinit var pipelineScope: CoroutineScope

    private lateinit var swapQuoteRepository: SwapQuoteRepository
    private lateinit var appCurrencyRepository: AppCurrencyRepository
    private lateinit var getDiscountBpsUseCase: GetDiscountBpsUseCase
    private lateinit var referralRepository: ReferralCodeSettingsRepository
    private lateinit var convertTokenAndValueToTokenValue: ConvertTokenAndValueToTokenValueUseCase
    private lateinit var swapDiscountChecker: SwapDiscountChecker
    private lateinit var swapGasCalculator: SwapGasCalculator
    private lateinit var fiatValueToString: FiatValueToStringMapper
    private lateinit var swapQuoteManager: SwapQuoteManager

    private val srcAmountState = TextFieldState()
    private val uiState = MutableStateFlow(SwapFormUiModel())
    private val quoteState = QuoteStateHolder()
    private val swapFeeFiat = MutableStateFlow<FiatValue?>(null)
    private val estimatedNetworkFeeTokenValue = MutableStateFlow<TokenValue?>(null)
    private val estimatedNetworkFeeFiatValue = MutableStateFlow<FiatValue?>(null)
    private val gasFee = MutableStateFlow<TokenValue?>(null)
    private val gasFeeChain = MutableStateFlow<Chain?>(null)
    private val refreshQuoteState = MutableStateFlow(0)
    private val selectedSrc = MutableStateFlow<SendSrc?>(null)
    private val selectedDst = MutableStateFlow<SendSrc?>(null)

    @BeforeEach
    fun setUp() {
        pipelineScope = CoroutineScope(mainDispatcher)

        swapQuoteRepository = mockk(relaxed = true)
        appCurrencyRepository = mockk(relaxed = true)
        every { appCurrencyRepository.currency } returns flowOf(AppCurrency.USD)
        getDiscountBpsUseCase = mockk(relaxed = true)
        referralRepository = mockk(relaxed = true)
        convertTokenAndValueToTokenValue = mockk(relaxed = true)
        every { convertTokenAndValueToTokenValue(any(), any()) } answers
            {
                TokenValue(value = secondArg(), token = firstArg())
            }
        swapDiscountChecker = mockk(relaxed = true)
        coEvery { swapDiscountChecker.checkVultBpsDiscount(any(), any(), any()) } returns
            VultDiscountResult(null, null, null)
        coEvery { swapDiscountChecker.checkReferralBpsDiscount(any(), any(), any(), any()) } returns
            ReferralDiscountResult(null, null, null)
        swapGasCalculator = mockk(relaxed = true)
        fiatValueToString = mockk(relaxed = true)
        coEvery { fiatValueToString(any(), any()) } returns "$0.00"

        // Real manager: the orchestrator delegates amountChanges/debounce/quote-resolution and
        // error mapping to it, and the failure-mapping test asserts on its real
        // mapSwapExceptionToFormError output. Its network-touching collaborators stay mocked.
        swapQuoteManager =
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
    }

    @AfterEach
    fun tearDown() {
        pipelineScope.cancel()
    }

    private fun createOrchestrator() =
        SwapQuoteOrchestrator(
            swapQuoteRepository = swapQuoteRepository,
            appCurrencyRepository = appCurrencyRepository,
            getDiscountBpsUseCase = getDiscountBpsUseCase,
            referralRepository = referralRepository,
            convertTokenAndValueToTokenValue = convertTokenAndValueToTokenValue,
            swapDiscountChecker = swapDiscountChecker,
            swapGasCalculator = swapGasCalculator,
            swapValidator = SwapValidator(),
            fiatValueToString = fiatValueToString,
            ioDispatcher = inertIoDispatcher,
        )

    private fun context() =
        SwapQuoteContext(
            uiState = uiState,
            quoteState = quoteState,
            swapFeeFiat = swapFeeFiat,
            estimatedNetworkFeeTokenValue = estimatedNetworkFeeTokenValue,
            estimatedNetworkFeeFiatValue = estimatedNetworkFeeFiatValue,
            gasFee = gasFee,
            gasFeeChain = gasFeeChain,
            refreshQuoteState = refreshQuoteState,
            selectedSrc = selectedSrc,
            selectedDst = selectedDst,
            srcAmountTextFlow = srcAmountState.textAsFlow(),
            swapQuoteManager = swapQuoteManager,
            srcAmount = { srcAmountState.text.toString().toBigDecimalOrNull() },
            isSrcAmountEmpty = { srcAmountState.text.isEmpty() },
            vaultId = { TEST_VAULT_ID },
            selectedSrcTokenTitle = { null },
        )

    @Test
    fun `resetQuoteState clears cached quote, swap fee, and resets the UI to pristine`() =
        runTest(mainDispatcher) {
            val orchestrator = createOrchestrator()
            orchestrator.start(pipelineScope, context())

            // Seed non-pristine state as a successful quote would have.
            quoteState.quote = mockk(relaxed = true)
            quoteState.provider = com.vultisig.wallet.data.models.SwapProvider.THORCHAIN
            swapFeeFiat.value = FiatValue(BigDecimal("1.23"), "USD")
            uiState.value =
                uiState.value.copy(
                    srcFiatValue = "$100.00",
                    isSwapDisabled = false,
                    isLoading = true,
                    formError = UiText.DynamicString("stale"),
                )

            orchestrator.resetQuoteState()

            assertNull(quoteState.quote)
            assertNull(quoteState.provider)
            assertNull(swapFeeFiat.value)
            val state = uiState.value
            assertEquals("0", state.srcFiatValue)
            assertTrue(state.isSwapDisabled)
            assertEquals(false, state.isLoading)
            assertNull(state.formError)
            assertEquals("0", state.feeBreakdown.fee)
            assertEquals("0", state.feeBreakdown.totalFee)
            assertEquals(false, state.quoteDisplay.hasQuote)
        }

    @Test
    fun `unsupported pair surfaces the route-not-available form error and disables swap`() =
        runTest(mainDispatcher) {
            // No eligible provider for the pair -> the pipeline throws SwapIsNotSupported, which
            // the
            // orchestrator maps via the real SwapQuoteManager and writes as the form error.
            every { swapQuoteRepository.getEligibleProviders(any(), any()) } returns emptyList()

            val orchestrator = createOrchestrator()
            orchestrator.start(pipelineScope, context())

            selectedSrc.value = sendSrc(ETH_COIN)
            selectedDst.value = sendSrc(USDC_COIN)
            advanceUntilIdle()

            srcAmountState.setTextAndPlaceCursorAtEnd("1")
            Snapshot.sendApplyNotifications()
            advanceTimeBy(500)
            advanceUntilIdle()

            val state = uiState.value
            assertEquals(UiText.StringResource(R.string.swap_route_not_available), state.formError)
            assertTrue(state.isSwapDisabled)
            assertEquals(false, state.isLoading)
            assertNull(quoteState.quote)
        }

    private fun sendSrc(coin: Coin): SendSrc {
        val account =
            Account(
                token = coin,
                tokenValue = TokenValue(value = BigInteger("1000000000000000000"), token = coin),
                fiatValue = null,
                price = null,
            )
        val address =
            Address(chain = coin.chain, address = coin.address, accounts = listOf(account))
        return SendSrc(address = address, account = account)
    }

    companion object {
        private const val TEST_VAULT_ID = "test-vault-id"

        private val ETH_COIN =
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

        private val USDC_COIN =
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
    }
}
