@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.send.submit

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import com.vultisig.wallet.R
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
import java.math.BigInteger
import kotlin.test.assertEquals
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

internal class StakeStrategyTest {

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

    private var lastError: UiText? = null

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        mockkObject(ThorchainFunctions)
        every {
            ThorchainFunctions.stakeRUJI(
                fromAddress = any(),
                stakingContract = any(),
                denom = any(),
                amount = any(),
            )
        } answers
            {
                WasmExecuteContractPayload(
                    senderAddress = arg(0),
                    contractAddress = arg(1),
                    executeMsg = "ruji-stake-msg",
                    coins = emptyList(),
                )
            }
        every {
            ThorchainFunctions.stakeTcyCompound(
                fromAddress = any(),
                stakingContract = any(),
                denom = any(),
                amount = any(),
            )
        } answers
            {
                WasmExecuteContractPayload(
                    senderAddress = arg(0),
                    contractAddress = arg(1),
                    executeMsg = "tcy-stake-msg",
                    coins = emptyList(),
                )
            }
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
        Dispatchers.resetMain()
    }

    @Test
    fun `submit surfaces no_address error when chain validates dst as invalid`() = runTest {
        givenValidatedAccount()
        coEvery { chainAccountAddressRepository.isValid(any(), any()) } returns false

        build(this, DeFiNavActions.STAKE_RUJI).submit()
        advanceUntilIdle()

        assertEquals(R.string.send_error_no_address, lastError.stringId())
    }

    @Test
    fun `submit surfaces no_amount when amount is blank`() = runTest {
        givenValidatedAccount()
        coEvery { chainAccountAddressRepository.isValid(any(), any()) } returns true

        build(this, DeFiNavActions.STAKE_RUJI).submit()
        advanceUntilIdle()

        assertEquals(R.string.send_error_no_amount, lastError.stringId())
    }

    @Test
    fun `submit STAKE_RUJI persists deposit with bond memo and ruji staking contract`() = runTest {
        withMockedIoDispatcher {
            givenSuccessfulStake()
            tokenAmountFieldState.setTextAndPlaceCursorAtEnd("0.5")

            val captured = slot<DepositTransaction>()
            coEvery { depositTransactionRepository.addTransaction(capture(captured)) } returns Unit

            build(this, DeFiNavActions.STAKE_RUJI).submit()
            advanceUntilIdle()

            val tx = captured.captured
            assertEquals("bond:ruji-contract:50000000", tx.memo)
            val payload = tx.wasmExecuteContractPayload
            assertNotNull(payload)
            assertEquals(STAKING_RUJI_CONTRACT, payload.contractAddress)
            coVerify { depositTransactionRepository.addTransaction(any()) }
        }
    }

    @Test
    fun `submit STAKE_TCY persists empty memo deposit when not autocompounding`() = runTest {
        withMockedIoDispatcher {
            givenSuccessfulStake(selectedToken = tcyCoin())
            tokenAmountFieldState.setTextAndPlaceCursorAtEnd("0.5")

            val captured = slot<DepositTransaction>()
            coEvery { depositTransactionRepository.addTransaction(capture(captured)) } returns Unit

            build(this, DeFiNavActions.STAKE_TCY).submit()
            advanceUntilIdle()

            // Non-autocompound TCY stake uses the legacy "TCY+" memo and no wasm payload.
            assertEquals("TCY+", captured.captured.memo)
        }
    }

    @Test
    fun `submit surfaces insufficient_balance when RUNE balance is below gasFee`() = runTest {
        givenValidatedAccount(gasFeeValue = BigInteger.valueOf(10_000_000))
        tokenAmountFieldState.setTextAndPlaceCursorAtEnd("0.5")
        coEvery { chainAccountAddressRepository.isValid(any(), any()) } returns true
        // RUNE account exists but its balance is below gasFee.
        every { accountsRepository.loadAddresses(VAULT_ID) } returns
            flowOf(listOf(addressWithRune(BigInteger.valueOf(1_000))))

        build(this, DeFiNavActions.STAKE_RUJI).submit()
        advanceUntilIdle()

        assertEquals(R.string.send_error_insufficient_balance, lastError.stringId())
        coVerify(exactly = 0) { depositTransactionRepository.addTransaction(any()) }
    }

    private inline fun withMockedIoDispatcher(block: () -> Unit) {
        mockkStatic(Dispatchers::class)
        every { Dispatchers.IO } returns mainDispatcher
        try {
            block()
        } finally {
            io.mockk.unmockkStatic(Dispatchers::class)
        }
    }

    private fun givenSuccessfulStake(selectedToken: Coin = rujiCoin()) {
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
                gasFee = TokenValue(BigInteger.valueOf(2_000_000), Coins.ThorChain.RUNE),
                dstAddress = "thor-staking-contract",
            )
        coEvery { chainAccountAddressRepository.isValid(any(), any()) } returns true
        every { accountsRepository.loadAddresses(VAULT_ID) } returns
            flowOf(listOf(addressWithRune(BigInteger.valueOf(1_000_000_000))))
        coEvery { getAvailableTokenBalance(any(), any()) } returns
            TokenValue(BigInteger.valueOf(1_000_000_000), selectedToken)
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

    private fun tcyCoin(): Coin =
        Coin(
            chain = Chain.ThorChain,
            ticker = "TCY",
            logo = "",
            address = "thor1self",
            decimal = 8,
            hexPublicKey = "",
            priceProviderID = "tcy",
            contractAddress = "tcy-contract",
            isNativeToken = false,
        )

    private fun givenValidatedAccount(gasFeeValue: BigInteger = BigInteger.valueOf(2_000_000)) {
        coEvery { accountValidator.validate() } returns
            ValidatedAccount(
                vaultId = VAULT_ID,
                selectedAccount =
                    Account(
                        token = rujiCoin(),
                        tokenValue = TokenValue(BigInteger.valueOf(2_000_000_000), rujiCoin()),
                        fiatValue = null,
                        price = null,
                    ),
                chain = Chain.ThorChain,
                gasFee = TokenValue(gasFeeValue, rujiCoin()),
                dstAddress = "thor-staking-contract",
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
        StakeStrategy(
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
            defiTypeProvider = { defiType },
            isAutocompoundProvider = { false },
            showLoading = {},
            hideLoading = {},
            showError = { lastError = it },
        )

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
