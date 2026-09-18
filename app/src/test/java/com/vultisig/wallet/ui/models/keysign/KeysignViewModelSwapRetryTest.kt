@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.keysign

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.SwapTransactionHistoryData
import com.vultisig.wallet.data.models.TransactionHistoryData
import com.vultisig.wallet.data.models.TssKeyType
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.NavigationOptions
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * The done screen's Try again is resolved from the history row the keysign records — the initiator
 * builds it from the transaction it staged, the co-signer from the payload it signs — so both
 * devices offer the same retry, and it is the same retry History offers later (#5918).
 */
internal class KeysignViewModelSwapRetryTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var navigator: Navigator<Destination>

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        navigator = mockk(relaxed = true)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a market swap the vault holds on both sides can be tried again`() {
        val vm = createViewModel(transactionHistoryData = swapRow())

        val retry = vm.swapRetry.shouldNotBeNull()
        retry.srcToken shouldBe rune
        retry.dstToken shouldBe eth
        // Raw and trimmed, as the form's field takes it — not the abbreviated display amount.
        retry.srcAmount shouldBe "1.5"
        retry.externalRecipient.shouldBeNull()
    }

    @Test
    fun `an output routed to a chosen address is routed there again`() {
        val vm = createViewModel(transactionHistoryData = swapRow(externalRecipient = "0xelse"))

        vm.swapRetry.shouldNotBeNull().externalRecipient shouldBe "0xelse"
    }

    @Test
    fun `a limit order offers no retry`() {
        createViewModel(transactionHistoryData = swapRow(isLimitOrder = true))
            .swapRetry
            .shouldBeNull()
    }

    @Test
    fun `a dApp-driven swap offers no retry`() {
        createViewModel(transactionHistoryData = swapRow(isDappRequest = true))
            .swapRetry
            .shouldBeNull()
    }

    @Test
    fun `a route whose recipient this device could not read offers no retry`() {
        createViewModel(transactionHistoryData = swapRow(isRecipientUnknown = true))
            .swapRetry
            .shouldBeNull()
    }

    @Test
    fun `a pair the vault no longer holds on both sides offers no retry`() {
        createViewModel(transactionHistoryData = swapRow(), heldCoins = listOf(rune))
            .swapRetry
            .shouldBeNull()
    }

    @Test
    fun `a send has nothing to retry`() {
        createViewModel(transactionHistoryData = null).swapRetry.shouldBeNull()
    }

    @Test
    fun `retrySwap reopens the form on the failed trade above Home`() =
        runTest(testDispatcher) {
            val vm = createViewModel(transactionHistoryData = swapRow(externalRecipient = "0xelse"))

            vm.retrySwap()

            coVerify(exactly = 1) {
                navigator.route(
                    Route.Swap(
                        vaultId = "v1",
                        chainId = Chain.ThorChain.id,
                        srcTokenId = rune.id,
                        dstTokenId = eth.id,
                        srcAmount = "1.5",
                        externalRecipient = "0xelse",
                        verifyOnQuote = true,
                    ),
                    NavigationOptions(popUpToRoute = Route.Home::class),
                )
            }
        }

    private fun createViewModel(
        transactionHistoryData: TransactionHistoryData?,
        heldCoins: List<Coin> = listOf(rune, eth),
    ) =
        KeysignViewModel(
            vault = Vault(id = "v1", name = "Test Vault").apply { coins = heldCoins },
            keysignCommittee = emptyList(),
            serverUrl = "",
            sessionId = "",
            encryptionKeyHex = "",
            messagesToSign = emptyList(),
            keyType = TssKeyType.ECDSA,
            keysignPayload = null,
            customMessagePayload = null,
            transactionTypeUiModel = null,
            isInitiatingDevice = false,
            transactionHistoryData = transactionHistoryData,
            thorChainApi = mockk(relaxed = true),
            evmApiFactory = mockk(relaxed = true),
            broadcastTx = mockk(relaxed = true),
            explorerLinkRepository = mockk(relaxed = true),
            navigator = navigator,
            sessionApi = mockk(relaxed = true),
            encryption = mockk(relaxed = true),
            featureFlagApi = mockk(relaxed = true),
            pullTssMessages = mockk(relaxed = true),
            addressBookRepository = mockk(relaxed = true),
            txStatusConfigurationProvider = mockk(relaxed = true),
            txStatusPoller = mockk(relaxed = true),
            vaultRepository = mockk(relaxed = true),
            chainAccountAddressRepository = mockk(relaxed = true),
            transactionHistoryRepository = mockk(relaxed = true),
            balanceRepository = mockk(relaxed = true),
            inAppReviewRepository = mockk(relaxed = true),
            gasFeeToEstimatedFee = mockk(relaxed = true),
            pendingLimitOrderRepository = mockk(relaxed = true),
            utxoInFlightRepository = mockk(relaxed = true),
            doneTransactionPresentation = mockk(relaxed = true),
            ioDispatcher = testDispatcher,
            awaitApprovalConfirmation = mockk(relaxed = true),
        )

    private fun swapRow(
        isLimitOrder: Boolean = false,
        isDappRequest: Boolean = false,
        externalRecipient: String? = null,
        isRecipientUnknown: Boolean = false,
    ) =
        SwapTransactionHistoryData(
            fromToken = rune.ticker,
            fromAmount = "1.5",
            fromChain = rune.chain.id,
            fromTokenLogo = "",
            toToken = eth.ticker,
            toAmount = "0.001",
            toChain = eth.chain.id,
            toTokenLogo = "",
            provider = "THORChain",
            fiatValue = "$3",
            toContractAddress = "",
            toIsNative = true,
            isLimitOrder = isLimitOrder,
            fromContractAddress = "",
            fromAmountDecimal = "1.5",
            isDappRequest = isDappRequest,
            externalRecipient = externalRecipient,
            isRecipientUnknown = isRecipientUnknown,
        )

    private val rune =
        Coin(
            chain = Chain.ThorChain,
            ticker = "RUNE",
            logo = "",
            address = "thorsrc",
            decimal = 8,
            hexPublicKey = "pub",
            priceProviderID = "thorchain",
            contractAddress = "",
            isNativeToken = true,
        )

    private val eth =
        Coin(
            chain = Chain.Ethereum,
            ticker = "ETH",
            logo = "",
            address = "0xdst",
            decimal = 18,
            hexPublicKey = "pub",
            priceProviderID = "ethereum",
            contractAddress = "",
            isNativeToken = true,
        )
}
