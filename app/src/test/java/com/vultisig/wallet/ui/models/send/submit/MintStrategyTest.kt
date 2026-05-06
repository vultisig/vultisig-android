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
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.screens.v2.defi.YRUNE_CONTRACT
import com.vultisig.wallet.ui.screens.v2.defi.YRUNE_YTCY_AFFILIATE_CONTRACT
import com.vultisig.wallet.ui.screens.v2.defi.YTCY_CONTRACT
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
import kotlin.test.assertTrue
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

internal class MintStrategyTest {

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
        mockkStatic(Dispatchers::class)
        every { Dispatchers.IO } returns mainDispatcher
        // Stub out ThorchainFunctions so the test doesn't load Trust Wallet Core
        // JNI (Base64.encode); the strategy still passes the real arguments through,
        // which is what we want to assert on.
        mockkObject(ThorchainFunctions)
        every {
            ThorchainFunctions.mintYToken(
                fromAddress = any(),
                stakingContract = any(),
                tokenContract = any(),
                denom = any(),
                amount = any(),
            )
        } answers
            {
                WasmExecuteContractPayload(
                    senderAddress = arg(0),
                    contractAddress = arg(1),
                    executeMsg = "encoded:${arg<String>(2)}",
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

        build(this, DeFiNavActions.MINT_YRUNE).submit()
        advanceUntilIdle()

        assertEquals(R.string.send_error_no_address, lastError.stringId())
    }

    @Test
    fun `submit MINT_YRUNE persists deposit with receive memo and YRUNE contract`() = runTest {
        givenSuccessfulMintFlow()
        tokenAmountFieldState.setTextAndPlaceCursorAtEnd("0.5")

        val captured = slot<DepositTransaction>()
        coEvery { depositTransactionRepository.addTransaction(capture(captured)) } returns Unit

        build(this, DeFiNavActions.MINT_YRUNE).submit()
        advanceUntilIdle()

        assertEquals(null, lastError, "No error expected; got $lastError")
        val tx = captured.captured
        assertEquals("receive:rune:0.5", tx.memo)
        assertEquals("thor-mint-contract", tx.dstAddress)
        assertEquals(BigInteger.valueOf(50_000_000), tx.srcTokenValue.value)
        val payload = tx.wasmExecuteContractPayload
        assertNotNull(payload)
        // Mint flows route execution through the affiliate contract; the YRUNE token
        // contract is encoded in the executeMsg payload.
        assertEquals(YRUNE_YTCY_AFFILIATE_CONTRACT, payload.contractAddress)
        assertTrue(payload.executeMsg.contains(YRUNE_CONTRACT))
        coVerify { navigator.route(any<Route.VerifyDeposit>()) }
    }

    @Test
    fun `submit MINT_YTCY persists deposit with YTCY contract in executeMsg`() = runTest {
        givenSuccessfulMintFlow(selectedToken = ytcyMintCoin())
        tokenAmountFieldState.setTextAndPlaceCursorAtEnd("0.5")

        val captured = slot<DepositTransaction>()
        coEvery { depositTransactionRepository.addTransaction(capture(captured)) } returns Unit

        build(this, DeFiNavActions.MINT_YTCY).submit()
        advanceUntilIdle()

        val payload = captured.captured.wasmExecuteContractPayload
        assertNotNull(payload)
        assertEquals(YRUNE_YTCY_AFFILIATE_CONTRACT, payload.contractAddress)
        assertTrue(payload.executeMsg.contains(YTCY_CONTRACT))
    }

    @Test
    fun `submit surfaces insufficient_balance when RUNE is below gasFee`() = runTest {
        givenValidatedAccount(gasFeeValue = BigInteger.valueOf(10_000_000))
        tokenAmountFieldState.setTextAndPlaceCursorAtEnd("0.5")
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

        build(this, DeFiNavActions.MINT_YRUNE).submit()
        advanceUntilIdle()

        assertEquals(R.string.send_error_insufficient_balance, lastError.stringId())
    }

    private fun givenSuccessfulMintFlow(selectedToken: Coin = runeCoin()) {
        coEvery { accountValidator.validate() } returns
            ValidatedAccount(
                vaultId = "vault-1",
                selectedAccount =
                    Account(
                        token = selectedToken,
                        tokenValue = TokenValue(BigInteger.valueOf(2_000_000_000), selectedToken),
                        fiatValue = null,
                        price = null,
                    ),
                chain = Chain.ThorChain,
                gasFee = TokenValue(BigInteger.valueOf(2_000_000), Coins.ThorChain.RUNE),
                dstAddress = "thor-mint-contract",
            )
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
                                            value = BigInteger.valueOf(1_000_000_000),
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

    private fun givenValidatedAccount(gasFeeValue: BigInteger = BigInteger.valueOf(2_000_000)) {
        coEvery { accountValidator.validate() } returns
            ValidatedAccount(
                vaultId = "vault-1",
                selectedAccount =
                    Account(
                        token = runeCoin(),
                        tokenValue = TokenValue(BigInteger.valueOf(2_000_000_000), runeCoin()),
                        fiatValue = null,
                        price = null,
                    ),
                chain = Chain.ThorChain,
                gasFee = TokenValue(gasFeeValue, runeCoin()),
                dstAddress = "thor-mint-contract",
            )
    }

    private fun build(scope: CoroutineScope, defiType: DeFiNavActions) =
        MintStrategy(
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
            showLoading = {},
            hideLoading = {},
            showError = { lastError = it },
        )

    private fun runeCoin(): Coin =
        Coin(
            chain = Chain.ThorChain,
            ticker = "RUNE",
            logo = "",
            address = "thor1self",
            decimal = 8,
            hexPublicKey = "",
            priceProviderID = "thorchain",
            contractAddress = "",
            isNativeToken = true,
        )

    private fun ytcyMintCoin(): Coin =
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

    private fun UiText?.stringId(): Int? = (this as? UiText.StringResource)?.resId
}
