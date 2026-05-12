@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.send

import androidx.compose.foundation.text.input.TextFieldState
import com.vultisig.wallet.data.blockchain.FeeServiceComposite
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.repositories.AddressParserRepository
import com.vultisig.wallet.data.repositories.AdvanceGasUiRepository
import com.vultisig.wallet.data.repositories.BlockChainSpecificAndUtxo
import com.vultisig.wallet.data.repositories.BlockChainSpecificRepository
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.data.repositories.TokenRepository
import com.vultisig.wallet.data.usecases.GasFeeToEstimatedFeeUseCase
import com.vultisig.wallet.ui.models.mappers.TokenValueToStringWithUnitMapper
import com.vultisig.wallet.ui.models.send.submit.BitcoinPlanService
import com.vultisig.wallet.ui.utils.UiText
import io.mockk.every
import io.mockk.mockk
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
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

internal class GasFeeOrchestratorTest {

    private val scheduler = TestCoroutineScheduler()
    private val mainDispatcher = UnconfinedTestDispatcher(scheduler)

    private val uiState = MutableStateFlow(SendFormUiModel())
    private val selectedToken = MutableStateFlow<Coin?>(null)
    private val accounts = MutableStateFlow<List<Account>>(emptyList())
    private val gasFee = MutableStateFlow<TokenValue?>(null)
    private val gasSettings = MutableStateFlow<GasSettings?>(null)
    private val specific = MutableStateFlow<BlockChainSpecificAndUtxo?>(null)
    private val planFee = MutableStateFlow<Long?>(null)
    private val planBtc = MutableStateFlow<wallet.core.jni.proto.Bitcoin.TransactionPlan?>(null)

    private val addressFieldState = TextFieldState()
    private val tokenAmountFieldState = TextFieldState()
    private val memoFieldState = TextFieldState()

    private var vault: Vault? = null
    private var account: Account? = null
    private var resolvedDst: String? = null
    private val isMaxAmount = MutableStateFlow(false)

    private val feeServiceComposite: FeeServiceComposite = mockk(relaxed = true)
    private val tokenRepository: TokenRepository = mockk(relaxed = true)
    private val addressParserRepository: AddressParserRepository = mockk(relaxed = true)
    private val chainAccountAddressRepository: ChainAccountAddressRepository = mockk(relaxed = true)
    private val blockChainSpecificRepository: BlockChainSpecificRepository = mockk(relaxed = true)
    private val advanceGasUiRepository: AdvanceGasUiRepository = mockk(relaxed = true)
    private val gasFeeToEstimatedFee: GasFeeToEstimatedFeeUseCase = mockk(relaxed = true)
    private val bitcoinPlanService: BitcoinPlanService = mockk(relaxed = true)
    private val mapTokenValueToString: TokenValueToStringWithUnitMapper = mockk(relaxed = true)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ──────── adjustGasFee top-level helper ────────

    @Test
    fun `adjustGasFee returns the original gas fee when settings are not UTXO`() {
        val gas = tokenValue(100, Coins.Ethereum.ETH)
        val ethSettings: GasSettings =
            GasSettings.Eth(BigInteger.ONE, BigInteger.TWO, BigInteger.TEN)

        val result = adjustGasFee(gas, ethSettings, spec = null)

        assertEquals(gas, result)
    }

    @Test
    fun `adjustGasFee swaps in the byteFee when both settings and specific are UTXO`() {
        val gas = tokenValue(100, Coins.ThorChain.RUNE)
        val utxoSettings = GasSettings.UTXO(byteFee = BigInteger("250"))
        val utxoSpec =
            BlockChainSpecificAndUtxo(
                BlockChainSpecific.UTXO(byteFee = BigInteger.ZERO, sendMaxAmount = false)
            )

        val result = adjustGasFee(gas, utxoSettings, utxoSpec)

        assertEquals(BigInteger("250"), result.value)
        // unit + decimals carried over from the original gas value.
        assertEquals(gas.unit, result.unit)
        assertEquals(gas.decimals, result.decimals)
    }

    @Test
    fun `adjustGasFee leaves value alone when settings are UTXO but specific is not`() {
        val gas = tokenValue(42, Coins.Ethereum.ETH)
        val utxoSettings = GasSettings.UTXO(byteFee = BigInteger("250"))
        // Mismatch: settings claim UTXO, specific is Ethereum-shaped.
        val ethSpec =
            BlockChainSpecificAndUtxo(
                BlockChainSpecific.Ethereum(
                    maxFeePerGasWei = BigInteger.ONE,
                    priorityFeeWei = BigInteger.ONE,
                    nonce = BigInteger.ZERO,
                    gasLimit = BigInteger.ONE,
                )
            )

        val result = adjustGasFee(gas, utxoSettings, ethSpec)

        assertEquals(BigInteger("42"), result.value)
    }

    // ──────── refresh() ────────

    @Test
    fun `refresh sets isRefreshing true synchronously then clears it after the delay`() =
        runTest(mainDispatcher) {
            val orchestrator = build(this) // run inside the test's TestScope (mainDispatcher)

            orchestrator.refresh()
            // mainDispatcher is unconfined, so the launch ran up to the delay(100) — true is set.
            assertTrue(uiState.value.isRefreshing)

            advanceUntilIdle()
            assertFalse(uiState.value.isRefreshing)
        }

    // ──────── collectGasTokenBalance ────────

    @Test
    fun `collectGasTokenBalance leaves gasTokenBalance null for native tokens`() =
        runTest(mainDispatcher) {
            val orchestrator = build(backgroundScope)
            orchestrator.start()

            selectedToken.value = ethCoin(isNativeToken = true)
            advanceUntilIdle()

            assertNull(uiState.value.gasTokenBalance)
        }

    @Test
    fun `collectGasTokenBalance maps the native account's tokenValue for non-native tokens`() =
        runTest(mainDispatcher) {
            every { mapTokenValueToString(any()) } returns "1.5 ETH"
            val orchestrator = build(backgroundScope)
            orchestrator.start()

            // Provide a native ETH account so the orchestrator can find the gas balance.
            accounts.value =
                listOf(
                    Account(
                        token = ethCoin(isNativeToken = true),
                        tokenValue =
                            TokenValue(
                                value = BigInteger("1500000000000000000"),
                                token = ethCoin(isNativeToken = true),
                            ),
                        fiatValue = null,
                        price = null,
                    )
                )

            // Then select a non-native ERC-20 on the same chain.
            val usdc =
                Coin(
                    chain = Chain.Ethereum,
                    ticker = "USDC",
                    logo = "",
                    address = "0xself",
                    decimal = 6,
                    hexPublicKey = "",
                    priceProviderID = "usd-coin",
                    contractAddress = "0xa0b86...",
                    isNativeToken = false,
                )
            selectedToken.value = usdc
            advanceUntilIdle()

            assertEquals(UiText.DynamicString("1.5 ETH"), uiState.value.gasTokenBalance)
        }

    // ──────── collectMaxAmount ────────

    @Test
    fun `collectMaxAmount propagates isMax to UTXO specific via sendMaxAmount`() =
        runTest(mainDispatcher) {
            account = btcAccount()
            specific.value =
                BlockChainSpecificAndUtxo(
                    BlockChainSpecific.UTXO(byteFee = BigInteger("100"), sendMaxAmount = false)
                )
            val orchestrator = build(backgroundScope)
            orchestrator.start()

            isMaxAmount.value = true
            advanceUntilIdle()

            val updated = specific.value?.blockChainSpecific as? BlockChainSpecific.UTXO
            assertEquals(true, updated?.sendMaxAmount)
            // byteFee untouched.
            assertEquals(BigInteger("100"), updated?.byteFee)
        }

    @Test
    fun `collectMaxAmount is a no-op for non-UTXO chains`() =
        runTest(mainDispatcher) {
            account =
                Account(
                    token = ethCoin(isNativeToken = true),
                    tokenValue = null,
                    fiatValue = null,
                    price = null,
                )
            // Set a non-UTXO specific so the cast inside the orchestrator returns null.
            specific.value =
                BlockChainSpecificAndUtxo(
                    BlockChainSpecific.Ethereum(
                        maxFeePerGasWei = BigInteger.ONE,
                        priorityFeeWei = BigInteger.ONE,
                        nonce = BigInteger.ZERO,
                        gasLimit = BigInteger.ONE,
                    )
                )
            val before = specific.value
            val orchestrator = build(backgroundScope)
            orchestrator.start()

            isMaxAmount.value = true
            advanceUntilIdle()

            // Specific reference unchanged — the UTXO branch is the only writer here.
            assertSame(before, specific.value)
        }

    @Test
    fun `collectMaxAmount is a no-op when there is no selected account`() =
        runTest(mainDispatcher) {
            account = null
            specific.value =
                BlockChainSpecificAndUtxo(
                    BlockChainSpecific.UTXO(byteFee = BigInteger("100"), sendMaxAmount = false)
                )
            val before = specific.value
            val orchestrator = build(backgroundScope)
            orchestrator.start()

            isMaxAmount.value = true
            advanceUntilIdle()

            assertSame(before, specific.value)
        }

    // ──────── helpers ────────

    private fun build(scope: CoroutineScope) =
        GasFeeOrchestrator(
            scope = scope,
            uiState = uiState,
            selectedToken = selectedToken,
            accounts = accounts,
            gasFee = gasFee,
            gasSettings = gasSettings,
            specific = specific,
            planFee = planFee,
            planBtc = planBtc,
            addressFieldState = addressFieldState,
            tokenAmountFieldState = tokenAmountFieldState,
            memoFieldState = memoFieldState,
            vaultProvider = { vault },
            vaultIdProvider = { "vault-id" },
            accountProvider = { account },
            resolvedDstAddressProvider = { resolvedDst },
            isMaxAmountFlow = isMaxAmount,
            feeServiceComposite = feeServiceComposite,
            tokenRepository = tokenRepository,
            addressParserRepository = addressParserRepository,
            chainAccountAddressRepository = chainAccountAddressRepository,
            blockChainSpecificRepository = blockChainSpecificRepository,
            advanceGasUiRepository = advanceGasUiRepository,
            gasFeeToEstimatedFee = gasFeeToEstimatedFee,
            bitcoinPlanService = bitcoinPlanService,
            mapTokenValueToString = mapTokenValueToString,
        )

    private fun ethCoin(isNativeToken: Boolean): Coin =
        Coin(
            chain = Chain.Ethereum,
            ticker = if (isNativeToken) "ETH" else "USDC",
            logo = "",
            address = "0xself",
            decimal = 18,
            hexPublicKey = "",
            priceProviderID = "ethereum",
            contractAddress = "",
            isNativeToken = isNativeToken,
        )

    private fun btcAccount(): Account {
        val btc =
            Coin(
                chain = Chain.Bitcoin,
                ticker = "BTC",
                logo = "",
                address = "bc1...",
                decimal = 8,
                hexPublicKey = "",
                priceProviderID = "bitcoin",
                contractAddress = "",
                isNativeToken = true,
            )
        return Account(token = btc, tokenValue = null, fiatValue = null, price = null)
    }

    private fun tokenValue(value: Long, coin: Coin): TokenValue =
        TokenValue(value = BigInteger.valueOf(value), token = coin)
}
