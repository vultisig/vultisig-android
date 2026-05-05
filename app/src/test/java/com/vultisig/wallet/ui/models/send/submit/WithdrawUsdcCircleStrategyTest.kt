@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.send.submit

import androidx.compose.foundation.text.input.TextFieldState
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.data.repositories.BlockChainSpecificRepository
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.data.repositories.DepositTransactionRepository
import com.vultisig.wallet.data.usecases.GasFeeToEstimatedFeeUseCase
import com.vultisig.wallet.data.usecases.GetAvailableTokenBalanceUseCase
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.utils.UiText
import io.mockk.coEvery
import io.mockk.mockk
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

    private var mscaAddress: String? = null
    private var lastError: UiText? = null

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `submit surfaces no_address when chain validates dst as invalid`() = runTest {
        givenValidatedAccount()
        coEvery { chainAccountAddressRepository.isValid(any(), any()) } returns false

        build(this).submit()
        advanceUntilIdle()

        assertEquals(R.string.send_error_no_address, lastError.stringId())
    }

    @Test
    fun `submit surfaces no_amount when amount is blank`() = runTest {
        givenValidatedAccount()
        coEvery { chainAccountAddressRepository.isValid(any(), any()) } returns true

        build(this).submit()
        advanceUntilIdle()

        assertEquals(R.string.send_error_no_amount, lastError.stringId())
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
            accountsRepository = mockk<AccountsRepository>(relaxed = true),
            blockChainSpecificRepository = mockk<BlockChainSpecificRepository>(relaxed = true),
            getAvailableTokenBalance = mockk<GetAvailableTokenBalanceUseCase>(relaxed = true),
            gasFeeToEstimatedFee = mockk<GasFeeToEstimatedFeeUseCase>(relaxed = true),
            depositTransactionRepository = mockk<DepositTransactionRepository>(relaxed = true),
            navigator = mockk<Navigator<Destination>>(relaxed = true),
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
