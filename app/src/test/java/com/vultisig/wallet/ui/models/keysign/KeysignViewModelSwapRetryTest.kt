@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.keysign

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.THORChainSwapPayload
import com.vultisig.wallet.data.models.TssKeyType
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.DAppMetadata
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.models.payload.SwapPayload
import com.vultisig.wallet.ui.models.swap.SwapTransactionUiModel
import com.vultisig.wallet.ui.models.swap.ValuedToken
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.NavigationOptions
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coVerify
import io.mockk.mockk
import java.math.BigDecimal
import java.math.BigInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import vultisig.keysign.v1.TransactionType

/**
 * The done screen's Try again is derived from the signed payload, so both the initiator and the
 * co-signer — which rebuilds its UI model from that same payload — offer the same retry (#5918).
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
        val vm = createViewModel(keysignPayload = swapPayload())

        val retry = vm.swapRetry.shouldNotBeNull()
        retry.srcToken shouldBe rune
        retry.dstToken shouldBe eth
        // Raw and trimmed, as the form's field takes it — not the abbreviated display amount.
        retry.srcAmount shouldBe "1.5"
    }

    @Test
    fun `a limit order offers no retry`() {
        val vm =
            createViewModel(
                keysignPayload = swapPayload(),
                transactionTypeUiModel = swapUiModel(isLimitOrder = true),
            )

        vm.swapRetry.shouldBeNull()
    }

    @Test
    fun `a dApp-driven swap offers no retry`() {
        val payload =
            swapPayload().copy(dappMetadata = DAppMetadata("dapp", "https://dapp.example", ""))

        createViewModel(keysignPayload = payload).swapRetry.shouldBeNull()
    }

    @Test
    fun `a pair the vault no longer holds on both sides offers no retry`() {
        createViewModel(keysignPayload = swapPayload(), heldCoins = listOf(rune))
            .swapRetry
            .shouldBeNull()
    }

    @Test
    fun `a send has nothing to retry`() {
        createViewModel(keysignPayload = null).swapRetry.shouldBeNull()
    }

    @Test
    fun `retrySwap reopens the form on the failed trade above Home`() =
        runTest(testDispatcher) {
            val vm = createViewModel(keysignPayload = swapPayload())

            vm.retrySwap()

            coVerify(exactly = 1) {
                navigator.route(
                    Route.Swap(
                        vaultId = "v1",
                        chainId = Chain.ThorChain.id,
                        srcTokenId = rune.id,
                        dstTokenId = eth.id,
                        srcAmount = "1.5",
                        verifyOnQuote = true,
                    ),
                    NavigationOptions(popUpToRoute = Route.Home::class),
                )
            }
        }

    private fun createViewModel(
        keysignPayload: KeysignPayload?,
        transactionTypeUiModel: TransactionTypeUiModel = swapUiModel(isLimitOrder = false),
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
            keysignPayload = keysignPayload,
            customMessagePayload = null,
            transactionTypeUiModel = transactionTypeUiModel,
            isInitiatingDevice = false,
            transactionHistoryData = null,
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

    private fun swapUiModel(isLimitOrder: Boolean) =
        TransactionTypeUiModel.Swap(
            SwapTransactionUiModel(
                src = ValuedToken(token = rune, value = "1.5", fiatValue = "$3"),
                dst = ValuedToken(token = eth, value = "0.001", fiatValue = "$3"),
                isLimitOrder = isLimitOrder,
            )
        )

    private fun swapPayload() =
        KeysignPayload(
            coin = rune,
            toAddress = "thorInbound",
            toAmount = SRC_AMOUNT,
            memo = "=:ETH.ETH:0xdst",
            blockChainSpecific =
                BlockChainSpecific.THORChain(
                    accountNumber = BigInteger.ZERO,
                    sequence = BigInteger.ZERO,
                    fee = BigInteger.valueOf(2_000_000L),
                    isDeposit = false,
                    transactionType = TransactionType.TRANSACTION_TYPE_UNSPECIFIED,
                ),
            swapPayload =
                SwapPayload.ThorChain(
                    THORChainSwapPayload(
                        fromAddress = "thorsrc",
                        fromCoin = rune,
                        toCoin = eth,
                        vaultAddress = "thorInbound",
                        routerAddress = null,
                        fromAmount = SRC_AMOUNT,
                        toAmountDecimal = BigDecimal("0.001"),
                        toAmountLimit = "0",
                        streamingInterval = "0",
                        streamingQuantity = "0",
                        expirationTime = 0UL,
                        isAffiliate = true,
                    )
                ),
            vaultPublicKeyECDSA = "pub",
            vaultLocalPartyID = "party",
            libType = null,
            wasmExecuteContractPayload = null,
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

    private companion object {
        /** 1.5 RUNE in 1e8 units. */
        val SRC_AMOUNT: BigInteger = BigInteger.valueOf(150_000_000L)
    }
}
