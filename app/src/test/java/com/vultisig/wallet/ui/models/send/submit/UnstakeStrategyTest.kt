@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.send.submit

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import com.vultisig.wallet.R
import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.api.models.thorchain.RujiStakeBalances
import com.vultisig.wallet.data.chains.helpers.ThorchainFunctions
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.DepositTransaction
import com.vultisig.wallet.data.models.EstimatedGasFee
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.data.repositories.BlockChainSpecificAndUtxo
import com.vultisig.wallet.data.repositories.BlockChainSpecificRepository
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.data.repositories.DepositTransactionRepository
import com.vultisig.wallet.data.usecases.GasFeeToEstimatedFeeUseCase
import com.vultisig.wallet.data.usecases.GetAvailableTokenBalanceUseCase
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.screens.v2.defi.STAKING_BRUNE_CONTRACT
import com.vultisig.wallet.ui.screens.v2.defi.STAKING_RUJI_CONTRACT
import com.vultisig.wallet.ui.screens.v2.defi.model.DeFiNavActions
import com.vultisig.wallet.ui.utils.UiText
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.unmockkStatic
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import vultisig.keysign.v1.TransactionType
import vultisig.keysign.v1.WasmExecuteContractPayload

internal class UnstakeStrategyTest {

    private val scheduler = TestCoroutineScheduler()
    private val mainDispatcher = UnconfinedTestDispatcher(scheduler)

    private val tokenAmountFieldState = TextFieldState()

    private val accountValidator: AccountValidator = mockk()
    private val chainAccountAddressRepository: ChainAccountAddressRepository = mockk()
    private val accountsRepository: AccountsRepository = mockk()
    private val blockChainSpecificRepository: BlockChainSpecificRepository = mockk()
    private val getAvailableTokenBalance: GetAvailableTokenBalanceUseCase = mockk()
    private val gasFeeToEstimatedFee: GasFeeToEstimatedFeeUseCase = mockk()
    private val depositTransactionRepository: DepositTransactionRepository = mockk(relaxed = true)
    private val navigator: Navigator<Destination> = mockk(relaxed = true)
    private val thorChainApi: ThorChainApi = mockk(relaxed = true)

    private var lastError: UiText? = null

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        mockkObject(ThorchainFunctions)
        every {
            ThorchainFunctions.unstakeRUJI(
                fromAddress = any(),
                amount = any(),
                stakingContract = any(),
            )
        } answers
            {
                WasmExecuteContractPayload(
                    senderAddress = arg(0),
                    contractAddress = arg(2),
                    executeMsg = "withdraw-msg",
                    coins = emptyList(),
                )
            }
        every {
            ThorchainFunctions.claimRujiRewards(fromAddress = any(), stakingContract = any())
        } answers
            {
                WasmExecuteContractPayload(
                    senderAddress = arg(0),
                    contractAddress = arg(1),
                    executeMsg = "claim-rewards-msg",
                    coins = emptyList(),
                )
            }
        every { ThorchainFunctions.rujiRewardsMemo(any(), any()) } returns "rewards-memo"
        every { ThorchainFunctions.tcyUnstakeMemo(any()) } answers { "tcy-unstake:${arg<Int>(0)}" }
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
        Dispatchers.resetMain()
    }

    @Test
    fun `submit on WITHDRAW_RUJI surfaces no_token error when no RUJI account exists`() = runTest {
        // Setup: balance/RUNE checks pass, but the second loadAddresses lookup
        // for the RUJI account returns no match — the strategy must surface a
        // user-facing error rather than silently no-op.
        givenValidatedAccount()
        tokenAmountFieldState.setTextAndPlaceCursorAtEnd("0.5")
        coEvery { chainAccountAddressRepository.isValid(any(), any()) } returns true
        coEvery { getAvailableTokenBalance(any(), any()) } returns
            TokenValue(BigInteger.valueOf(2_000_000_000), Coins.ThorChain.RUNE)
        // RUNE account has plenty for gas. RUJI is absent.
        every { accountsRepository.loadAddresses(VAULT_ID) } returns
            flowOf(listOf(addressWithRune(BigInteger.valueOf(2_000_000_000))))

        build(this, DeFiNavActions.WITHDRAW_RUJI).submit()
        advanceUntilIdle()

        assertEquals(R.string.send_error_no_token, lastError.stringId())
        coVerify(exactly = 0) { depositTransactionRepository.addTransaction(any()) }
        coVerify(exactly = 0) { navigator.route(any(), any()) }
    }

    @Test
    fun `submit surfaces insufficient_balance when RUNE is below gasFee`() = runTest {
        givenValidatedAccount(gasFeeValue = BigInteger.valueOf(10_000_000))
        tokenAmountFieldState.setTextAndPlaceCursorAtEnd("0.5")
        coEvery { chainAccountAddressRepository.isValid(any(), any()) } returns true
        every { accountsRepository.loadAddresses(VAULT_ID) } returns
            flowOf(listOf(addressWithRune(BigInteger.valueOf(1_000))))

        build(this, DeFiNavActions.UNSTAKE_RUJI).submit()
        advanceUntilIdle()

        assertEquals(R.string.send_error_insufficient_balance, lastError.stringId())
    }

    @Test
    fun `submit UNSTAKE_RUJI persists deposit with withdraw memo and ruji staking contract`() =
        runTest {
            withMockedIoDispatcher {
                givenSuccessfulFlow()
                tokenAmountFieldState.setTextAndPlaceCursorAtEnd("0.5")

                val captured = slot<DepositTransaction>()
                coEvery { depositTransactionRepository.addTransaction(capture(captured)) } returns
                    Unit

                build(this, DeFiNavActions.UNSTAKE_RUJI).submit()
                advanceUntilIdle()

                val tx = captured.captured
                assertEquals("withdraw:ruji-contract:50000000", tx.memo)
                assertNotNull(tx.wasmExecuteContractPayload)
                assertEquals(STAKING_RUJI_CONTRACT, tx.wasmExecuteContractPayload!!.contractAddress)
            }
        }

    @Test
    fun `submit UNSTAKE_TCY persists deposit with basis-point memo when not autocompounding`() =
        runTest {
            withMockedIoDispatcher {
                givenSuccessfulFlow()
                tokenAmountFieldState.setTextAndPlaceCursorAtEnd("0.5")

                val captured = slot<DepositTransaction>()
                coEvery { depositTransactionRepository.addTransaction(capture(captured)) } returns
                    Unit

                build(this, DeFiNavActions.UNSTAKE_TCY).submit()
                advanceUntilIdle()

                // Non-autocompound TCY unstake encodes basis points via tcyUnstakeMemo;
                // 0.5/avail (0.5/availableTokenBalance) maps to ~5000 bps under the test fixture.
                assertEquals("tcy-unstake:5000", captured.captured.memo)
            }
        }

    @Test
    fun `submit WITHDRAW_RUJI persists rewards-claim deposit when RUJI account exists`() = runTest {
        withMockedIoDispatcher {
            givenSuccessfulFlow(includeRuji = true)
            tokenAmountFieldState.setTextAndPlaceCursorAtEnd("0.5")

            val captured = slot<DepositTransaction>()
            coEvery { depositTransactionRepository.addTransaction(capture(captured)) } returns Unit

            build(this, DeFiNavActions.WITHDRAW_RUJI).submit()
            advanceUntilIdle()

            val tx = captured.captured
            assertEquals("rewards-memo", tx.memo)
            assertNotNull(tx.wasmExecuteContractPayload)
            assertEquals(STAKING_RUJI_CONTRACT, tx.wasmExecuteContractPayload!!.contractAddress)
        }
    }

    @Test
    fun `submit UNSTAKE_SRUJI converts the RUJI amount into receipt shares`() = runTest {
        withMockedIoDispatcher {
            givenSuccessfulFlow()
            // The card and the form are denominated in RUJI (liquidSize), but liquid.unbond is
            // funded in shares, which are fewer because the share price sits above 1.
            givenAutoCompoundPosition(
                positionValue = BigInteger.valueOf(100_000_000),
                heldShares = BigInteger.valueOf(98_500_000),
            )
            tokenAmountFieldState.setTextAndPlaceCursorAtEnd("0.5")

            val captured = slot<DepositTransaction>()
            coEvery { depositTransactionRepository.addTransaction(capture(captured)) } returns Unit

            build(this, DeFiNavActions.UNSTAKE_SRUJI).submit()
            advanceUntilIdle()

            val payload = captured.captured.wasmExecuteContractPayload
            assertNotNull(payload)
            assertEquals("""{ "liquid": { "unbond": {} } }""", payload!!.executeMsg)
            assertEquals(STAKING_RUJI_CONTRACT, payload.contractAddress)
            assertEquals("x/staking-x/ruji", payload.coins[0]!!.denom)
            // 0.5 RUJI of a 1 RUJI position = half the shares held.
            assertEquals("49250000", payload.coins[0]!!.amount)
        }
    }

    @Test
    fun `submit UNSTAKE_SRUJI redeems the exact share balance when taking the whole position`() =
        runTest {
            withMockedIoDispatcher {
                givenSuccessfulFlow()
                givenAutoCompoundPosition(
                    positionValue = BigInteger.valueOf(100_000_000),
                    heldShares = BigInteger.valueOf(98_500_000),
                )
                tokenAmountFieldState.setTextAndPlaceCursorAtEnd("1")

                val captured = slot<DepositTransaction>()
                coEvery { depositTransactionRepository.addTransaction(capture(captured)) } returns
                    Unit

                build(this, DeFiNavActions.UNSTAKE_SRUJI).submit()
                advanceUntilIdle()

                // Redeeming everything sends the share balance itself, so a rounded conversion
                // cannot strand dust in the position.
                val payload = captured.captured.wasmExecuteContractPayload
                assertEquals("98500000", payload!!.coins[0]!!.amount)
            }
        }

    @Test
    fun `submit UNSTAKE_SRUJI honours an amount the cached ceiling has fallen behind`() = runTest {
        withMockedIoDispatcher {
            givenSuccessfulFlow()
            // The form was seeded from a cached 1 RUJI position that has since compounded to 1.5.
            // Measuring the typed amount against that stale ceiling would reject a redemption the
            // live position can honour, so the live read is the authority.
            givenAutoCompoundPosition(
                positionValue = BigInteger.valueOf(150_000_000),
                heldShares = BigInteger.valueOf(147_000_000),
            )
            tokenAmountFieldState.setTextAndPlaceCursorAtEnd("1.2")

            val captured = slot<DepositTransaction>()
            coEvery { depositTransactionRepository.addTransaction(capture(captured)) } returns Unit

            build(this, DeFiNavActions.UNSTAKE_SRUJI).submit()
            advanceUntilIdle()

            val payload = captured.captured.wasmExecuteContractPayload
            assertEquals("117600000", payload!!.coins[0]!!.amount)
        }
    }

    @Test
    fun `submit UNSTAKE_SRUJI redeems and displays only what the live position still holds`() =
        runTest {
            withMockedIoDispatcher {
                givenSuccessfulFlow()
                // The cache says 1 RUJI, the live position is 0.6 — redeeming everything is right,
                // but the transaction must carry the real amount so Verify shows what the contract
                // will return rather than the larger figure that was typed.
                givenAutoCompoundPosition(
                    positionValue = BigInteger.valueOf(60_000_000),
                    heldShares = BigInteger.valueOf(59_000_000),
                )
                tokenAmountFieldState.setTextAndPlaceCursorAtEnd("1")

                val captured = slot<DepositTransaction>()
                coEvery { depositTransactionRepository.addTransaction(capture(captured)) } returns
                    Unit

                build(this, DeFiNavActions.UNSTAKE_SRUJI).submit()
                advanceUntilIdle()

                val tx = captured.captured
                assertEquals("59000000", tx.wasmExecuteContractPayload!!.coins[0]!!.amount)
                assertEquals(BigInteger.valueOf(60_000_000), tx.srcTokenValue.value)
            }
        }

    @Test
    fun `submit UNSTAKE_SRUJI refuses to build a redemption against an empty position`() = runTest {
        withMockedIoDispatcher {
            givenSuccessfulFlow()
            givenAutoCompoundPosition(positionValue = BigInteger.ZERO, heldShares = BigInteger.ZERO)
            tokenAmountFieldState.setTextAndPlaceCursorAtEnd("0.5")

            val captured = slot<DepositTransaction>()
            coEvery { depositTransactionRepository.addTransaction(capture(captured)) } returns Unit

            build(this, DeFiNavActions.UNSTAKE_SRUJI).submit()
            advanceUntilIdle()

            assertEquals(R.string.send_error_insufficient_balance, lastError.stringId())
            assertFalse(captured.isCaptured)
        }
    }

    @Test
    fun `submit UNSTAKE_SRUJI translates a failed position read instead of leaking its message`() =
        runTest {
            withMockedIoDispatcher {
                givenSuccessfulFlow()
                // The position read fails closed rather than reporting a false zero, so this is a
                // fetch failure, not an empty position — it must not reach the user as raw text.
                coEvery { thorChainApi.getRujiStakeBalance(any()) } throws
                    Exception("Could not fetch balances: status 502")
                tokenAmountFieldState.setTextAndPlaceCursorAtEnd("0.5")

                val captured = slot<DepositTransaction>()
                coEvery { depositTransactionRepository.addTransaction(capture(captured)) } returns
                    Unit

                build(this, DeFiNavActions.UNSTAKE_SRUJI).submit()
                advanceUntilIdle()

                assertEquals(R.string.dialog_default_error_body, lastError.stringId())
                assertFalse(captured.isCaptured)
            }
        }

    @Test
    fun `submit UNSTAKE_SRUJI refuses to size a redemption against an unknown share count`() =
        runTest {
            withMockedIoDispatcher {
                givenSuccessfulFlow()
                // Shares unreadable on a funded position: guessing the count could redeem the wrong
                // slice, and calling it zero would report a live position as empty.
                givenAutoCompoundPosition(
                    positionValue = BigInteger.valueOf(100_000_000),
                    heldShares = null,
                )
                tokenAmountFieldState.setTextAndPlaceCursorAtEnd("0.5")

                val captured = slot<DepositTransaction>()
                coEvery { depositTransactionRepository.addTransaction(capture(captured)) } returns
                    Unit

                build(this, DeFiNavActions.UNSTAKE_SRUJI).submit()
                advanceUntilIdle()

                assertEquals(R.string.dialog_default_error_body, lastError.stringId())
                assertFalse(captured.isCaptured)
            }
        }

    @Test
    fun `submit UNSTAKE_YBRUNE funds the unbond with the receipt units entered`() = runTest {
        withMockedIoDispatcher {
            givenSuccessfulFlow(selectedToken = ybRuneCoin())
            // The position is reported in receipt units, so what the form carries is what funds
            // the execute — no share conversion, unlike the sRUJI redemption above.
            tokenAmountFieldState.setTextAndPlaceCursorAtEnd("0.4")

            val captured = slot<DepositTransaction>()
            coEvery { depositTransactionRepository.addTransaction(capture(captured)) } returns Unit

            build(this, DeFiNavActions.UNSTAKE_YBRUNE).submit()
            advanceUntilIdle()

            val payload = captured.captured.wasmExecuteContractPayload
            assertNotNull(payload)
            assertEquals("""{ "liquid": { "unbond": {} } }""", payload!!.executeMsg)
            assertEquals(STAKING_BRUNE_CONTRACT, payload.contractAddress)
            assertEquals(Coins.ThorChain.ybRUNE.contractAddress, payload.coins[0]!!.denom)
            assertEquals("40000000", payload.coins[0]!!.amount)
            assertEquals("", captured.captured.memo)
        }
    }

    @Test
    fun `submit UNSTAKE_YBRUNE refuses an amount below one receipt unit`() = runTest {
        withMockedIoDispatcher {
            givenSuccessfulFlow(selectedToken = ybRuneCoin())
            // Rounds to zero base units at 8 decimals: the execute would carry no funds, so the
            // ceremony could only ever fail on-chain after the user paid for it.
            tokenAmountFieldState.setTextAndPlaceCursorAtEnd("0.000000001")

            val captured = slot<DepositTransaction>()
            coEvery { depositTransactionRepository.addTransaction(capture(captured)) } returns Unit

            build(this, DeFiNavActions.UNSTAKE_YBRUNE).submit()
            advanceUntilIdle()

            assertEquals(R.string.send_error_no_amount, lastError.stringId())
            assertFalse(captured.isCaptured)
        }
    }

    @Test
    fun `submit surfaces no_address when chain validates dst as invalid`() = runTest {
        givenValidatedAccount()
        coEvery { chainAccountAddressRepository.isValid(any(), any()) } returns false

        build(this, DeFiNavActions.UNSTAKE_RUJI).submit()
        advanceUntilIdle()

        assertEquals(R.string.send_error_no_address, lastError.stringId())
    }

    private inline fun withMockedIoDispatcher(block: () -> Unit) {
        mockkStatic(Dispatchers::class)
        every { Dispatchers.IO } returns mainDispatcher
        try {
            block()
        } finally {
            unmockkStatic(Dispatchers::class)
        }
    }

    private fun givenSuccessfulFlow(
        includeRuji: Boolean = false,
        selectedToken: Coin = rujiCoin(),
    ) {
        givenValidatedAccount(selectedToken = selectedToken)
        coEvery { chainAccountAddressRepository.isValid(any(), any()) } returns true
        coEvery { getAvailableTokenBalance(any(), any()) } returns
            TokenValue(BigInteger.valueOf(100_000_000), selectedToken)
        val accounts =
            mutableListOf(
                Account(
                    token = Coins.ThorChain.RUNE,
                    tokenValue =
                        TokenValue(
                            value = BigInteger.valueOf(2_000_000_000),
                            token = Coins.ThorChain.RUNE,
                        ),
                    fiatValue = null,
                    price = null,
                )
            )
        if (includeRuji) {
            accounts.add(
                Account(
                    token = Coins.ThorChain.RUJI,
                    tokenValue =
                        TokenValue(BigInteger.valueOf(1_000_000_000), Coins.ThorChain.RUJI),
                    fiatValue = null,
                    price = null,
                )
            )
        }
        every { accountsRepository.loadAddresses(VAULT_ID) } returns
            flowOf(
                listOf(Address(chain = Chain.ThorChain, address = "thor1self", accounts = accounts))
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
                transactionType = any(),
            )
        } returns
            BlockChainSpecificAndUtxo(
                BlockChainSpecific.THORChain(
                    accountNumber = BigInteger.ZERO,
                    sequence = BigInteger.ZERO,
                    fee = BigInteger.ZERO,
                    isDeposit = true,
                    transactionType = TransactionType.TRANSACTION_TYPE_GENERIC_CONTRACT,
                )
            )
        coEvery { gasFeeToEstimatedFee(any()) } returns
            EstimatedGasFee(
                formattedFiatValue = "$0.01",
                formattedTokenValue = "0.0001 RUNE",
                tokenValue = TokenValue(BigInteger.ONE, Coins.ThorChain.RUNE),
                fiatValue = mockk(relaxed = true),
            )
    }

    private fun givenAutoCompoundPosition(positionValue: BigInteger, heldShares: BigInteger?) {
        coEvery { thorChainApi.getRujiStakeBalance(any()) } returns
            RujiStakeBalances(
                stakeAmount = BigInteger.ZERO,
                stakeTicker = "RUJI",
                autoCompoundAmount = positionValue,
                autoCompoundShares = heldShares,
            )
    }

    private fun givenValidatedAccount(
        gasFeeValue: BigInteger = BigInteger.valueOf(2_000_000),
        selectedToken: Coin = rujiCoin(),
    ) {
        coEvery { accountValidator.validate() } returns
            ValidatedAccount(
                vaultId = VAULT_ID,
                selectedAccount =
                    Account(
                        token = selectedToken,
                        tokenValue = TokenValue(BigInteger.valueOf(2_000_000_000), selectedToken),
                        fiatValue = null,
                        price = null,
                    ),
                chain = Chain.ThorChain,
                gasFee = TokenValue(gasFeeValue, Coins.ThorChain.RUNE),
                dstAddress = "thor-rewards-contract",
            )
    }

    private fun addressWithRune(runeBalance: BigInteger): Address =
        Address(
            chain = Chain.ThorChain,
            address = "thor1self",
            accounts =
                listOf(
                    Account(
                        token = Coins.ThorChain.RUNE,
                        tokenValue = TokenValue(value = runeBalance, token = Coins.ThorChain.RUNE),
                        fiatValue = null,
                        price = null,
                    )
                ),
        )

    private fun build(scope: CoroutineScope, defiType: DeFiNavActions) =
        UnstakeStrategy(
            scope = scope,
            tokenAmountFieldState = tokenAmountFieldState,
            accountValidator = accountValidator,
            chainAccountAddressRepository = chainAccountAddressRepository,
            accountsRepository = accountsRepository,
            blockChainSpecificRepository = blockChainSpecificRepository,
            getAvailableTokenBalance = getAvailableTokenBalance,
            gasFeeToEstimatedFee = gasFeeToEstimatedFee,
            depositTransactionRepository = depositTransactionRepository,
            navigator = navigator,
            thorChainApi = thorChainApi,
            defiTypeProvider = { defiType },
            isAutocompoundProvider = { false },
            showLoading = {},
            hideLoading = {},
            showError = { lastError = it },
        )

    private fun ybRuneCoin(): Coin = Coins.ThorChain.ybRUNE.copy(address = "thor1self")

    private fun rujiCoin(): Coin =
        Coin(
            chain = Chain.ThorChain,
            ticker = "RUJI",
            logo = "",
            address = "thor1self",
            decimal = 8,
            hexPublicKey = "",
            priceProviderID = "ruji",
            contractAddress = "ruji-contract",
            isNativeToken = false,
        )

    private fun UiText?.stringId(): Int? = (this as? UiText.StringResource)?.resId

    private companion object {
        const val VAULT_ID = "vault-1"
    }
}
