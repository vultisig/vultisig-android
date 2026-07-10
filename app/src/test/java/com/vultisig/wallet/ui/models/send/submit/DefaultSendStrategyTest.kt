@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.send.submit

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import com.vultisig.wallet.R
import com.vultisig.wallet.data.api.RippleAccountInfoResponseAccountDataJson
import com.vultisig.wallet.data.api.RippleAccountInfoResponseJson
import com.vultisig.wallet.data.api.RippleAccountInfoResponseResultJson
import com.vultisig.wallet.data.api.RippleApi
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
import org.junit.jupiter.api.Assumptions.assumeTrue
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
    private val destinationTagFieldState = TextFieldState()

    private val accountValidator: AccountValidator = mockk(relaxed = true)
    private val chainAccountAddressRepository: ChainAccountAddressRepository = mockk(relaxed = true)
    private val blockChainSpecificRepository: BlockChainSpecificRepository = mockk(relaxed = true)
    private val transactionRepository: TransactionRepository = mockk(relaxed = true)
    private val getAvailableTokenBalance: GetAvailableTokenBalanceUseCase = mockk(relaxed = true)
    private val gasFeeToEstimatedFee: GasFeeToEstimatedFeeUseCase = mockk(relaxed = true)
    private val rippleApi: RippleApi = mockk(relaxed = true)
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

    @Test
    fun `submit blocks native token send exceeding the available balance`() = runTest {
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
            every { amountManager.currentMaxAmount } returns BigDecimal.ZERO
            // Only 0.4 ETH available, but the form has 0.5 ETH entered.
            coEvery { getAvailableTokenBalance(any(), any()) } returns
                TokenValue(BigInteger.valueOf(400_000_000_000_000_000L), ethCoin)

            build(this).submit()
            advanceUntilIdle()

            assertEquals(
                R.string.send_error_insufficient_native_balance_with_fees,
                (lastError as UiText.FormattedText).resId,
            )
            // The insufficient-balance check throws before the strategy ever builds or persists
            // a Transaction, so a non-null lastError already proves addTransaction was skipped.
        } finally {
            unmockkStatic(Dispatchers::class)
        }
    }

    /**
     * `validateRippleDestinationReserve` formats the reserve amount via WalletCore's `CoinType`,
     * which is unavailable in a plain JVM unit test — the assertion below only runs when the native
     * lib loads, mirroring the same skip used for [ChainValidationServiceTest]'s BTC-like dust
     * tests.
     */
    @Test
    fun `submit blocks XRP send to an unfunded destination below the reserve`() {
        try {
            runTest {
                mockkStatic(Dispatchers::class)
                every { Dispatchers.IO } returns mainDispatcher
                try {
                    val xrpCoin = xrpCoin()
                    val account =
                        Account(
                            token = xrpCoin,
                            tokenValue =
                                TokenValue(BigInteger.valueOf(20_000_000L), xrpCoin), // 20 XRP
                            fiatValue = null,
                            price = null,
                        )
                    vaultId = "vault-1"
                    selectedAccount = account
                    addressFieldState.setTextAndPlaceCursorAtEnd("rNewDestination")
                    tokenAmountFieldState.setTextAndPlaceCursorAtEnd("0.5") // below 1 XRP reserve
                    coEvery { accountValidator.validate() } returns
                        ValidatedAccount(
                            vaultId = "vault-1",
                            selectedAccount = account,
                            chain = Chain.Ripple,
                            gasFee = TokenValue(BigInteger.valueOf(400L), xrpCoin),
                            dstAddress = "rNewDestination",
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
                            BlockChainSpecific.Ripple(
                                sequence = 1UL,
                                lastLedgerSequence = 100UL,
                                gas = 400UL,
                            )
                        )
                    every { amountManager.currentMaxAmount } returns BigDecimal.ZERO
                    coEvery { getAvailableTokenBalance(any(), any()) } returns
                        TokenValue(BigInteger.valueOf(19_999_600L), xrpCoin)
                    // The destination has never been funded.
                    coEvery { rippleApi.fetchAccountsInfo("rNewDestination") } returns null

                    build(this).submit()
                    advanceUntilIdle()

                    assertEquals(
                        R.string.send_error_xrp_destination_not_activated,
                        (lastError as UiText.FormattedText).resId,
                    )
                    // The reserve check throws before the strategy ever builds or persists a
                    // Transaction, so a non-null lastError already proves addTransaction was
                    // skipped.
                } finally {
                    unmockkStatic(Dispatchers::class)
                }
            }
        } catch (e: Throwable) {
            if (
                e is UnsatisfiedLinkError ||
                    e is ExceptionInInitializerError ||
                    e is NoClassDefFoundError
            ) {
                assumeTrue(false, "WalletCore JNI not available: ${e.message}")
            } else throw e
        }
    }

    /**
     * #5247 dual-write: an XRP send with a dedicated destination tag and no memo must persist the
     * tag's canonical decimal in `Transaction.memo` too, so a not-yet-updated co-signer that only
     * reads the legacy memo-as-tag carrier rebuilds the same DestinationTag (byte-identical
     * sighash).
     */
    @Test
    fun `submit dual-writes the XRP destination tag into the memo`() {
        try {
            runTest {
                mockkStatic(Dispatchers::class)
                every { Dispatchers.IO } returns mainDispatcher
                try {
                    val xrpCoin = xrpCoin()
                    val account =
                        Account(
                            token = xrpCoin,
                            tokenValue =
                                TokenValue(BigInteger.valueOf(20_000_000L), xrpCoin), // 20 XRP
                            fiatValue = null,
                            price = null,
                        )
                    vaultId = "vault-1"
                    selectedAccount = account
                    addressFieldState.setTextAndPlaceCursorAtEnd("rDest")
                    tokenAmountFieldState.setTextAndPlaceCursorAtEnd("5") // above 1 XRP reserve
                    destinationTagFieldState.setTextAndPlaceCursorAtEnd("12345")
                    // memo left empty on purpose.
                    coEvery { accountValidator.validate() } returns
                        ValidatedAccount(
                            vaultId = "vault-1",
                            selectedAccount = account,
                            chain = Chain.Ripple,
                            gasFee = TokenValue(BigInteger.valueOf(400L), xrpCoin),
                            dstAddress = "rDest",
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
                            BlockChainSpecific.Ripple(
                                sequence = 1UL,
                                lastLedgerSequence = 100UL,
                                gas = 400UL,
                            )
                        )
                    every { amountManager.currentMaxAmount } returns BigDecimal.ZERO
                    coEvery { getAvailableTokenBalance(any(), any()) } returns
                        TokenValue(BigInteger.valueOf(19_999_600L), xrpCoin)
                    // Funded destination (accountData present) so the reserve check passes without
                    // touching WalletCore.
                    coEvery { rippleApi.fetchAccountsInfo("rDest") } returns
                        RippleAccountInfoResponseJson(
                            result =
                                RippleAccountInfoResponseResultJson(
                                    accountData =
                                        RippleAccountInfoResponseAccountDataJson(
                                            balance = "20000000",
                                            flags = 0L,
                                        )
                                )
                        )
                    coEvery { gasFeeToEstimatedFee(any()) } returns
                        EstimatedGasFee(
                            formattedFiatValue = "$0.01",
                            formattedTokenValue = "0.0001 XRP",
                            tokenValue = TokenValue(BigInteger.valueOf(400L), xrpCoin),
                            fiatValue = mockk(relaxed = true),
                        )

                    val captured = slot<Transaction>()
                    coEvery { transactionRepository.addTransaction(capture(captured)) } returns Unit

                    build(this).submit()
                    advanceUntilIdle()

                    assertNull(lastError, "Expected no error; got $lastError")
                    val tx = captured.captured
                    assertEquals("12345", tx.memo)
                    assertEquals(
                        12345u,
                        (tx.blockChainSpecific as BlockChainSpecific.Ripple).destinationTag,
                    )
                } finally {
                    unmockkStatic(Dispatchers::class)
                }
            }
        } catch (e: Throwable) {
            if (
                e is UnsatisfiedLinkError ||
                    e is ExceptionInInitializerError ||
                    e is NoClassDefFoundError
            ) {
                assumeTrue(false, "WalletCore JNI not available: ${e.message}")
            } else throw e
        }
    }

    private fun xrpCoin(): Coin =
        Coin(
            chain = Chain.Ripple,
            ticker = "XRP",
            logo = "",
            address = "rSelf",
            decimal = 6,
            hexPublicKey = "",
            priceProviderID = "ripple",
            contractAddress = "",
            isNativeToken = true,
        )

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
            destinationTagFieldState = destinationTagFieldState,
            accountValidator = accountValidator,
            chainAccountAddressRepository = chainAccountAddressRepository,
            blockChainSpecificRepository = blockChainSpecificRepository,
            transactionRepository = transactionRepository,
            bitcoinPlanService = mockk(relaxed = true),
            getAvailableTokenBalance = getAvailableTokenBalance,
            gasFeeToEstimatedFee = gasFeeToEstimatedFee,
            chainValidationService = ChainValidationService(rippleApi = rippleApi),
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
