@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.send

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import com.vultisig.wallet.data.api.TronApi
import com.vultisig.wallet.data.api.models.TronAccountJson
import com.vultisig.wallet.data.api.models.TronAccountResourceJson
import com.vultisig.wallet.data.api.models.TronChainParameterJson
import com.vultisig.wallet.data.api.models.TronChainParametersJson
import com.vultisig.wallet.data.blockchain.FeeServiceComposite
import com.vultisig.wallet.data.blockchain.tron.TronFeeService
import com.vultisig.wallet.data.blockchain.tron.TronResourceType
import com.vultisig.wallet.data.blockchain.tron.TronStakingOperation
import com.vultisig.wallet.data.blockchain.tron.tronStakingMemo
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.repositories.BlockChainSpecificAndUtxo
import com.vultisig.wallet.data.repositories.TokenRepository
import com.vultisig.wallet.data.usecases.GetAvailableTokenBalanceUseCase
import com.vultisig.wallet.ui.screens.v2.defi.model.DeFiNavActions
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.mockk
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class AmountFractionManagerTest {

    private val scheduler = TestCoroutineScheduler()
    private val mainDispatcher = UnconfinedTestDispatcher(scheduler)

    private val tokenAmountFieldState = TextFieldState()
    private val addressFieldState = TextFieldState()
    private val memoFieldState = TextFieldState()
    private val uiState = MutableStateFlow(SendFormUiModel())
    private val gasFee = MutableStateFlow<TokenValue?>(null)
    private val gasSettings = MutableStateFlow<GasSettings?>(null)
    private val specific = MutableStateFlow<BlockChainSpecificAndUtxo?>(null)

    private var defiType: DeFiNavActions? = null
    private var vault: Vault? = vault()
    private var account: Account? = null
    private var tronFrozen: BigDecimal? = null

    private val tronApi: TronApi =
        mockk(relaxed = true) {
            coEvery { getChainParameters() } returns
                TronChainParametersJson(
                    listOf(
                        TronChainParameterJson("getTransactionFee", 1000L),
                        TronChainParameterJson("getCreateAccountFee", 100000L),
                        TronChainParameterJson("getCreateNewAccountFeeInSystemContract", 1000000L),
                        TronChainParameterJson("getMemoFee", 1000000L),
                    )
                )
            coEvery { getAccountResource(any()) } returns
                TronAccountResourceJson(freeNetLimit = 5_000L)
            coEvery { getAccount(any()) } returns TronAccountJson(address = "T...")
        }

    private val getAvailableTokenBalance: GetAvailableTokenBalanceUseCase = mockk(relaxed = true)
    private val feeServiceComposite: FeeServiceComposite = mockk(relaxed = true)
    private val tokenRepository: TokenRepository = mockk(relaxed = true)
    private val amountManager: AmountManager = mockk(relaxed = true)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ──────── chooseMaxTokenAmount ────────

    @Test
    fun `chooseMaxTokenAmount UNFREEZE_TRX while loading - early returns without state mutation`() =
        runTest(mainDispatcher) {
            defiType = DeFiNavActions.UNFREEZE_TRX
            uiState.value = uiState.value.copy(isTronFrozenBalancesLoading = true)
            val manager = build(backgroundScope)

            manager.chooseMaxTokenAmount()
            advanceUntilIdle()

            // No fraction selected, no token text written, no markMax called.
            assertNull(uiState.value.selectedAmountFraction)
            assertEquals("", tokenAmountFieldState.text.toString())
            confirmVerified(amountManager)
        }

    @Test
    fun `chooseMaxTokenAmount with no vault - clears loading flag and writes empty amount`() =
        runTest(mainDispatcher) {
            vault = null
            account = ethAccount()
            val manager = build(backgroundScope)

            manager.chooseMaxTokenAmount()
            advanceUntilIdle()

            // The launch ran, set the fraction, and the finally block reset the loading flag.
            assertEquals(AmountFraction.F100, uiState.value.selectedAmountFraction)
            assertFalse(uiState.value.isAmountSelectionLoading)
            // Vault-null short-circuits to BigDecimal.ZERO → "0".
            assertEquals("0", tokenAmountFieldState.text.toString())
        }

    @Test
    fun `chooseMaxTokenAmount with no account - clears loading flag and writes empty amount`() =
        runTest(mainDispatcher) {
            account = null
            val manager = build(backgroundScope)

            manager.chooseMaxTokenAmount()
            advanceUntilIdle()

            assertEquals(AmountFraction.F100, uiState.value.selectedAmountFraction)
            assertFalse(uiState.value.isAmountSelectionLoading)
            assertEquals("0", tokenAmountFieldState.text.toString())
        }

    @Test
    fun `chooseMaxTokenAmount UNFREEZE_TRX - writes percentage of frozen balance and marks max`() =
        runTest(mainDispatcher) {
            defiType = DeFiNavActions.UNFREEZE_TRX
            account = trxAccount()
            tronFrozen = BigDecimal("123.456789")
            val manager = build(backgroundScope)

            manager.chooseMaxTokenAmount()
            advanceUntilIdle()

            // 123.456789 * 1.0 → "123.456789" (TRX has 6 decimals; setScale + stripTrailingZeros).
            assertEquals("123.456789", tokenAmountFieldState.text.toString())
            coVerify { amountManager.markMax(BigDecimal("123.456789")) }
        }

    @Test
    fun `chooseMaxTokenAmount EVM with non-null gasFee - short-circuits without fee recalculation`() =
        runTest(mainDispatcher) {
            // defiType is null → falls past the DeFi guard. EVM + gasFee != null short-circuits
            // before any IO call (feeService / tokenRepository must NOT be touched).
            account = ethAccount()
            gasFee.value = TokenValue(value = BigInteger("100"), token = ethAccount().token)
            coEvery { getAvailableTokenBalance(any(), any()) } returns
                TokenValue(value = BigInteger("2000000000000000000"), token = ethAccount().token)
            val manager = build(backgroundScope)

            manager.chooseMaxTokenAmount()
            advanceUntilIdle()

            // 2 * 1.0 ETH → fetchPercentageOfAvailableBalance returns the stubbed 2e18 wei,
            // scaled down by token decimals (18) to "2".
            assertEquals("2", tokenAmountFieldState.text.toString())
            coVerify { amountManager.markMax(any()) }
            coVerify(exactly = 0) { feeServiceComposite.calculateFees(any()) }
            coVerify(exactly = 0) { tokenRepository.getNativeToken(any()) }
        }

    @Test
    fun `chooseMaxTokenAmount non-native token - short-circuits without fee recalculation`() =
        runTest(mainDispatcher) {
            // Non-native SPL token on a non-EVM chain (USDC on Solana). Chain gas is paid in
            // the native coin (SOL) and never reduces the USDC balance, so the chip must not
            // block on a fresh-fee RPC.
            val usdc = usdcSolanaAccount()
            account = usdc
            gasFee.value = TokenValue(value = BigInteger("5000"), token = solAccount().token)
            coEvery { getAvailableTokenBalance(any(), any()) } returns
                TokenValue(value = BigInteger("12500000"), token = usdc.token)
            val manager = build(backgroundScope)

            manager.chooseMaxTokenAmount()
            advanceUntilIdle()

            // 12.5 USDC × 1.0 → 12.5 (6 decimals, trailing zeros stripped).
            assertEquals("12.5", tokenAmountFieldState.text.toString())
            coVerify { amountManager.markMax(BigDecimal("12.5")) }
            coVerify(exactly = 0) { feeServiceComposite.calculateFees(any()) }
            coVerify(exactly = 0) { tokenRepository.getNativeToken(any()) }
        }

    @Test
    fun `chooseMaxTokenAmount FREEZE_TRX - reserves the real fee, not the staking memo's`() =
        runTest(mainDispatcher) {
            // End-to-end over the real TronFeeService: the freeze form parks its routing signal in
            // the memo field, which used to buy a ~1 TRX memo fee the chain never charges and which
            // was then reserved out of the freezable balance (issue #5481).
            defiType = DeFiNavActions.FREEZE_TRX
            val trx = trxAccount().let { it.copy(tokenValue = TokenValue(BALANCE, it.token)) }
            account = trx
            addressFieldState.setTextAndPlaceCursorAtEnd(trx.token.address)
            memoFieldState.setTextAndPlaceCursorAtEnd(
                tronStakingMemo(TronStakingOperation.FREEZE, TronResourceType.BANDWIDTH)
            )
            // Seed the stale phantom so the assertions below fail if the recompute reinstates it.
            gasFee.value = TokenValue(value = BigInteger("1000000"), token = trx.token)
            coEvery { feeServiceComposite.calculateFees(any()) } coAnswers
                {
                    TronFeeService(tronApi).calculateFees(firstArg())
                }
            // Mirrors GetAvailableTokenBalanceUseCase for a native coin: balance minus the fee.
            coEvery { getAvailableTokenBalance(any(), any()) } answers
                {
                    TokenValue(BALANCE - secondArg<BigInteger>(), trx.token)
                }
            val manager = build(backgroundScope)

            manager.chooseMaxTokenAmount()

            // Free bandwidth covers the transfer and the destination is the sender's own,
            // already-activated account, so the whole 100 TRX is freezable.
            assertEquals("100", awaitAmountField())
            assertEquals(BigInteger.ZERO, gasFee.value?.value)
        }

    /**
     * The percentage recompute hops to [Dispatchers.IO], which [runTest]'s scheduler cannot
     * advance, so wait on the real clock for the amount it finally writes.
     */
    private suspend fun awaitAmountField(): String =
        withContext(Dispatchers.Default) {
            withTimeout(10_000) {
                var text = tokenAmountFieldState.text.toString()
                while (text.isEmpty()) {
                    delay(10)
                    text = tokenAmountFieldState.text.toString()
                }
                text
            }
        }

    // ──────── choosePercentageAmount ────────

    @Test
    fun `choosePercentageAmount UNFREEZE_TRX while loading - early returns without state mutation`() =
        runTest(mainDispatcher) {
            defiType = DeFiNavActions.UNFREEZE_TRX
            uiState.value = uiState.value.copy(isTronFrozenBalancesLoading = true)
            val manager = build(backgroundScope)

            manager.choosePercentageAmount(AmountFraction.F50)
            advanceUntilIdle()

            assertNull(uiState.value.selectedAmountFraction)
            assertEquals("", tokenAmountFieldState.text.toString())
        }

    @Test
    fun `choosePercentageAmount UNFREEZE_TRX - writes fraction of frozen balance and does NOT markMax`() =
        runTest(mainDispatcher) {
            defiType = DeFiNavActions.UNFREEZE_TRX
            account = trxAccount()
            tronFrozen = BigDecimal("100")
            val manager = build(backgroundScope)

            manager.choosePercentageAmount(AmountFraction.F50)
            advanceUntilIdle()

            assertEquals(AmountFraction.F50, uiState.value.selectedAmountFraction)
            assertFalse(uiState.value.isAmountSelectionLoading)
            assertEquals("50", tokenAmountFieldState.text.toString())
            // Only chooseMaxTokenAmount markMax-es the result; the percentage path does not.
            coVerify(exactly = 0) { amountManager.markMax(any()) }
        }

    @Test
    fun `choosePercentageAmount with no vault - clears loading flag and selects fraction`() =
        runTest(mainDispatcher) {
            vault = null
            account = ethAccount()
            val manager = build(backgroundScope)

            manager.choosePercentageAmount(AmountFraction.F75)
            advanceUntilIdle()

            assertEquals(AmountFraction.F75, uiState.value.selectedAmountFraction)
            assertFalse(uiState.value.isAmountSelectionLoading)
            assertEquals("0", tokenAmountFieldState.text.toString())
        }

    @Test
    fun `choosePercentageAmount non-native token - short-circuits without fee recalculation`() =
        runTest(mainDispatcher) {
            val usdc = usdcSolanaAccount()
            account = usdc
            gasFee.value = TokenValue(value = BigInteger("5000"), token = solAccount().token)
            coEvery { getAvailableTokenBalance(any(), any()) } returns
                TokenValue(value = BigInteger("10000000"), token = usdc.token)
            val manager = build(backgroundScope)

            manager.choosePercentageAmount(AmountFraction.F50)
            advanceUntilIdle()

            // 10 USDC × 0.5 → "5".
            assertEquals(AmountFraction.F50, uiState.value.selectedAmountFraction)
            assertFalse(uiState.value.isAmountSelectionLoading)
            assertEquals("5", tokenAmountFieldState.text.toString())
            coVerify(exactly = 0) { feeServiceComposite.calculateFees(any()) }
            coVerify(exactly = 0) { tokenRepository.getNativeToken(any()) }
            coVerify(exactly = 0) { amountManager.markMax(any()) }
        }

    @Test
    fun `choosePercentageAmount non-native token with null gasFee - short-circuits without fee recalculation`() =
        runTest(mainDispatcher) {
            // gasFee.value stays null. For non-native tokens this is still safe: the fee never
            // reduces the selected balance, and GasFeeOrchestrator refreshes the fee in the
            // background once the amount field is populated.
            val usdc = usdcSolanaAccount()
            account = usdc
            coEvery { getAvailableTokenBalance(any(), any()) } returns
                TokenValue(value = BigInteger("8000000"), token = usdc.token)
            val manager = build(backgroundScope)

            manager.choosePercentageAmount(AmountFraction.F75)
            advanceUntilIdle()

            // 8 USDC × 0.75 → "6".
            assertEquals(AmountFraction.F75, uiState.value.selectedAmountFraction)
            assertFalse(uiState.value.isAmountSelectionLoading)
            assertEquals("6", tokenAmountFieldState.text.toString())
            coVerify(exactly = 0) { feeServiceComposite.calculateFees(any()) }
            coVerify(exactly = 0) { tokenRepository.getNativeToken(any()) }
        }

    @Test
    fun `choosePercentageAmount cancels in-flight job when invoked twice - latest call wins`() =
        runTest(mainDispatcher) {
            // The ensureActive() guard inside the manager makes this deterministic: the first
            // job's continuation, after cancel(), throws CancellationException at the
            // post-finally checkpoint and never reaches setTextAndPlaceCursorAtEnd.
            account = ethAccount()
            gasFee.value = TokenValue(value = BigInteger("0"), token = ethAccount().token)
            coEvery { getAvailableTokenBalance(any(), any()) } returns
                TokenValue(value = BigInteger("1000000000000000000"), token = ethAccount().token)
            val manager = build(backgroundScope)

            manager.choosePercentageAmount(AmountFraction.F25)
            manager.choosePercentageAmount(AmountFraction.F75)
            advanceUntilIdle()

            // The latest invocation always wins — F25 is never an acceptable end-state.
            assertEquals(AmountFraction.F75, uiState.value.selectedAmountFraction)
            // Only chooseMaxTokenAmount calls markMax, never the percentage path.
            coVerify(exactly = 0) { amountManager.markMax(any()) }
        }

    @Test
    fun `cancel is safe to call when no job is in-flight and clears the held reference`() =
        runTest(mainDispatcher) {
            // The Tron resource toggle calls cancel() unconditionally on every invocation,
            // including the first one before any percentage calc has run. Must not throw.
            val manager = build(backgroundScope)

            manager.cancel()
            manager.cancel() // idempotent

            // Subsequent percentage calls still work after cancel.
            account = ethAccount()
            gasFee.value = TokenValue(value = BigInteger("0"), token = ethAccount().token)
            coEvery { getAvailableTokenBalance(any(), any()) } returns
                TokenValue(value = BigInteger("1000000000000000000"), token = ethAccount().token)

            manager.choosePercentageAmount(AmountFraction.F50)
            advanceUntilIdle()

            assertEquals(AmountFraction.F50, uiState.value.selectedAmountFraction)
        }

    // ──────── helpers ────────

    private fun build(scope: CoroutineScope) =
        AmountFractionManager(
            scope = scope,
            tokenAmountFieldState = tokenAmountFieldState,
            addressFieldState = addressFieldState,
            memoFieldState = memoFieldState,
            uiState = uiState,
            gasFee = gasFee,
            gasSettings = gasSettings,
            specific = specific,
            defiTypeProvider = { defiType },
            vaultProvider = { vault },
            accountProvider = { account },
            currentTronFrozenBalanceProvider = { tronFrozen },
            getAvailableTokenBalance = getAvailableTokenBalance,
            feeServiceComposite = feeServiceComposite,
            tokenRepository = tokenRepository,
            adjustGasFee = { gas, _, _ -> gas },
            amountManager = amountManager,
        )

    private fun vault(): Vault =
        Vault(
            id = "vault-id",
            name = "test",
            hexChainCode = "00",
            pubKeyECDSA = "",
            pubKeyEDDSA = "",
        )

    private fun ethAccount(): Account {
        val token =
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
        return Account(
            token = token,
            tokenValue = TokenValue(value = BigInteger("2000000000000000000"), token = token),
            fiatValue = null,
            price = null,
        )
    }

    private fun trxAccount(): Account {
        val token =
            Coin(
                chain = Chain.Tron,
                ticker = "TRX",
                logo = "",
                address = "T...",
                decimal = 6,
                hexPublicKey = "",
                priceProviderID = "tron",
                contractAddress = "",
                isNativeToken = true,
            )
        return Account(
            token = token,
            tokenValue = TokenValue(value = BigInteger("0"), token = token),
            fiatValue = null,
            price = null,
        )
    }

    private fun solAccount(): Account {
        val token =
            Coin(
                chain = Chain.Solana,
                ticker = "SOL",
                logo = "",
                address = "Sol...",
                decimal = 9,
                hexPublicKey = "",
                priceProviderID = "solana",
                contractAddress = "",
                isNativeToken = true,
            )
        return Account(
            token = token,
            tokenValue = TokenValue(value = BigInteger("0"), token = token),
            fiatValue = null,
            price = null,
        )
    }

    private companion object {
        /** 100 TRX, in sun. */
        val BALANCE: BigInteger = BigInteger("100000000")
    }

    private fun usdcSolanaAccount(): Account {
        val token =
            Coin(
                chain = Chain.Solana,
                ticker = "USDC",
                logo = "",
                address = "Sol...",
                decimal = 6,
                hexPublicKey = "",
                priceProviderID = "usd-coin",
                contractAddress = "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v",
                isNativeToken = false,
            )
        return Account(
            token = token,
            tokenValue = TokenValue(value = BigInteger("10000000"), token = token),
            fiatValue = null,
            price = null,
        )
    }
}
