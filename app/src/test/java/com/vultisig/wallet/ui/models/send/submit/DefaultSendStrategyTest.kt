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
import com.vultisig.wallet.ui.models.send.selectGasFeeForFeeEstimation
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.screens.v2.defi.model.DeFiNavActions
import com.vultisig.wallet.ui.utils.UiText
import io.mockk.CapturingSlot
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import wallet.core.jni.proto.Bitcoin
import wallet.core.jni.proto.Common.SigningError

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
    private val bitcoinPlanService: BitcoinPlanService = mockk(relaxed = true)
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
    private val gasSettings = MutableStateFlow<GasSettings?>(null)
    private val planBtc = MutableStateFlow<Bitcoin.TransactionPlan?>(null)
    private val planFee = MutableStateFlow<Long?>(null)

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
            val captured =
                arrangeSuccessfulEthSubmit(
                    ethCoin(),
                    BlockChainSpecific.Ethereum(
                        maxFeePerGasWei = BigInteger.ONE,
                        priorityFeeWei = BigInteger.ONE,
                        nonce = BigInteger.ZERO,
                        gasLimit = BigInteger.valueOf(21000),
                    ),
                )

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
     * Regression for #5397: the maxFeePerGasWei signed into BlockChainSpecific.Ethereum and the fee
     * estimate shown to the user (GasFeeSelection.selectGasFeeForFeeEstimation, used by both the
     * live Send-form estimate and this same submit path's totalGasAndFee) must derive from the same
     * GasSettings.Eth sum. Previously the signed value dropped the priority fee entirely.
     */
    @Test
    fun `submit sums base and priority fee into the signed maxFeePerGasWei, matching the fee estimate`() =
        runTest {
            mockkStatic(Dispatchers::class)
            every { Dispatchers.IO } returns mainDispatcher
            try {
                val ethCoin = ethCoin()
                val captured =
                    arrangeSuccessfulEthSubmit(
                        ethCoin,
                        BlockChainSpecific.Ethereum(
                            maxFeePerGasWei = BigInteger.ONE,
                            priorityFeeWei = BigInteger.ONE,
                            nonce = BigInteger.ZERO,
                            gasLimit = BigInteger.valueOf(21000),
                        ),
                    )

                val advancedGasSettings =
                    GasSettings.Eth(
                        baseFee = BigInteger.valueOf(30_000_000_000L),
                        priorityFee = BigInteger.valueOf(2_000_000_000L),
                        gasLimit = BigInteger.valueOf(21_000),
                    )
                gasSettings.value = advancedGasSettings

                build(this).submit()
                advanceUntilIdle()

                assertNull(lastError, "Expected no error; got $lastError")
                val signedSpec = captured.captured.blockChainSpecific as BlockChainSpecific.Ethereum
                assertEquals(BigInteger.valueOf(32_000_000_000L), signedSpec.maxFeePerGasWei)

                val displayedEstimateFee =
                    selectGasFeeForFeeEstimation(
                        chain = Chain.Ethereum,
                        gasFee = TokenValue(BigInteger.valueOf(21_000), ethCoin),
                        planFee = null,
                        evmGasSettings = advancedGasSettings,
                    )
                assertEquals(signedSpec.maxFeePerGasWei, displayedEstimateFee.value)
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

    /**
     * Regression for #5504: submit reused `planBtc.value` via an elvis short-circuit whenever it
     * was already non-null, so a background-collected plan computed against a stale specific (a
     * wrong sendMaxAmount, or a since-changed amount) never got re-planned at submit time — even
     * though submit's own `specific` here already carries the corrected flag/amount. Submit must
     * always re-plan.
     */
    @Test
    fun `submit always re-plans the BTC transaction, even when planBtc already holds a cached plan`() =
        runTest {
            val btcCoin = btcCoin()
            val account =
                Account(
                    token = btcCoin,
                    tokenValue = TokenValue(BigInteger.valueOf(1_000_000L), btcCoin),
                    fiatValue = null,
                    price = null,
                )
            vaultId = "vault-1"
            selectedAccount = account
            addressFieldState.setTextAndPlaceCursorAtEnd("bc1dest")
            tokenAmountFieldState.setTextAndPlaceCursorAtEnd("0.0099945")
            coEvery { accountValidator.validate() } returns
                ValidatedAccount(
                    vaultId = "vault-1",
                    selectedAccount = account,
                    chain = Chain.Bitcoin,
                    gasFee = TokenValue(BigInteger.TEN, btcCoin),
                    dstAddress = "bc1dest",
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
                    BlockChainSpecific.UTXO(byteFee = BigInteger.TEN, sendMaxAmount = false)
                )
            every { amountManager.currentMaxAmount } returns BigDecimal.ZERO
            coEvery { getAvailableTokenBalance(any(), any()) } returns
                TokenValue(BigInteger.valueOf(1_000_000L), btcCoin)
            coEvery { gasFeeToEstimatedFee(any()) } returns
                EstimatedGasFee(
                    formattedFiatValue = "$0.10",
                    formattedTokenValue = "0.0000001 BTC",
                    tokenValue = TokenValue(BigInteger.ONE, btcCoin),
                    fiatValue = mockk(relaxed = true),
                )
            coEvery { transactionRepository.addTransaction(any()) } returns Unit

            val freshPlan =
                Bitcoin.TransactionPlan.newBuilder()
                    .setAmount(994_500L)
                    .setFee(550L)
                    .setError(SigningError.OK)
                    .build()
            val bitcoinPlanServiceMock: BitcoinPlanService = mockk()
            coEvery {
                bitcoinPlanServiceMock.getPlan(any(), any(), any(), any(), any(), any())
            } returns freshPlan

            // A stale, already-failing plan sits cached in planBtc from an earlier background
            // recompute — the old elvis short-circuit would reuse this instead of re-planning.
            val stalePlan =
                Bitcoin.TransactionPlan.newBuilder()
                    .setError(SigningError.Error_not_enough_utxos)
                    .build()
            val planBtcFlow = MutableStateFlow<Bitcoin.TransactionPlan?>(stalePlan)

            mockkStatic(Dispatchers::class)
            every { Dispatchers.IO } returns mainDispatcher
            try {
                build(this, bitcoinPlanService = bitcoinPlanServiceMock, planBtc = planBtcFlow)
                    .submit()
                advanceUntilIdle()
            } finally {
                unmockkStatic(Dispatchers::class)
            }

            coVerify(exactly = 1) {
                bitcoinPlanServiceMock.getPlan(any(), any(), any(), any(), any(), any())
            }
            assertNull(lastError, "Expected no error; got $lastError")
            assertEquals(freshPlan, planBtcFlow.value)
        }

    /**
     * Regression for #5504: `sendMaxAmount=true` tells WalletCore's planner to sweep the real
     * balance-minus-fee itself, ignoring the requested amount. The staged Transaction must reflect
     * what the plan actually signs, not the approximate byteFee-based estimate that produced the
     * requested amount — otherwise the Verify screen shows a number the broadcast transaction
     * doesn't match.
     */
    @Test
    fun `submit stages the plan's real sweep amount for a Max BTC send, not the approximate one`() =
        runTest {
            val btcCoin = btcCoin()
            val account =
                Account(
                    token = btcCoin,
                    tokenValue = TokenValue(BigInteger.valueOf(1_000_000L), btcCoin),
                    fiatValue = null,
                    price = null,
                )
            vaultId = "vault-1"
            selectedAccount = account
            addressFieldState.setTextAndPlaceCursorAtEnd("bc1dest")
            tokenAmountFieldState.setTextAndPlaceCursorAtEnd("0.01")
            coEvery { accountValidator.validate() } returns
                ValidatedAccount(
                    vaultId = "vault-1",
                    selectedAccount = account,
                    chain = Chain.Bitcoin,
                    gasFee = TokenValue(BigInteger.TEN, btcCoin),
                    dstAddress = "bc1dest",
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
                    BlockChainSpecific.UTXO(byteFee = BigInteger.TEN, sendMaxAmount = true)
                )
            // The estimate that put "0.01" in the amount field in the first place.
            every { amountManager.currentMaxAmount } returns BigDecimal("0.01")
            coEvery { getAvailableTokenBalance(any(), any()) } returns
                TokenValue(BigInteger.valueOf(1_000_000L), btcCoin)
            coEvery { gasFeeToEstimatedFee(any()) } returns
                EstimatedGasFee(
                    formattedFiatValue = "$0.10",
                    formattedTokenValue = "0.0000001 BTC",
                    tokenValue = TokenValue(BigInteger.ONE, btcCoin),
                    fiatValue = mockk(relaxed = true),
                )
            val captured = slot<Transaction>()
            coEvery { transactionRepository.addTransaction(capture(captured)) } returns Unit

            // WalletCore's own precise sweep — deliberately different from the "0.01" estimate.
            val realSweepPlan =
                Bitcoin.TransactionPlan.newBuilder()
                    .setAmount(994_500L)
                    .setFee(550L)
                    .setError(SigningError.OK)
                    .build()
            val bitcoinPlanServiceMock: BitcoinPlanService = mockk()
            coEvery {
                bitcoinPlanServiceMock.getPlan(any(), any(), any(), any(), any(), any())
            } returns realSweepPlan

            mockkStatic(Dispatchers::class)
            every { Dispatchers.IO } returns mainDispatcher
            try {
                build(this, bitcoinPlanService = bitcoinPlanServiceMock).submit()
                advanceUntilIdle()
            } finally {
                unmockkStatic(Dispatchers::class)
            }

            assertNull(lastError, "Expected no error; got $lastError")
            assertEquals(BigInteger.valueOf(994_500L), captured.captured.tokenValue.value)
        }

    /**
     * A native amount that overshoots the balance on its own is a real over-entry, not a fee edge —
     * it must still be rejected rather than silently adjusted down (see the #5493 clamp below).
     */
    @Test
    fun `submit blocks native token send exceeding the balance itself`() = runTest {
        mockkStatic(Dispatchers::class)
        every { Dispatchers.IO } returns mainDispatcher
        try {
            val ethCoin = ethCoin()
            val account =
                Account(
                    token = ethCoin,
                    tokenValue = TokenValue(BigInteger.valueOf(400_000_000_000_000_000L), ethCoin),
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
            // Only 0.4 ETH held, but the form has 0.5 ETH entered.
            coEvery { getAvailableTokenBalance(any(), any()) } returns
                TokenValue(BigInteger.valueOf(399_999_999_999_979_000L), ethCoin)

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

    /**
     * Race 1 (#5316): the non-native "Max" snapshot is captured against a stale-high cached
     * balance, then network hydration corrects the balance downward before submit. The stale field
     * must be re-clamped to the CURRENT balance so the send succeeds instead of failing
     * "insufficient" — which is what forced users to leave some USDT behind.
     */
    @Test
    fun `submit re-clamps a stale non-native Max down to the current balance`() = runTest {
        mockkStatic(Dispatchers::class)
        every { Dispatchers.IO } returns mainDispatcher
        try {
            val usdtCoin = usdtCoin()
            // Balance already corrected downward (123.400000) by the time submit runs.
            val account =
                Account(
                    token = usdtCoin,
                    tokenValue = TokenValue(BigInteger("123400000"), usdtCoin),
                    fiatValue = null,
                    price = null,
                )
            vaultId = "vault-1"
            selectedAccount = account
            addressFieldState.setTextAndPlaceCursorAtEnd("0xdest")
            // Field still holds the stale-high Max snapshot (123.456789).
            tokenAmountFieldState.setTextAndPlaceCursorAtEnd("123.456789")
            coEvery { accountValidator.validate() } returns
                ValidatedAccount(
                    vaultId = "vault-1",
                    selectedAccount = account,
                    chain = Chain.Ethereum,
                    gasFee = TokenValue(BigInteger.valueOf(21_000), ethCoin()),
                    dstAddress = "0xdest",
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
                        gasLimit = BigInteger.valueOf(65000),
                    )
                )
            every { amountManager.currentMaxAmount } returns BigDecimal("123.456789")
            // Non-native: the use case returns the full (already-corrected) token balance.
            coEvery { getAvailableTokenBalance(any(), any()) } returns
                TokenValue(BigInteger("123400000"), usdtCoin)
            coEvery { gasFeeToEstimatedFee(any()) } returns
                EstimatedGasFee(
                    formattedFiatValue = "$0.10",
                    formattedTokenValue = "0.0001 ETH",
                    tokenValue = TokenValue(BigInteger.ONE, ethCoin()),
                    fiatValue = mockk(relaxed = true),
                )

            val captured = slot<Transaction>()
            coEvery { transactionRepository.addTransaction(capture(captured)) } returns Unit

            build(this).submit()
            advanceUntilIdle()

            assertNull(lastError, "Expected no error; got $lastError")
            // Clamped down from 123.456789 to the current 123.400000 balance.
            assertEquals(BigInteger("123400000"), captured.captured.tokenValue.value)
            assertEquals("123.4", tokenAmountFieldState.text.toString())
            // Still a Max — the snapshot moves onto the adjusted value so the form's 100%
            // selection survives the balance moving under it.
            verify { amountManager.markMax(BigDecimal("123.4")) }
        } finally {
            unmockkStatic(Dispatchers::class)
        }
    }

    /**
     * Race 2 (#5316): an EVM native "Max" reuses the cached gas fee, so the filled amount is
     * `balance − cachedFee`. If gas rises before submit, `balance − freshFee` drops below it. The
     * amount must be re-clamped to the current available balance so the send succeeds.
     */
    @Test
    fun `submit re-clamps a stale native EVM Max down when gas rises`() = runTest {
        mockkStatic(Dispatchers::class)
        every { Dispatchers.IO } returns mainDispatcher
        try {
            val ethCoin = ethCoin()
            val account =
                Account(
                    token = ethCoin,
                    tokenValue = TokenValue(BigInteger("1000000000000000000"), ethCoin), // 1.0 ETH
                    fiatValue = null,
                    price = null,
                )
            vaultId = "vault-1"
            selectedAccount = account
            addressFieldState.setTextAndPlaceCursorAtEnd("0xdest")
            // Max was filled as balance − cachedFee = 0.9990 ETH.
            tokenAmountFieldState.setTextAndPlaceCursorAtEnd("0.999")
            coEvery { accountValidator.validate() } returns
                ValidatedAccount(
                    vaultId = "vault-1",
                    selectedAccount = account,
                    chain = Chain.Ethereum,
                    gasFee = TokenValue(BigInteger("1500000000000000"), ethCoin), // fresh 0.0015
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
            every { amountManager.currentMaxAmount } returns BigDecimal("0.999")
            // Fresh available = balance − freshFee = 0.9985 ETH (< the filled 0.999).
            coEvery { getAvailableTokenBalance(any(), any()) } returns
                TokenValue(BigInteger("998500000000000000"), ethCoin)
            coEvery { gasFeeToEstimatedFee(any()) } returns
                EstimatedGasFee(
                    formattedFiatValue = "$0.10",
                    formattedTokenValue = "0.0015 ETH",
                    tokenValue = TokenValue(BigInteger.ONE, ethCoin),
                    fiatValue = mockk(relaxed = true),
                )

            val captured = slot<Transaction>()
            coEvery { transactionRepository.addTransaction(capture(captured)) } returns Unit

            build(this).submit()
            advanceUntilIdle()

            assertNull(lastError, "Expected no error; got $lastError")
            // Clamped down from 0.999 to the current 0.9985 available.
            assertEquals(BigInteger("998500000000000000"), captured.captured.tokenValue.value)
            assertEquals("0.9985", tokenAmountFieldState.text.toString())
            // Still a Max — the snapshot moves onto the adjusted value so the form's 100%
            // selection survives the fee moving under it.
            verify { amountManager.markMax(BigDecimal("0.9985")) }
        } finally {
            unmockkStatic(Dispatchers::class)
        }
    }

    /**
     * A non-native over-entry (not a Max snapshot) is a pure token-balance shortfall — gas is paid
     * in the native coin — so it must surface the token-balance error, not the "with fees" one that
     * wrongly implies reserving tokens for gas.
     */
    @Test
    fun `submit blocks non-native over-entry with the token-balance error`() = runTest {
        mockkStatic(Dispatchers::class)
        every { Dispatchers.IO } returns mainDispatcher
        try {
            val usdtCoin = usdtCoin()
            val account =
                Account(
                    token = usdtCoin,
                    tokenValue = TokenValue(BigInteger("100000000"), usdtCoin), // 100 USDT
                    fiatValue = null,
                    price = null,
                )
            vaultId = "vault-1"
            selectedAccount = account
            addressFieldState.setTextAndPlaceCursorAtEnd("0xdest")
            tokenAmountFieldState.setTextAndPlaceCursorAtEnd("150") // more than the 100 balance
            coEvery { accountValidator.validate() } returns
                ValidatedAccount(
                    vaultId = "vault-1",
                    selectedAccount = account,
                    chain = Chain.Ethereum,
                    gasFee = TokenValue(BigInteger.valueOf(21_000), ethCoin()),
                    dstAddress = "0xdest",
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
                        gasLimit = BigInteger.valueOf(65000),
                    )
                )
            // Not a Max: no clamp, so the over-entry reaches the balance check.
            every { amountManager.currentMaxAmount } returns BigDecimal.ZERO

            build(this).submit()
            advanceUntilIdle()

            assertEquals(
                R.string.send_error_insufficient_token_balance,
                (lastError as UiText.FormattedText).resId,
            )
        } finally {
            unmockkStatic(Dispatchers::class)
        }
    }

    /**
     * #5493: a hand-typed native amount that fits the balance but not `amount + fee` used to
     * dead-end on "insufficient balance". It must instead be adjusted down to `balance − fee` — and
     * the adjustment has to be visible in the amount fields, since the fiat mirror there is what
     * Verify shows next to the signed amount.
     */
    @Test
    fun `submit adjusts a hand-typed native amount down to balance minus fee`() = runTest {
        mockkStatic(Dispatchers::class)
        every { Dispatchers.IO } returns mainDispatcher
        try {
            val ethCoin = ethCoin()
            val account =
                Account(
                    token = ethCoin,
                    tokenValue = TokenValue(BigInteger("1000000000000000000"), ethCoin), // 1.0 ETH
                    fiatValue = null,
                    price = null,
                )
            vaultId = "vault-1"
            selectedAccount = account
            addressFieldState.setTextAndPlaceCursorAtEnd("0xdest")
            // The whole balance, typed by hand — within balance, but not once the fee is added.
            tokenAmountFieldState.setTextAndPlaceCursorAtEnd("1")
            fiatAmountFieldState.setTextAndPlaceCursorAtEnd("2000")
            coEvery { accountValidator.validate() } returns
                ValidatedAccount(
                    vaultId = "vault-1",
                    selectedAccount = account,
                    chain = Chain.Ethereum,
                    gasFee = TokenValue(BigInteger("1500000000000000"), ethCoin),
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
            // Never tapped Max — this is a hand-typed amount.
            every { amountManager.currentMaxAmount } returns BigDecimal.ZERO
            coEvery { getAvailableTokenBalance(any(), any()) } returns
                TokenValue(BigInteger("998500000000000000"), ethCoin)
            coEvery { gasFeeToEstimatedFee(any()) } returns
                EstimatedGasFee(
                    formattedFiatValue = "$0.10",
                    formattedTokenValue = "0.0015 ETH",
                    tokenValue = TokenValue(BigInteger.ONE, ethCoin),
                    fiatValue = mockk(relaxed = true),
                )

            val captured = slot<Transaction>()
            coEvery { transactionRepository.addTransaction(capture(captured)) } returns Unit

            build(this).submit()
            advanceUntilIdle()

            assertNull(lastError, "Expected no error; got $lastError")
            assertEquals(BigInteger("998500000000000000"), captured.captured.tokenValue.value)
            // The user must see what they are about to sign, in both token and fiat.
            assertEquals("0.9985", tokenAmountFieldState.text.toString())
            assertEquals("1997", fiatAmountFieldState.text.toString())
            assertEquals(BigDecimal("1997"), captured.captured.fiatValue.value)
            // Not a Max, so the max snapshot must not be moved onto the adjusted value.
            verify(exactly = 0) { amountManager.markMax(any()) }
        } finally {
            unmockkStatic(Dispatchers::class)
        }
    }

    /**
     * The #5493 clamp is native-only: a token's gas is paid in the native coin, so its balance is
     * fully spendable and an amount equal to it must go out untouched.
     */
    @Test
    fun `submit sends a hand-typed non-native amount equal to the balance unchanged`() = runTest {
        mockkStatic(Dispatchers::class)
        every { Dispatchers.IO } returns mainDispatcher
        try {
            val usdtCoin = usdtCoin()
            val account =
                Account(
                    token = usdtCoin,
                    tokenValue = TokenValue(BigInteger("100000000"), usdtCoin), // 100 USDT
                    fiatValue = null,
                    price = null,
                )
            vaultId = "vault-1"
            selectedAccount = account
            addressFieldState.setTextAndPlaceCursorAtEnd("0xdest")
            tokenAmountFieldState.setTextAndPlaceCursorAtEnd("100")
            coEvery { accountValidator.validate() } returns
                ValidatedAccount(
                    vaultId = "vault-1",
                    selectedAccount = account,
                    chain = Chain.Ethereum,
                    gasFee = TokenValue(BigInteger.valueOf(21_000), ethCoin()),
                    dstAddress = "0xdest",
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
                        gasLimit = BigInteger.valueOf(65000),
                    )
                )
            every { amountManager.currentMaxAmount } returns BigDecimal.ZERO
            coEvery { getAvailableTokenBalance(any(), any()) } returns
                TokenValue(BigInteger("100000000"), usdtCoin)
            coEvery { gasFeeToEstimatedFee(any()) } returns
                EstimatedGasFee(
                    formattedFiatValue = "$0.10",
                    formattedTokenValue = "0.0001 ETH",
                    tokenValue = TokenValue(BigInteger.ONE, ethCoin()),
                    fiatValue = mockk(relaxed = true),
                )

            val captured = slot<Transaction>()
            coEvery { transactionRepository.addTransaction(capture(captured)) } returns Unit

            build(this).submit()
            advanceUntilIdle()

            assertNull(lastError, "Expected no error; got $lastError")
            assertEquals(BigInteger("100000000"), captured.captured.tokenValue.value)
            assertEquals("100", tokenAmountFieldState.text.toString())
        } finally {
            unmockkStatic(Dispatchers::class)
        }
    }

    /**
     * DeFi flows carry their own balance semantics (staked/bonded/frozen amounts) and compute their
     * own max, so the #5493 clamp must leave them alone and let their balance check speak.
     */
    @Test
    fun `submit does not adjust a defi native amount and keeps the insufficient error`() = runTest {
        mockkStatic(Dispatchers::class)
        every { Dispatchers.IO } returns mainDispatcher
        try {
            val ethCoin = ethCoin()
            val account =
                Account(
                    token = ethCoin,
                    tokenValue = TokenValue(BigInteger("1000000000000000000"), ethCoin), // 1.0 ETH
                    fiatValue = null,
                    price = null,
                )
            vaultId = "vault-1"
            selectedAccount = account
            defiType = DeFiNavActions.ADD_LP
            addressFieldState.setTextAndPlaceCursorAtEnd("0xdest")
            tokenAmountFieldState.setTextAndPlaceCursorAtEnd("1")
            coEvery { accountValidator.validate() } returns
                ValidatedAccount(
                    vaultId = "vault-1",
                    selectedAccount = account,
                    chain = Chain.Ethereum,
                    gasFee = TokenValue(BigInteger("1500000000000000"), ethCoin),
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
            coEvery { getAvailableTokenBalance(any(), any()) } returns
                TokenValue(BigInteger("998500000000000000"), ethCoin)

            build(this).submit()
            advanceUntilIdle()

            assertEquals(
                R.string.send_error_insufficient_native_balance_with_fees,
                (lastError as UiText.FormattedText).resId,
            )
            assertEquals("1", tokenAmountFieldState.text.toString())
        } finally {
            unmockkStatic(Dispatchers::class)
        }
    }

    /**
     * `adjustGasFee` folds UTXO byte fees and Cardano fees back into `gasFee` but leaves
     * `GasSettings.Eth` out, while `applyGasSettings` still patches the signed maxFeePerGasWei and
     * gasLimit. Sizing the clamp on `gasFee` alone would hand back a clean-looking amount the chain
     * then rejects for insufficient funds, so raised Advanced Gas Settings must reserve
     * `maxFeePerGasWei × gasLimit`.
     */
    @Test
    fun `submit sizes the adjustment on raised advanced gas settings, not the stale gas fee`() =
        runTest {
            mockkStatic(Dispatchers::class)
            every { Dispatchers.IO } returns mainDispatcher
            try {
                val ethCoin = ethCoin()
                val account =
                    Account(
                        token = ethCoin,
                        tokenValue = TokenValue(BigInteger("1000000000000000000"), ethCoin),
                        fiatValue = null,
                        price = null,
                    )
                vaultId = "vault-1"
                selectedAccount = account
                addressFieldState.setTextAndPlaceCursorAtEnd("0xdest")
                tokenAmountFieldState.setTextAndPlaceCursorAtEnd("1")
                // Network estimate is 0.0015 ETH; gasFee never learns about the raise below.
                val networkGasFee = TokenValue(BigInteger("1500000000000000"), ethCoin)
                coEvery { accountValidator.validate() } returns
                    ValidatedAccount(
                        vaultId = "vault-1",
                        selectedAccount = account,
                        chain = Chain.Ethereum,
                        gasFee = networkGasFee,
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
                // 200 gwei cap × 21000 gas = 0.0042 ETH — nearly 3× the network estimate.
                gasSettings.value =
                    GasSettings.Eth(
                        baseFee = BigInteger.valueOf(198_000_000_000L),
                        priorityFee = BigInteger.valueOf(2_000_000_000L),
                        gasLimit = BigInteger.valueOf(21_000),
                    )
                // The use case is honest about whichever fee it is handed.
                coEvery { getAvailableTokenBalance(any(), any()) } answers
                    {
                        TokenValue(
                            BigInteger("1000000000000000000") - secondArg<BigInteger>(),
                            ethCoin,
                        )
                    }
                coEvery { gasFeeToEstimatedFee(any()) } returns
                    EstimatedGasFee(
                        formattedFiatValue = "$0.10",
                        formattedTokenValue = "0.0042 ETH",
                        tokenValue = TokenValue(BigInteger.ONE, ethCoin),
                        fiatValue = mockk(relaxed = true),
                    )

                val captured = slot<Transaction>()
                coEvery { transactionRepository.addTransaction(capture(captured)) } returns Unit

                build(this).submit()
                advanceUntilIdle()

                assertNull(lastError, "Expected no error; got $lastError")
                // balance − (200 gwei × 21000) = 0.9958 ETH, not balance − 0.0015 = 0.9985.
                assertEquals(BigInteger("995800000000000000"), captured.captured.tokenValue.value)
                assertEquals("0.9958", tokenAmountFieldState.text.toString())
            } finally {
                unmockkStatic(Dispatchers::class)
            }
        }

    /**
     * The clamp runs before the Cardano/BTC/XRP validators, so an adjusted amount can still be
     * rejected downstream. When that happens the fields must be left holding what the user actually
     * typed — stranding the form on a number they never entered is worse than the original error.
     */
    @Test
    fun `submit leaves the amount fields untouched when a post-clamp validator rejects the send`() =
        runTest {
            mockkStatic(Dispatchers::class)
            every { Dispatchers.IO } returns mainDispatcher
            try {
                val xrpCoin = xrpCoin()
                val account =
                    Account(
                        token = xrpCoin,
                        tokenValue = TokenValue(BigInteger.valueOf(20_000_000L), xrpCoin),
                        fiatValue = null,
                        price = null,
                    )
                vaultId = "vault-1"
                selectedAccount = account
                addressFieldState.setTextAndPlaceCursorAtEnd("rNewDestination")
                tokenAmountFieldState.setTextAndPlaceCursorAtEnd("20")
                fiatAmountFieldState.setTextAndPlaceCursorAtEnd("40")
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
                // The clamp succeeds (20 → 19.9996 XRP)…
                coEvery { getAvailableTokenBalance(any(), any()) } returns
                    TokenValue(BigInteger.valueOf(19_999_600L), xrpCoin)
                // …but the funded destination requires a tag (lsfRequireDestTag) and none was
                // given, so a validator downstream of the clamp rejects the send.
                coEvery { rippleApi.fetchAccountsInfo("rNewDestination") } returns
                    RippleAccountInfoResponseJson(
                        result =
                            RippleAccountInfoResponseResultJson(
                                accountData =
                                    RippleAccountInfoResponseAccountDataJson(
                                        balance = "20000000",
                                        flags = 0x00020000L,
                                    )
                            )
                    )

                build(this).submit()
                advanceUntilIdle()

                assertEquals(
                    R.string.send_error_xrp_destination_tag_required,
                    (lastError as UiText.StringResource).resId,
                )
                assertEquals("20", tokenAmountFieldState.text.toString())
                assertEquals("40", fiatAmountFieldState.text.toString())
            } finally {
                unmockkStatic(Dispatchers::class)
            }
        }

    /**
     * `GasFeeOrchestrator.collectPlanFee` refills `planBtc` on every keystroke from the raw field,
     * so after a clamp the cached plan still describes the pre-clamp amount. Reusing it would have
     * `validateBtcLikeAmount` check the clamped amount against a stale over-fee plan and throw
     * `insufficient_utxos_error`, defeating the fix for hand-typed full-balance UTXO sends.
     */
    @Test
    fun `submit re-plans a clamped UTXO send instead of reusing the pre-clamp plan`() {
        try {
            runTest {
                mockkStatic(Dispatchers::class)
                every { Dispatchers.IO } returns mainDispatcher
                try {
                    val btcCoin = btcCoin()
                    val account =
                        Account(
                            token = btcCoin,
                            tokenValue = TokenValue(BigInteger.valueOf(1_000_000L), btcCoin),
                            fiatValue = null,
                            price = null,
                        )
                    vaultId = "vault-1"
                    selectedAccount = account
                    addressFieldState.setTextAndPlaceCursorAtEnd("bc1qdest")
                    tokenAmountFieldState.setTextAndPlaceCursorAtEnd("0.01")
                    coEvery { accountValidator.validate() } returns
                        ValidatedAccount(
                            vaultId = "vault-1",
                            selectedAccount = account,
                            chain = Chain.Bitcoin,
                            gasFee = TokenValue(BigInteger.valueOf(2_000L), btcCoin),
                            dstAddress = "bc1qdest",
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
                            BlockChainSpecific.UTXO(byteFee = BigInteger.TEN, sendMaxAmount = false)
                        )
                    every { amountManager.currentMaxAmount } returns BigDecimal.ZERO
                    coEvery { getAvailableTokenBalance(any(), any()) } returns
                        TokenValue(BigInteger.valueOf(998_000L), btcCoin)
                    // Stale plan for the pre-clamp amount, already cached by collectPlanFee.
                    planBtc.value = okPlan(2_000L)
                    coEvery {
                        bitcoinPlanService.getPlan(
                            vaultId = any(),
                            selectedToken = any(),
                            dstAddress = any(),
                            tokenAmountInt = any(),
                            specific = any(),
                            memo = any(),
                        )
                    } returns okPlan(1_800L)
                    coEvery { gasFeeToEstimatedFee(any()) } returns
                        EstimatedGasFee(
                            formattedFiatValue = "$1.00",
                            formattedTokenValue = "0.000018 BTC",
                            tokenValue = TokenValue(BigInteger.ONE, btcCoin),
                            fiatValue = mockk(relaxed = true),
                        )

                    val captured = slot<Transaction>()
                    coEvery { transactionRepository.addTransaction(capture(captured)) } returns Unit

                    build(this).submit()
                    advanceUntilIdle()

                    assertNull(lastError, "Expected no error; got $lastError")
                    assertEquals(BigInteger.valueOf(998_000L), captured.captured.tokenValue.value)
                    // Re-planned for the clamped amount rather than trusting the cached plan.
                    coVerify {
                        bitcoinPlanService.getPlan(
                            vaultId = any(),
                            selectedToken = any(),
                            dstAddress = any(),
                            tokenAmountInt = BigInteger.valueOf(998_000L),
                            specific = any(),
                            memo = any(),
                        )
                    }
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
     * A clamp ratio that does not divide cleanly must still yield a bounded fiat string rather than
     * an 18-digit tail, and the field and the persisted Transaction must agree on it.
     */
    @Test
    fun `submit bounds the adjusted fiat when the clamp ratio does not divide evenly`() = runTest {
        mockkStatic(Dispatchers::class)
        every { Dispatchers.IO } returns mainDispatcher
        try {
            val ethCoin = ethCoin()
            val account =
                Account(
                    token = ethCoin,
                    tokenValue = TokenValue(BigInteger("1000000000000000000"), ethCoin),
                    fiatValue = null,
                    price = null,
                )
            vaultId = "vault-1"
            selectedAccount = account
            addressFieldState.setTextAndPlaceCursorAtEnd("0xdest")
            tokenAmountFieldState.setTextAndPlaceCursorAtEnd("1")
            fiatAmountFieldState.setTextAndPlaceCursorAtEnd("2000")
            coEvery { accountValidator.validate() } returns
                ValidatedAccount(
                    vaultId = "vault-1",
                    selectedAccount = account,
                    chain = Chain.Ethereum,
                    gasFee = TokenValue(BigInteger("1500000000000001"), ethCoin),
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
            // 998499999999999999 / 1e18 — a ratio with no clean decimal expansion.
            coEvery { getAvailableTokenBalance(any(), any()) } returns
                TokenValue(BigInteger("998499999999999999"), ethCoin)
            coEvery { gasFeeToEstimatedFee(any()) } returns
                EstimatedGasFee(
                    formattedFiatValue = "$0.10",
                    formattedTokenValue = "0.0015 ETH",
                    tokenValue = TokenValue(BigInteger.ONE, ethCoin),
                    fiatValue = mockk(relaxed = true),
                )

            val captured = slot<Transaction>()
            coEvery { transactionRepository.addTransaction(capture(captured)) } returns Unit

            build(this).submit()
            advanceUntilIdle()

            assertNull(lastError, "Expected no error; got $lastError")
            val fiatText = fiatAmountFieldState.text.toString()
            // Bounded to the token's own precision, and the field and the tx never disagree.
            assertTrue(
                (fiatText.substringAfter('.', "").length) <= ethCoin.decimal,
                "Adjusted fiat kept an unbounded tail: $fiatText",
            )
            assertEquals(BigDecimal(fiatText), captured.captured.fiatValue.value)
        } finally {
            unmockkStatic(Dispatchers::class)
        }
    }

    /**
     * `saveGasSettings` never clears what it stored, so a `GasSettings.Eth` set on an EVM chain
     * survives a switch to a non-EVM one. Its wei-denominated `maxFeePerGasWei × gasLimit` read as
     * satoshis would swallow the entire balance and fail every BTC send as "insufficient".
     */
    @Test
    fun `submit ignores stale EVM gas settings on a non-EVM chain`() {
        try {
            runTest {
                mockkStatic(Dispatchers::class)
                every { Dispatchers.IO } returns mainDispatcher
                try {
                    val btcCoin = btcCoin()
                    val account =
                        Account(
                            token = btcCoin,
                            tokenValue = TokenValue(BigInteger.valueOf(1_000_000L), btcCoin),
                            fiatValue = null,
                            price = null,
                        )
                    vaultId = "vault-1"
                    selectedAccount = account
                    addressFieldState.setTextAndPlaceCursorAtEnd("bc1qdest")
                    tokenAmountFieldState.setTextAndPlaceCursorAtEnd("0.005")
                    coEvery { accountValidator.validate() } returns
                        ValidatedAccount(
                            vaultId = "vault-1",
                            selectedAccount = account,
                            chain = Chain.Bitcoin,
                            gasFee = TokenValue(BigInteger.valueOf(2_000L), btcCoin),
                            dstAddress = "bc1qdest",
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
                            BlockChainSpecific.UTXO(byteFee = BigInteger.TEN, sendMaxAmount = false)
                        )
                    every { amountManager.currentMaxAmount } returns BigDecimal.ZERO
                    // Left over from an earlier Ethereum send: 0.0042 ETH worth of wei.
                    gasSettings.value =
                        GasSettings.Eth(
                            baseFee = BigInteger.valueOf(198_000_000_000L),
                            priorityFee = BigInteger.valueOf(2_000_000_000L),
                            gasLimit = BigInteger.valueOf(21_000),
                        )
                    // Honest about whichever fee it is handed — a wei-scale one zeroes the balance.
                    coEvery { getAvailableTokenBalance(any(), any()) } answers
                        {
                            TokenValue(
                                (BigInteger.valueOf(1_000_000L) - secondArg<BigInteger>())
                                    .coerceAtLeast(BigInteger.ZERO),
                                btcCoin,
                            )
                        }
                    // collectPlanFee fills both together before submit ever runs.
                    planBtc.value = okPlan(2_000L)
                    planFee.value = 2_000L
                    // Submit always re-plans (#5504), so even this no-clamp case calls getPlan —
                    // stub it to mirror the already-cached plan since nothing here should change
                    // it.
                    coEvery {
                        bitcoinPlanService.getPlan(
                            vaultId = any(),
                            selectedToken = any(),
                            dstAddress = any(),
                            tokenAmountInt = any(),
                            specific = any(),
                            memo = any(),
                        )
                    } returns okPlan(2_000L)
                    coEvery { gasFeeToEstimatedFee(any()) } returns
                        EstimatedGasFee(
                            formattedFiatValue = "$1.00",
                            formattedTokenValue = "0.00002 BTC",
                            tokenValue = TokenValue(BigInteger.ONE, btcCoin),
                            fiatValue = mockk(relaxed = true),
                        )

                    val captured = slot<Transaction>()
                    coEvery { transactionRepository.addTransaction(capture(captured)) } returns Unit

                    build(this).submit()
                    advanceUntilIdle()

                    assertNull(lastError, "Expected no error; got $lastError")
                    // Untouched: 0.005 BTC, sized against the 2000 sat fee, not the EVM leftovers.
                    assertEquals(BigInteger.valueOf(500_000L), captured.captured.tokenValue.value)
                    assertEquals("0.005", tokenAmountFieldState.text.toString())
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
     * The motivating case for deferring the field write: a balance so close to the fee that
     * `balance − fee` lands under Cardano's 1.4 ADA minimum-send floor. The clamp succeeds,
     * `validateCardanoUTXORequirements` then rejects it, and the fields must still hold what the
     * user typed rather than an amount the send never accepted.
     */
    @Test
    fun `submit surfaces the Cardano minimum-send floor on a clamped amount and keeps the typed fields`() {
        try {
            runTest {
                mockkStatic(Dispatchers::class)
                every { Dispatchers.IO } returns mainDispatcher
                try {
                    val adaCoin = adaCoin()
                    // 1.5 ADA held, 1.45 ADA fee — the clamp lands at 0.05 ADA.
                    val account =
                        Account(
                            token = adaCoin,
                            tokenValue = TokenValue(BigInteger.valueOf(1_500_000L), adaCoin),
                            fiatValue = null,
                            price = null,
                        )
                    vaultId = "vault-1"
                    selectedAccount = account
                    addressFieldState.setTextAndPlaceCursorAtEnd("addr1dest")
                    tokenAmountFieldState.setTextAndPlaceCursorAtEnd("1.5")
                    fiatAmountFieldState.setTextAndPlaceCursorAtEnd("0.6")
                    coEvery { accountValidator.validate() } returns
                        ValidatedAccount(
                            vaultId = "vault-1",
                            selectedAccount = account,
                            chain = Chain.Cardano,
                            gasFee = TokenValue(BigInteger.valueOf(1_450_000L), adaCoin),
                            dstAddress = "addr1dest",
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
                            BlockChainSpecific.Cardano(
                                byteFee = 1_450_000L,
                                sendMaxAmount = false,
                                ttl = 1_000UL,
                            )
                        )
                    every { amountManager.currentMaxAmount } returns BigDecimal.ZERO
                    coEvery { getAvailableTokenBalance(any(), any()) } returns
                        TokenValue(BigInteger.valueOf(50_000L), adaCoin)

                    build(this).submit()
                    advanceUntilIdle()

                    assertEquals(
                        R.string.minimum_send_amount_is_ada,
                        (lastError as UiText.FormattedText).resId,
                    )
                    assertEquals("1.5", tokenAmountFieldState.text.toString())
                    assertEquals("0.6", fiatAmountFieldState.text.toString())
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

    private fun adaCoin(): Coin =
        Coin(
            chain = Chain.Cardano,
            ticker = "ADA",
            logo = "",
            address = "addr1self",
            decimal = 6,
            hexPublicKey = "",
            priceProviderID = "cardano",
            contractAddress = "",
            isNativeToken = true,
        )

    private fun okPlan(fee: Long): Bitcoin.TransactionPlan =
        Bitcoin.TransactionPlan.newBuilder().setFee(fee).setError(SigningError.OK).build()

    /**
     * Arranges a straightforward ETH native-token submit that reaches transactionRepository without
     * error, returning the captured Transaction slot. [blockChainSpecific] is what getSpecific()
     * returns before any gasSettings override is applied by the strategy itself.
     */
    private fun arrangeSuccessfulEthSubmit(
        ethCoin: Coin,
        blockChainSpecific: BlockChainSpecific.Ethereum,
    ): CapturingSlot<Transaction> {
        val account =
            Account(
                token = ethCoin,
                tokenValue = TokenValue(BigInteger.valueOf(1_000_000_000_000_000_000L), ethCoin),
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
        } returns BlockChainSpecificAndUtxo(blockChainSpecific)
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
        return captured
    }

    private fun btcCoin(): Coin =
        Coin(
            chain = Chain.Bitcoin,
            ticker = "BTC",
            logo = "",
            address = "bc1qself",
            decimal = 8,
            hexPublicKey = "",
            priceProviderID = "bitcoin",
            contractAddress = "",
            isNativeToken = true,
        )

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

    private fun build(
        scope: CoroutineScope,
        bitcoinPlanService: BitcoinPlanService = this.bitcoinPlanService,
        planBtc: MutableStateFlow<Bitcoin.TransactionPlan?> = this.planBtc,
    ) =
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
            bitcoinPlanService = bitcoinPlanService,
            getAvailableTokenBalance = getAvailableTokenBalance,
            gasFeeToEstimatedFee = gasFeeToEstimatedFee,
            chainValidationService = ChainValidationService(rippleApi = rippleApi),
            addressManager = addressManager,
            amountManager = amountManager,
            gasSettings = gasSettings,
            planBtc = planBtc,
            planFee = planFee,
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
