@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.send.submit

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.EstimatedGasFee
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.Transaction
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.settings.AppCurrency
import com.vultisig.wallet.data.repositories.BlockChainSpecificAndUtxo
import com.vultisig.wallet.data.repositories.BlockChainSpecificRepository
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.data.repositories.TransactionRepository
import com.vultisig.wallet.data.usecases.GasFeeToEstimatedFeeUseCase
import com.vultisig.wallet.data.usecases.GetAvailableTokenBalanceUseCase
import com.vultisig.wallet.ui.models.send.AddressManager
import com.vultisig.wallet.ui.models.send.AmountManager
import com.vultisig.wallet.ui.models.send.ChainValidationService
import com.vultisig.wallet.ui.models.send.GasSettings
import com.vultisig.wallet.ui.models.send.SendFocusField
import com.vultisig.wallet.ui.models.send.SendSections
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.screens.v2.defi.model.DeFiNavActions
import com.vultisig.wallet.ui.utils.UiText
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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
import wallet.core.jni.proto.Bitcoin

internal class DefaultSendStrategyTest {

    private val scheduler = TestCoroutineScheduler()
    private val mainDispatcher = UnconfinedTestDispatcher(scheduler)

    private val addressFieldState = TextFieldState()
    private val tokenAmountFieldState = TextFieldState()
    private val fiatAmountFieldState = TextFieldState()
    private val memoFieldState = TextFieldState()

    private val accountValidator: AccountValidator = mockk(relaxed = true)
    private val chainAccountAddressRepository: ChainAccountAddressRepository = mockk(relaxed = true)
    private val blockChainSpecificRepository: BlockChainSpecificRepository = mockk(relaxed = true)
    private val transactionRepository: TransactionRepository = mockk(relaxed = true)
    private val getAvailableTokenBalance: GetAvailableTokenBalanceUseCase = mockk(relaxed = true)
    private val gasFeeToEstimatedFee: GasFeeToEstimatedFeeUseCase = mockk(relaxed = true)
    private val amountManager: AmountManager = mockk(relaxed = true)
    private val addressManager: AddressManager = mockk(relaxed = true)
    private val dstAddressLabelFlow = MutableStateFlow<String?>(null)

    private var vaultId: String? = null
    private var selectedAccount: Account? = null
    private var expandedSection: SendSections? = null
    private var emittedFocusField: SendFocusField? = null
    private var lastError: UiText? = null
    private var defiType: DeFiNavActions? = null
    private val accounts = MutableStateFlow<List<Account>>(emptyList())

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        every { addressManager.dstAddressLabel } returns dstAddressLabelFlow
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `submit with blank address expands Address and emits ADDRESS focus pre-launch`() = runTest {
        build(this).submit()

        assertEquals(SendSections.Address, expandedSection)
        assertEquals(SendFocusField.ADDRESS, emittedFocusField)
        assertNull(lastError)
    }

    @Test
    fun `submit with non-blank address but blank amount expands Amount and emits AMOUNT focus`() =
        runTest {
            addressFieldState.setTextAndPlaceCursorAtEnd("0xabc")

            build(this).submit()

            assertEquals(SendSections.Amount, expandedSection)
            assertEquals(SendFocusField.AMOUNT, emittedFocusField)
            assertNull(lastError)
        }

    @Test
    fun `submit with blank address does not launch a coroutine`() = runTest {
        // Accounts unset — if the strategy launched, it would surface no_token via showError.
        build(this).submit()
        // No advanceUntilIdle; the early return in submit() runs synchronously.
        assertNull(lastError)
    }

    @Test
    fun `submit persists Transaction with parsed amount and resolved dst address`() = runTest {
        mockkStatic(Dispatchers::class)
        every { Dispatchers.IO } returns mainDispatcher
        try {
            val ethCoin = ethCoin()
            val account =
                Account(
                    token = ethCoin,
                    tokenValue =
                        TokenValue(BigInteger.valueOf(1_000_000_000_000_000_000L), ethCoin),
                    fiatValue = null,
                    price = null,
                )
            vaultId = "vault-1"
            selectedAccount = account
            addressFieldState.setTextAndPlaceCursorAtEnd("0xdest")
            tokenAmountFieldState.setTextAndPlaceCursorAtEnd("0.5")
            coEvery { accountValidator.validate() } returns
                ValidatedAccount(
                    vaultId = "vault-1",
                    selectedAccount = account,
                    chain = Chain.Ethereum,
                    gasFee = TokenValue(BigInteger.valueOf(21_000), ethCoin),
                    dstAddress = "0xdest",
                )
            coEvery { chainAccountAddressRepository.isValid(any(), any()) } returns true
            coEvery {
                blockChainSpecificRepository.getSpecific(
                    chain = any(),
                    address = any(),
                    token = any(),
                    gasFee = any(),
                    isSwap = any(),
                    isMaxAmountEnabled = any(),
                    isDeposit = any(),
                    dstAddress = any(),
                    tokenAmountValue = any(),
                    memo = any(),
                    isThorchainRouterDeposit = any(),
                )
            } returns
                BlockChainSpecificAndUtxo(
                    BlockChainSpecific.Ethereum(
                        maxFeePerGasWei = BigInteger.ONE,
                        priorityFeeWei = BigInteger.ONE,
                        nonce = BigInteger.ZERO,
                        gasLimit = BigInteger.valueOf(21000),
                    )
                )
            every { amountManager.currentMaxAmount } returns BigDecimal.ONE
            coEvery { getAvailableTokenBalance(any(), any()) } returns
                TokenValue(BigInteger.valueOf(1_000_000_000_000_000_000L), ethCoin)
            coEvery { gasFeeToEstimatedFee(any()) } returns
                EstimatedGasFee(
                    formattedFiatValue = "$0.10",
                    formattedTokenValue = "0.0001 ETH",
                    tokenValue = TokenValue(BigInteger.ONE, ethCoin),
                    fiatValue = mockk(relaxed = true),
                )

            val captured = slot<Transaction>()
            coEvery { transactionRepository.addTransaction(capture(captured)) } returns Unit

            build(this).submit()
            advanceUntilIdle()

            assertNull(lastError, "Expected no error; got $lastError")
            val tx = captured.captured
            assertEquals("0xdest", tx.dstAddress)
            // 0.5 ETH at 18 decimals = 5e17 wei.
            assertEquals(BigInteger("500000000000000000"), tx.tokenValue.value)
            assertNotNull(tx.blockChainSpecific)
        } finally {
            unmockkStatic(Dispatchers::class)
        }
    }

    /**
     * Production regression for #4152: when the user reaches the Send form via the THORChain LP
     * "Add LP → ETH.<token>" navigation, the resulting non-native EVM Send must pass
     * `isThorchainRouterDeposit = true` so the helper bumps the gas limit past the bare-transfer
     * 150k ceiling. Without this, `depositWithExpiry` reverts on non-standard ERC-20s like USDT.
     */
    @Test
    fun `ADD_LP defi non-native EVM Send flags getSpecific as thorchain router deposit`() =
        runTest {
            mockkStatic(Dispatchers::class)
            every { Dispatchers.IO } returns mainDispatcher
            try {
                val usdtCoin = usdtCoin()
                val account =
                    Account(
                        token = usdtCoin,
                        tokenValue = TokenValue(BigInteger("1000000000"), usdtCoin),
                        fiatValue = null,
                        price = null,
                    )
                vaultId = "vault-1"
                selectedAccount = account
                defiType = DeFiNavActions.ADD_LP
                addressFieldState.setTextAndPlaceCursorAtEnd("0xrouter")
                tokenAmountFieldState.setTextAndPlaceCursorAtEnd("0.30")
                memoFieldState.setTextAndPlaceCursorAtEnd("+:ETH.USDT-0xdac17:thor1abc")
                coEvery { accountValidator.validate() } returns
                    ValidatedAccount(
                        vaultId = "vault-1",
                        selectedAccount = account,
                        chain = Chain.Ethereum,
                        gasFee = TokenValue(BigInteger.valueOf(21_000), usdtCoin),
                        dstAddress = "0xrouter",
                    )
                coEvery { chainAccountAddressRepository.isValid(any(), any()) } returns true
                accounts.value =
                    listOf(
                        Account(
                            token = ethCoin(),
                            tokenValue = TokenValue(BigInteger("1000000000000000000"), ethCoin()),
                            fiatValue = null,
                            price = null,
                        )
                    )

                val flagSlot = slot<Boolean>()
                coEvery {
                    blockChainSpecificRepository.getSpecific(
                        chain = any(),
                        address = any(),
                        token = any(),
                        gasFee = any(),
                        isSwap = any(),
                        isMaxAmountEnabled = any(),
                        isDeposit = any(),
                        dstAddress = any(),
                        tokenAmountValue = any(),
                        memo = any(),
                        isThorchainRouterDeposit = capture(flagSlot),
                    )
                } returns
                    BlockChainSpecificAndUtxo(
                        BlockChainSpecific.Ethereum(
                            maxFeePerGasWei = BigInteger.ONE,
                            priorityFeeWei = BigInteger.ONE,
                            nonce = BigInteger.ZERO,
                            gasLimit = BigInteger.valueOf(200_000),
                        )
                    )
                every { amountManager.currentMaxAmount } returns BigDecimal.ONE
                coEvery { gasFeeToEstimatedFee(any()) } returns
                    EstimatedGasFee(
                        formattedFiatValue = "$0.10",
                        formattedTokenValue = "0.0001 ETH",
                        tokenValue = TokenValue(BigInteger.ONE, ethCoin()),
                        fiatValue = mockk(relaxed = true),
                    )
                coEvery { transactionRepository.addTransaction(any()) } returns Unit

                build(this).submit()
                advanceUntilIdle()

                assertEquals(true, flagSlot.captured)
            } finally {
                unmockkStatic(Dispatchers::class)
            }
        }

    private fun usdtCoin(): Coin =
        Coin(
            chain = Chain.Ethereum,
            ticker = "USDT",
            logo = "",
            address = "0xself",
            decimal = 6,
            hexPublicKey = "",
            priceProviderID = "tether",
            contractAddress = "0xdac17f958d2ee523a2206206994597c13d831ec7",
            isNativeToken = false,
        )

    private fun ethCoin(): Coin =
        Coin(
            chain = Chain.Ethereum,
            ticker = "ETH",
            logo = "",
            address = "0xself",
            decimal = 18,
            hexPublicKey = "",
            priceProviderID = "ethereum",
            contractAddress = "",
            isNativeToken = true,
        )

    private fun build(scope: CoroutineScope) =
        DefaultSendStrategy(
            scope = scope,
            addressFieldState = addressFieldState,
            tokenAmountFieldState = tokenAmountFieldState,
            fiatAmountFieldState = fiatAmountFieldState,
            memoFieldState = memoFieldState,
            accountValidator = accountValidator,
            chainAccountAddressRepository = chainAccountAddressRepository,
            blockChainSpecificRepository = blockChainSpecificRepository,
            feeServiceComposite = mockk(relaxed = true),
            transactionRepository = transactionRepository,
            bitcoinPlanService = mockk(relaxed = true),
            getAvailableTokenBalance = getAvailableTokenBalance,
            gasFeeToEstimatedFee = gasFeeToEstimatedFee,
            chainValidationService = ChainValidationService(),
            addressManager = addressManager,
            amountManager = amountManager,
            gasSettings = MutableStateFlow<GasSettings?>(null),
            planBtc = MutableStateFlow<Bitcoin.TransactionPlan?>(null),
            planFee = MutableStateFlow<Long?>(null),
            accounts = accounts,
            appCurrency = MutableStateFlow(AppCurrency.USD),
            vaultIdProvider = { vaultId },
            selectedAccountProvider = { selectedAccount },
            defiTypeProvider = { defiType },
            currentTronFrozenBalanceProvider = { null },
            navigator = mockk<Navigator<Destination>>(relaxed = true),
            expandSection = { expandedSection = it },
            emitFocusField = { emittedFocusField = it },
            showLoading = {},
            hideLoading = {},
            showError = { lastError = it },
        )
}
