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
import com.vultisig.wallet.ui.models.send.ChainValidationService
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.screens.v2.defi.YRUNE_CONTRACT
import com.vultisig.wallet.ui.screens.v2.defi.model.DeFiNavActions
import com.vultisig.wallet.ui.utils.UiText
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.unmockkStatic
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

internal class RedeemStrategyTest {

    private val scheduler = TestCoroutineScheduler()
    private val mainDispatcher = UnconfinedTestDispatcher(scheduler)

    private val tokenAmountFieldState = TextFieldState()
    private val slippageFieldState = TextFieldState()

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
            ThorchainFunctions.redeemYToken(
                fromAddress = any(),
                tokenContract = any(),
                slippage = any(),
                denom = any(),
                amount = any(),
            )
        } answers
            {
                WasmExecuteContractPayload(
                    senderAddress = arg(0),
                    contractAddress = arg(1),
                    executeMsg = "redeem-msg",
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
    fun `submit surfaces no_address when chain validates dst as invalid`() = runTest {
        givenValidatedAccount()
        coEvery { chainAccountAddressRepository.isValid(any(), any()) } returns false

        build(this, DeFiNavActions.REDEEM_YRUNE).submit()
        advanceUntilIdle()

        assertEquals(R.string.send_error_no_address, lastError.stringId())
    }

    @Test
    fun `submit REDEEM_YRUNE persists deposit with redeem memo and YRUNE contract`() = runTest {
        withMockedIoDispatcher {
            givenSuccessfulRedeem()
            tokenAmountFieldState.setTextAndPlaceCursorAtEnd("0.5")
            slippageFieldState.setTextAndPlaceCursorAtEnd("1.0")

            val captured = slot<DepositTransaction>()
            coEvery { depositTransactionRepository.addTransaction(capture(captured)) } returns Unit

            build(this, DeFiNavActions.REDEEM_YRUNE).submit()
            advanceUntilIdle()

            val tx = captured.captured
            assertEquals("sell:yrune-contract:0.5", tx.memo)
            // Redeem flows route the call through the YRUNE token contract directly.
            assertEquals(YRUNE_CONTRACT, tx.dstAddress)
            assertNotNull(tx.wasmExecuteContractPayload)
        }
    }

    @Test
    fun `submit surfaces insufficient_balance when RUNE is below gasFee`() = runTest {
        givenValidatedAccount(gasFeeValue = BigInteger.valueOf(10_000_000))
        tokenAmountFieldState.setTextAndPlaceCursorAtEnd("0.5")
        slippageFieldState.setTextAndPlaceCursorAtEnd("1.0")
        coEvery { chainAccountAddressRepository.isValid(any(), any()) } returns true
        every { accountsRepository.loadAddresses("vault-1") } returns
            flowOf(
                listOf(
                    Address(
                        chain = Chain.ThorChain,
                        address = "thor1self",
                        accounts =
                            listOf(
                                Account(
                                    token = Coins.ThorChain.RUNE,
                                    tokenValue =
                                        TokenValue(
                                            value = BigInteger.valueOf(1_000),
                                            token = Coins.ThorChain.RUNE,
                                        ),
                                    fiatValue = null,
                                    price = null,
                                )
                            ),
                    )
                )
            )

        build(this, DeFiNavActions.REDEEM_YRUNE).submit()
        advanceUntilIdle()

        assertEquals(R.string.send_error_insufficient_balance, lastError.stringId())
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

    private fun givenSuccessfulRedeem() {
        givenValidatedAccount()
        coEvery { chainAccountAddressRepository.isValid(any(), any()) } returns true
        every { accountsRepository.loadAddresses("vault-1") } returns
            flowOf(
                listOf(
                    Address(
                        chain = Chain.ThorChain,
                        address = "thor1self",
                        accounts =
                            listOf(
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
                            ),
                    )
                )
            )
        coEvery { getAvailableTokenBalance(any(), any()) } returns
            TokenValue(BigInteger.valueOf(1_000_000_000), yRuneCoin())
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

    private fun givenValidatedAccount(gasFeeValue: BigInteger = BigInteger.valueOf(2_000_000)) {
        coEvery { accountValidator.validate() } returns
            ValidatedAccount(
                vaultId = "vault-1",
                selectedAccount =
                    Account(
                        token = yRuneCoin(),
                        tokenValue = TokenValue(BigInteger.valueOf(2_000_000_000), yRuneCoin()),
                        fiatValue = null,
                        price = null,
                    ),
                chain = Chain.ThorChain,
                gasFee = TokenValue(gasFeeValue, Coins.ThorChain.RUNE),
                dstAddress = "thor-redeem-contract",
            )
    }

    private fun build(scope: CoroutineScope, defiType: DeFiNavActions) =
        RedeemStrategy(
            scope = scope,
            tokenAmountFieldState = tokenAmountFieldState,
            slippageFieldState = slippageFieldState,
            accountValidator = accountValidator,
            chainAccountAddressRepository = chainAccountAddressRepository,
            accountsRepository = accountsRepository,
            blockChainSpecificRepository = blockChainSpecificRepository,
            getAvailableTokenBalance = getAvailableTokenBalance,
            gasFeeToEstimatedFee = gasFeeToEstimatedFee,
            chainValidationService = ChainValidationService(rippleApi = mockk(relaxed = true)),
            depositTransactionRepository = depositTransactionRepository,
            navigator = navigator,
            defiTypeProvider = { defiType },
            showLoading = {},
            hideLoading = {},
            showError = { lastError = it },
        )

    private fun yRuneCoin(): Coin =
        Coin(
            chain = Chain.ThorChain,
            ticker = "yRUNE",
            logo = "",
            address = "thor1self",
            decimal = 8,
            hexPublicKey = "",
            priceProviderID = "yrune",
            contractAddress = "yrune-contract",
            isNativeToken = false,
        )

    private fun UiText?.stringId(): Int? = (this as? UiText.StringResource)?.resId
}
