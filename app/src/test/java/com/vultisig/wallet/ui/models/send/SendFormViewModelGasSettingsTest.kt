@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.send

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.settings.AppCurrency
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.ui.models.mappers.AccountToTokenBalanceUiModelMapper
import com.vultisig.wallet.ui.navigation.Route
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Issue #6168: the gas sheet is opened for one chain, but the form can move to another while it is
 * open (a QR deep link switches networks, for one). A save that lands after that move carries
 * values priced for a chain this send no longer targets, so it has to be dropped.
 */
internal class SendFormViewModelGasSettingsTest {

    private val scheduler = TestCoroutineScheduler()
    private val mainDispatcher = UnconfinedTestDispatcher(scheduler)

    private val savedStateHandle: SavedStateHandle = mockk(relaxed = true)
    private val accountsRepository: AccountsRepository = mockk(relaxed = true)
    private val appCurrencyRepository: AppCurrencyRepository = mockk(relaxed = true)

    private var viewModel: SendFormViewModel? = null

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        // The form hops to Dispatchers.IO for its fee/specific lookups. Left on real threads,
        // those calls outlive the test body and resume on a Dispatchers.Main that resetMain() has
        // already swapped out, which leaks an uncaught exception into the next test class. Route
        // them onto the test scheduler so the test can drive them to completion instead.
        mockkStatic(Dispatchers::class)
        every { Dispatchers.IO } returns mainDispatcher
        mockkStatic("androidx.navigation.SavedStateHandleKt")
        every { savedStateHandle.toRoute<Route.Send>() } returns Route.Send(vaultId = VAULT_ID)
        every { appCurrencyRepository.currency } returns flowOf(AppCurrency.USD)
        every { appCurrencyRepository.defaultCurrency } returns AppCurrency.USD
    }

    @AfterEach
    fun tearDown() {
        // The ViewModel's collectors outlive the test body; cancel them and let the cancellation
        // settle before resetMain(), so they can't touch a Dispatchers.Main being swapped out
        // underneath them — that leaks an uncaught exception into whichever test class runs next.
        viewModel?.viewModelScope?.cancel()
        viewModel = null
        scheduler.advanceUntilIdle()
        unmockkStatic(Dispatchers::class)
        Dispatchers.resetMain()
        unmockkStatic("androidx.navigation.SavedStateHandleKt")
    }

    @Test
    fun `a save from a sheet opened for another chain is ignored`() = runTest(mainDispatcher) {
        val vm = buildViewModelWithPreselectedToken(Coins.Base.ETH)

        // Ethereum and Base share BlockChainSpecific.Ethereum, so nothing downstream would have
        // noticed this fee was priced for a different chain.
        vm.saveGasSettings(ethSettings(Chain.Ethereum))
        advanceUntilIdle()

        assertNull(vm.appliedGasSettings)
    }

    @Test
    fun `a save for the selected chain is applied`() = runTest(mainDispatcher) {
        val vm = buildViewModelWithPreselectedToken(Coins.Base.ETH)
        val settings = ethSettings(Chain.Base)

        vm.saveGasSettings(settings)
        advanceUntilIdle()

        assertEquals(settings, vm.appliedGasSettings)
    }

    @Test
    fun `a UTXO save from another chain's sheet never becomes the byte fee`() =
        runTest(mainDispatcher) {
            // Bitcoin and Dogecoin share BlockChainSpecific.UTXO, and their byte-fee scales differ
            // by orders of magnitude, so this is the save that must not reach the spec
            // saveGasSettings patches.
            val vm = buildViewModelWithPreselectedToken(Coins.Dogecoin.DOGE)

            vm.saveGasSettings(GasSettings.UTXO(chain = Chain.Bitcoin, byteFee = BigInteger("35")))
            advanceUntilIdle()

            assertNull(vm.appliedGasSettings)
        }

    private fun ethSettings(chain: Chain) =
        GasSettings.Eth(
            chain = chain,
            baseFee = BigInteger("1000000000"),
            priorityFee = BigInteger("1000000000"),
            gasLimit = BigInteger("21000"),
        )

    private fun account(coin: Coin) =
        Account(token = coin, tokenValue = null, fiatValue = null, price = null)

    private fun TestScope.buildViewModelWithPreselectedToken(coin: Coin): SendFormViewModel {
        val token = coin.copy(address = ADDRESS)
        coEvery { accountsRepository.loadAddresses(VAULT_ID) } returns
            flowOf(
                listOf(
                    Address(
                        chain = token.chain,
                        address = ADDRESS,
                        accounts = listOf(account(token)),
                    )
                )
            )
        every { savedStateHandle.toRoute<Route.Send>() } returns
            Route.Send(vaultId = VAULT_ID, tokenId = token.id)

        return buildViewModel().also { advanceUntilIdle() }
    }

    private fun buildViewModel(): SendFormViewModel {
        val tokenBalanceMapper = mockk<AccountToTokenBalanceUiModelMapper>()
        coEvery { tokenBalanceMapper.invoke(any()) } returns
            TokenBalanceUiModel(
                model = mockk(relaxed = true),
                title = "",
                balance = "0",
                fiatValue = "0",
                isNativeToken = true,
                isLayer2 = false,
                tokenStandard = null,
                tokenLogo = "",
                chainLogo = 0,
            )
        return SendFormViewModel(
            savedStateHandle = savedStateHandle,
            navigator = mockk(relaxed = true),
            accountToTokenBalanceUiModelMapper = tokenBalanceMapper,
            mapTokenValueToString = mockk(relaxed = true),
            requestQrScan = mockk(relaxed = true),
            accountsRepository = accountsRepository,
            appCurrencyRepository = appCurrencyRepository,
            chainAccountAddressRepository = mockk(relaxed = true),
            tokenPriceRepository = mockk(relaxed = true),
            blockChainSpecificRepository = mockk(relaxed = true),
            requestResultRepository = mockk(relaxed = true),
            addressParserRepository = mockk(relaxed = true),
            getAvailableTokenBalance = mockk(relaxed = true),
            gasFeeToEstimatedFee = mockk(relaxed = true),
            advanceGasUiRepository = mockk(relaxed = true),
            vaultRepository = mockk(relaxed = true),
            tokenRepository = mockk(relaxed = true),
            stakingDetailsRepository = mockk(relaxed = true),
            defaultStakingPositionService = mockk(relaxed = true),
            rujiStakingService = mockk(relaxed = true),
            feeServiceComposite = mockk(relaxed = true),
            chainValidationService = mockk(relaxed = true),
            requestAddressBookEntry = mockk(relaxed = true),
            getTronFrozenBalances = mockk(relaxed = true),
            sendStrategyFactory = fakeSendStrategyFactory(),
        )
            .also { viewModel = it }
    }

    private companion object {
        const val VAULT_ID = "vault-1"
        const val ADDRESS = "address-1"
    }
}
