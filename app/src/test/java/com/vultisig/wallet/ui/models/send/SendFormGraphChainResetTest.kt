@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.send

import androidx.compose.foundation.text.input.TextFieldState
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.settings.AppCurrency
import com.vultisig.wallet.data.repositories.BlockChainSpecificAndUtxo
import com.vultisig.wallet.ui.models.mappers.AccountToTokenBalanceUiModelMapper
import io.mockk.coEvery
import io.mockk.mockk
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Issue #6168: Advanced Gas Settings are priced for the chain they were saved on, so everything the
 * form derived from them has to go when the selected chain changes — the saved settings themselves,
 * and the chain-specific data `saveGasSettings` folded a UTXO byte fee into.
 */
internal class SendFormGraphChainResetTest {

    private val scheduler = TestCoroutineScheduler()
    private val mainDispatcher = UnconfinedTestDispatcher(scheduler)

    private val uiState = MutableStateFlow(SendFormUiModel())
    private val selectedToken = MutableStateFlow<Coin?>(null)
    private val accounts = MutableStateFlow<List<Account>>(emptyList())
    private val accountsState = MutableStateFlow<AccountsLoadState>(AccountsLoadState.Uninitialized)
    private val appCurrency = MutableStateFlow(AppCurrency.USD)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `switching chains drops gas settings saved for the previous one`() =
        runTest(mainDispatcher) {
            val graph = buildGraph(backgroundScope)
            graph.bindUiState()
            selectedToken.value = btc
            advanceUntilIdle()
            graph.gasSettings.value = GasSettings.UTXO(Chain.Bitcoin, BigInteger("35"))

            selectedToken.value = doge
            advanceUntilIdle()

            assertNull(graph.gasSettings.value)
        }

    @Test
    fun `gas settings survive a token change within the same chain`() =
        runTest(mainDispatcher) {
            val graph = buildGraph(backgroundScope)
            graph.bindUiState()
            selectedToken.value = eth
            advanceUntilIdle()
            val saved =
                GasSettings.Eth(
                    chain = Chain.Ethereum,
                    baseFee = BigInteger("1000"),
                    priorityFee = BigInteger("2000"),
                    gasLimit = BigInteger("21000"),
                )
            graph.gasSettings.value = saved

            // Same chain, different asset: the fee the user set still prices this send.
            selectedToken.value = usdc
            advanceUntilIdle()

            assertEquals(saved, graph.gasSettings.value)
        }

    /**
     * `saveGasSettings` copies a UTXO byte fee straight into `specific`, and only the debounced
     * `getSpecific` replaces it. Leaving it in place let `calculateUtxoMaxAmount` plan a Max on the
     * new chain with the previous chain's fee rate, so the spec is dropped and that planner takes
     * its null fallback until the new one loads.
     */
    @Test
    fun `switching chains drops the chain specific data carrying the old byte fee`() =
        runTest(mainDispatcher) {
            val graph = buildGraph(backgroundScope)
            graph.bindUiState()
            selectedToken.value = btc
            advanceUntilIdle()
            val bitcoinSpec =
                BlockChainSpecificAndUtxo(
                    BlockChainSpecific.UTXO(byteFee = BigInteger("35"), sendMaxAmount = false)
                )
            graph.specific.value = bitcoinSpec
            uiState.update { it.copy(specific = bitcoinSpec) }

            selectedToken.value = doge
            advanceUntilIdle()

            assertNull(graph.specific.value)
            // The UI copy goes too, so the gas sheet can't open on another chain's spec.
            assertNull(uiState.value.specific)
        }

    @Test
    fun `chain specific data survives a token change within the same chain`() =
        runTest(mainDispatcher) {
            val graph = buildGraph(backgroundScope)
            graph.bindUiState()
            selectedToken.value = eth
            advanceUntilIdle()
            val ethSpec =
                BlockChainSpecificAndUtxo(
                    BlockChainSpecific.Ethereum(
                        maxFeePerGasWei = BigInteger("1000"),
                        priorityFeeWei = BigInteger("100"),
                        nonce = BigInteger.ZERO,
                        gasLimit = BigInteger("21000"),
                    )
                )
            graph.specific.value = ethSpec

            selectedToken.value = usdc
            advanceUntilIdle()

            assertEquals(ethSpec, graph.specific.value)
        }

    private val btc = Coins.Bitcoin.BTC.copy(address = "bc1q")
    private val doge = Coins.Dogecoin.DOGE.copy(address = "D8")
    private val eth = Coins.Ethereum.ETH.copy(address = "0x1")
    private val usdc = Coins.Ethereum.USDC.copy(address = "0x1")

    // collectSelectedAccount casts this mapper's result, so a relaxed mock's Object won't do.
    private fun tokenBalanceMapper(): AccountToTokenBalanceUiModelMapper {
        val mapper = mockk<AccountToTokenBalanceUiModelMapper>()
        coEvery { mapper.invoke(any()) } returns
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
        return mapper
    }

    private fun buildGraph(scope: CoroutineScope) =
        SendFormGraph(
            scope = scope,
            navigator = mockk(relaxed = true),
            accountsRepository = mockk(relaxed = true),
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
            getTronFrozenBalances = mockk(relaxed = true),
            sendStrategyFactory = fakeSendStrategyFactory(),
            mapTokenValueToString = mockk(relaxed = true),
            accountToTokenBalanceUiModelMapper = tokenBalanceMapper(),
            requestAddressBookEntry = mockk(relaxed = true),
            uiState = uiState,
            selectedToken = selectedToken,
            accountsState = accountsState,
            accounts = accounts,
            appCurrency = appCurrency,
            addressFieldState = TextFieldState(),
            tokenAmountFieldState = TextFieldState(),
            fiatAmountFieldState = TextFieldState(),
            memoFieldState = TextFieldState(),
            destinationTagFieldState = TextFieldState(),
            slippageFieldState = TextFieldState(),
            selectedTokenProvider = { selectedToken.value },
            selectedAccountProvider = { null },
            isAutocompoundProvider = { false },
            expandSection = {},
            showLoading = {},
            hideLoading = {},
            showError = {},
            emitFocusField = {},
        )
}
