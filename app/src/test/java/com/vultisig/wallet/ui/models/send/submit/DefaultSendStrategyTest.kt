@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.send.submit

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.settings.AppCurrency
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
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.utils.UiText
import io.mockk.mockk
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import wallet.core.jni.proto.Bitcoin

internal class DefaultSendStrategyTest {

    private val scheduler = TestCoroutineScheduler()
    private val mainDispatcher = UnconfinedTestDispatcher(scheduler)

    private val addressFieldState = TextFieldState()
    private val tokenAmountFieldState = TextFieldState()
    private val fiatAmountFieldState = TextFieldState()
    private val memoFieldState = TextFieldState()

    private var expandedSection: SendSections? = null
    private var emittedFocusField: SendFocusField? = null
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

    private fun build(scope: CoroutineScope) =
        DefaultSendStrategy(
            scope = scope,
            addressFieldState = addressFieldState,
            tokenAmountFieldState = tokenAmountFieldState,
            fiatAmountFieldState = fiatAmountFieldState,
            memoFieldState = memoFieldState,
            accountValidator = mockk(relaxed = true),
            chainAccountAddressRepository = mockk<ChainAccountAddressRepository>(relaxed = true),
            blockChainSpecificRepository = mockk<BlockChainSpecificRepository>(relaxed = true),
            transactionRepository = mockk<TransactionRepository>(relaxed = true),
            bitcoinPlanService = mockk(relaxed = true),
            getAvailableTokenBalance = mockk<GetAvailableTokenBalanceUseCase>(relaxed = true),
            gasFeeToEstimatedFee = mockk<GasFeeToEstimatedFeeUseCase>(relaxed = true),
            chainValidationService = ChainValidationService(),
            addressManager = mockk<AddressManager>(relaxed = true),
            amountManager = mockk<AmountManager>(relaxed = true),
            gasSettings = MutableStateFlow<GasSettings?>(null),
            planBtc = MutableStateFlow<Bitcoin.TransactionPlan?>(null),
            planFee = MutableStateFlow<Long?>(null),
            accounts = MutableStateFlow<List<Account>>(emptyList()),
            appCurrency = MutableStateFlow(AppCurrency.USD),
            vaultIdProvider = { null },
            selectedAccountProvider = { null },
            defiTypeProvider = { null },
            currentTronFrozenBalanceProvider = { null },
            navigator = mockk<Navigator<Destination>>(relaxed = true),
            expandSection = { expandedSection = it },
            emitFocusField = { emittedFocusField = it },
            showLoading = {},
            hideLoading = {},
            showError = { lastError = it },
        )
}
