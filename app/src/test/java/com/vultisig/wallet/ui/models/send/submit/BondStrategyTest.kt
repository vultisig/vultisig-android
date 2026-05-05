@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.send.submit

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.repositories.AddressParserRepository
import com.vultisig.wallet.data.repositories.BlockChainSpecificRepository
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.data.repositories.DepositTransactionRepository
import com.vultisig.wallet.data.usecases.GasFeeToEstimatedFeeUseCase
import com.vultisig.wallet.data.usecases.GetAvailableTokenBalanceUseCase
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.utils.UiText
import io.mockk.coEvery
import io.mockk.coVerify
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

internal class BondStrategyTest {

    private val scheduler = TestCoroutineScheduler()
    private val mainDispatcher = UnconfinedTestDispatcher(scheduler)

    private val tokenAmountFieldState = TextFieldState()
    private val providerBondFieldState = TextFieldState()
    private val operatorFeesBondFieldState = TextFieldState()

    private val accountValidator: AccountValidator = mockk()
    private val chainAccountAddressRepository: ChainAccountAddressRepository = mockk()
    private val addressParserRepository: AddressParserRepository = mockk(relaxed = true)
    private val blockChainSpecificRepository: BlockChainSpecificRepository = mockk()
    private val getAvailableTokenBalance: GetAvailableTokenBalanceUseCase = mockk()
    private val gasFeeToEstimatedFee: GasFeeToEstimatedFeeUseCase = mockk()
    private val depositTransactionRepository: DepositTransactionRepository = mockk(relaxed = true)
    private val navigator: Navigator<Destination> = mockk(relaxed = true)

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
    fun `submit surfaces invalid_operator_fee error when fee is non-numeric`() = runTest {
        givenValidatedAccount()
        operatorFeesBondFieldState.setTextAndPlaceCursorAtEnd("abc")
        coEvery { chainAccountAddressRepository.isValid(any(), any()) } returns true

        build(this).submit()
        advanceUntilIdle()

        assertEquals(R.string.send_error_invalid_operator_fee, lastError.stringId())
        coVerify(exactly = 0) { depositTransactionRepository.addTransaction(any()) }
    }

    @Test
    fun `submit surfaces invalid_operator_fee error when fee is above 10000`() = runTest {
        givenValidatedAccount()
        operatorFeesBondFieldState.setTextAndPlaceCursorAtEnd("10001")
        coEvery { chainAccountAddressRepository.isValid(any(), any()) } returns true

        build(this).submit()
        advanceUntilIdle()

        assertEquals(R.string.send_error_invalid_operator_fee, lastError.stringId())
    }

    @Test
    fun `submit accepts boundary operator fee 10000 and proceeds past validation`() = runTest {
        givenValidatedAccount()
        operatorFeesBondFieldState.setTextAndPlaceCursorAtEnd("10000")
        tokenAmountFieldState.setTextAndPlaceCursorAtEnd("0")
        coEvery { chainAccountAddressRepository.isValid(any(), any()) } returns true

        build(this).submit()
        advanceUntilIdle()

        // Boundary 10000 is valid; tokenAmount=0 fails the next check, surfacing no_amount.
        assertEquals(R.string.send_error_no_amount, lastError.stringId())
    }

    @Test
    fun `submit surfaces no_address error when chain validates dst as invalid`() = runTest {
        givenValidatedAccount()
        coEvery { chainAccountAddressRepository.isValid(any(), any()) } returns false

        build(this).submit()
        advanceUntilIdle()

        assertEquals(R.string.send_error_no_address, lastError.stringId())
    }

    private fun givenValidatedAccount() {
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
                gasFee = TokenValue(BigInteger.valueOf(2_000_000), runeCoin()),
                dstAddress = "thor-node-address",
            )
    }

    private fun build(scope: CoroutineScope) =
        BondStrategy(
            scope = scope,
            tokenAmountFieldState = tokenAmountFieldState,
            providerBondFieldState = providerBondFieldState,
            operatorFeesBondFieldState = operatorFeesBondFieldState,
            accountValidator = accountValidator,
            chainAccountAddressRepository = chainAccountAddressRepository,
            addressParserRepository = addressParserRepository,
            blockChainSpecificRepository = blockChainSpecificRepository,
            getAvailableTokenBalance = getAvailableTokenBalance,
            gasFeeToEstimatedFee = gasFeeToEstimatedFee,
            depositTransactionRepository = depositTransactionRepository,
            navigator = navigator,
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

    private fun UiText?.stringId(): Int? = (this as? UiText.StringResource)?.resId
}
