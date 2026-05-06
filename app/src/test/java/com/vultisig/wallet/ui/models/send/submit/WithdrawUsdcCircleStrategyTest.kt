@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.send.submit

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import com.vultisig.wallet.R
import com.vultisig.wallet.data.chains.helpers.EthereumFunction
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

internal class WithdrawUsdcCircleStrategyTest {

    private val scheduler = TestCoroutineScheduler()
    private val mainDispatcher = UnconfinedTestDispatcher(scheduler)

    private val tokenAmountFieldState = TextFieldState()

    private val accountValidator: AccountValidator = mockk()
    private val chainAccountAddressRepository: ChainAccountAddressRepository = mockk()
    // Per-test stubbing on accountsRepository — relaxed mocks would silently swallow
    // every loadAddresses() call regardless of what an ETH-account fixture sets up.
    private val accountsRepository: AccountsRepository = mockk()
    private val blockChainSpecificRepository: BlockChainSpecificRepository = mockk()
    private val getAvailableTokenBalance: GetAvailableTokenBalanceUseCase = mockk()
    private val gasFeeToEstimatedFee: GasFeeToEstimatedFeeUseCase = mockk()
    private val depositTransactionRepository: DepositTransactionRepository = mockk(relaxed = true)
    private val navigator: Navigator<Destination> = mockk(relaxed = true)

    private var mscaAddress: String? = null
    private var lastError: UiText? = null

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        mockkObject(EthereumFunction)
        every { EthereumFunction.withdrawCircleMSCA(any(), any(), any()) } returns "0xencoded"
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
        // No-op accountsRepository stub: if this test ever called loadAddresses, mockk
        // would fail loudly instead of returning an empty flow as a relaxed mock would.
        every { accountsRepository.loadAddresses(any()) } returns flowOf(emptyList())

        build(this).submit()
        advanceUntilIdle()

        assertEquals(R.string.send_error_no_address, lastError.stringId())
    }

    @Test
    fun `submit surfaces no_amount when amount is blank`() = runTest {
        givenValidatedAccount()
        coEvery { chainAccountAddressRepository.isValid(any(), any()) } returns true
        every { accountsRepository.loadAddresses(any()) } returns flowOf(emptyList())

        build(this).submit()
        advanceUntilIdle()

        assertEquals(R.string.send_error_no_amount, lastError.stringId())
    }

    @Test
    fun `submit surfaces no_token when ETH account is missing from vault`() = runTest {
        givenValidatedAccount()
        coEvery { chainAccountAddressRepository.isValid(any(), any()) } returns true
        every { accountsRepository.loadAddresses("vault-1") } returns flowOf(emptyList())
        tokenAmountFieldState.setTextAndPlaceCursorAtEnd("0.5")

        build(this).submit()
        advanceUntilIdle()

        assertEquals(R.string.send_error_no_token, lastError.stringId())
    }

    @Test
    fun `submit persists withdraw deposit with circle operation and msca dstAddress`() = runTest {
        withMockedIoDispatcher {
            givenSuccessfulWithdraw()
            tokenAmountFieldState.setTextAndPlaceCursorAtEnd("0.5")
            mscaAddress = "0xMSCA"

            val captured = slot<DepositTransaction>()
            coEvery { depositTransactionRepository.addTransaction(capture(captured)) } returns Unit

            build(this).submit()
            advanceUntilIdle()

            val tx = captured.captured
            assertEquals("0xMSCA", tx.dstAddress)
            assertEquals("0xencoded", tx.memo)
            // Withdraw flows always send 0 of the native ETH; the actual transfer is encoded
            // in the memo and routed through the MSCA destination.
            assertEquals(BigInteger.ZERO, tx.srcTokenValue.value)
            assertNotNull(tx.operation)
        }
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

    private fun givenSuccessfulWithdraw() {
        givenValidatedAccount()
        coEvery { chainAccountAddressRepository.isValid(any(), any()) } returns true
        every { accountsRepository.loadAddresses("vault-1") } returns
            flowOf(
                listOf(
                    Address(
                        chain = Chain.Ethereum,
                        address = "0xself",
                        accounts =
                            listOf(
                                Account(
                                    token = Coins.Ethereum.ETH,
                                    tokenValue =
                                        TokenValue(
                                            value = BigInteger.valueOf(1_000_000_000),
                                            token = Coins.Ethereum.ETH,
                                        ),
                                    fiatValue = null,
                                    price = null,
                                )
                            ),
                    )
                )
            )
        coEvery { getAvailableTokenBalance(any(), any()) } returns
            TokenValue(BigInteger.valueOf(2_000_000), usdcCoin())
        coEvery {
            blockChainSpecificRepository.getSpecific(
                chain = any(),
                address = any(),
                token = any(),
                gasFee = any(),
                isSwap = any(),
                isMaxAmountEnabled = any(),
                isDeposit = any(),
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
        coEvery { gasFeeToEstimatedFee(any()) } returns
            EstimatedGasFee(
                formattedFiatValue = "$0.10",
                formattedTokenValue = "0.0001 ETH",
                tokenValue = TokenValue(BigInteger.ONE, Coins.Ethereum.ETH),
                fiatValue = mockk(relaxed = true),
            )
    }

    private fun givenValidatedAccount() {
        coEvery { accountValidator.validate() } returns
            ValidatedAccount(
                vaultId = "vault-1",
                selectedAccount =
                    Account(
                        token = usdcCoin(),
                        tokenValue = TokenValue(BigInteger.valueOf(2_000_000), usdcCoin()),
                        fiatValue = null,
                        price = null,
                    ),
                chain = Chain.Ethereum,
                gasFee = TokenValue(BigInteger.valueOf(21000), usdcCoin()),
                dstAddress = "0xdest",
            )
    }

    private fun build(scope: CoroutineScope) =
        WithdrawUsdcCircleStrategy(
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
            mscaAddressProvider = { mscaAddress },
            showLoading = {},
            hideLoading = {},
            showError = { lastError = it },
        )

    private fun usdcCoin(): Coin =
        Coin(
            chain = Chain.Ethereum,
            ticker = "USDC",
            logo = "",
            address = "0xself",
            decimal = 6,
            hexPublicKey = "",
            priceProviderID = "usd-coin",
            contractAddress = "0xUSDC",
            isNativeToken = false,
        )

    private fun UiText?.stringId(): Int? = (this as? UiText.StringResource)?.resId
}
