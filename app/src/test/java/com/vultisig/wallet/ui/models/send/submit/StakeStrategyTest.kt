@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.send.submit

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.data.repositories.BlockChainSpecificRepository
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.data.repositories.DepositTransactionRepository
import com.vultisig.wallet.data.usecases.GasFeeToEstimatedFeeUseCase
import com.vultisig.wallet.data.usecases.GetAvailableTokenBalanceUseCase
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.screens.v2.defi.model.DeFiNavActions
import com.vultisig.wallet.ui.utils.UiText
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.math.BigInteger
import kotlin.test.assertEquals
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
    }

    @AfterEach
    fun tearDown() {
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
