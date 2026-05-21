@file:OptIn(ExperimentalCoroutinesApi::class, ExperimentalStdlibApi::class)

package com.vultisig.wallet.ui.models.send

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.settings.AppCurrency
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.ui.models.mappers.AccountToTokenBalanceUiModelMapper
import com.vultisig.wallet.ui.navigation.Route
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.yield
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class SendFormViewModelAutoCompoundTest {

    private val scheduler = TestCoroutineScheduler()
    private val mainDispatcher = UnconfinedTestDispatcher(scheduler)

    private val savedStateHandle: SavedStateHandle = mockk(relaxed = true)
    private val vaultRepository: VaultRepository = mockk(relaxed = true)
    private val accountsRepository: AccountsRepository = mockk(relaxed = true)
    private val appCurrencyRepository: AppCurrencyRepository = mockk(relaxed = true)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        mockkStatic("androidx.navigation.SavedStateHandleKt")
        every { savedStateHandle.toRoute<Route.Send>() } returns
            Route.Send(vaultId = VAULT_ID, type = "UNSTAKE_TCY")
        every { appCurrencyRepository.currency } returns flowOf(AppCurrency.USD)
        every { appCurrencyRepository.defaultCurrency } returns AppCurrency.USD
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic("androidx.navigation.SavedStateHandleKt")
    }

    @Test
    fun `onAutoCompound(true) drives cached + hydrated emissions and releases isSwitchingAccounts`() =
        runTest(mainDispatcher) {
            // The initial UNSTAKE_TCY load goes through loadDeFiAddresses; we don't care
            // about its contents, only that it parks in awaitClose() so the autocompound
            // switch below has to cancel a live collector (the exact production shape).
            coEvery { accountsRepository.loadDeFiAddresses(VAULT_ID, false) } returns
                channelFlow<List<Address>> {
                    send(emptyList())
                    awaitClose()
                }

            // The autocompound=true switch flips to loadAddresses. Use a real channelFlow
            // with cached -> hydrated -> awaitClose() so the test exercises the
            // never-closing semantic — flowOf(...) would let the collect return and hide
            // the stuck-isSwitchingAccounts regression behind a happy path.
            val cachedSTCY = thorAccount(Coins.ThorChain.sTCY, BigInteger("100"))
            val hydratedSTCY = thorAccount(Coins.ThorChain.sTCY, BigInteger("999"))
            every { accountsRepository.loadAddresses(VAULT_ID) } returns
                channelFlow {
                    send(
                        listOf(
                            Address(
                                chain = Chain.ThorChain,
                                address = "thor1",
                                accounts = listOf(cachedSTCY),
                            )
                        )
                    )
                    // yield() lets the cached snapshot propagate through accountsState
                    // before the hydrated emission conflates over it.
                    yield()
                    send(
                        listOf(
                            Address(
                                chain = Chain.ThorChain,
                                address = "thor1",
                                accounts = listOf(hydratedSTCY),
                            )
                        )
                    )
                    awaitClose()
                }

            val balancesPassedToMapper = mutableListOf<BigInteger?>()
            val mapper = mockk<AccountToTokenBalanceUiModelMapper>()
            coEvery { mapper.invoke(any()) } answers
                {
                    val src = firstArg<SendSrc>()
                    balancesPassedToMapper.add(src.account.tokenValue?.value)
                    TokenBalanceUiModel(
                        model = mockk(relaxed = true),
                        title = src.account.token.ticker,
                        balance = src.account.tokenValue?.value?.toString() ?: "null",
                        fiatValue = null,
                        isNativeToken = src.account.token.isNativeToken,
                        isLayer2 = false,
                        tokenStandard = null,
                        tokenLogo = "",
                        chainLogo = 0,
                    )
                }

            val vm = buildViewModel(mapper)
            advanceUntilIdle()

            vm.onAutoCompound(true)
            advanceUntilIdle()

            // (1) isAutocompound flipped — uiState reflects the toggle.
            assertTrue(vm.uiState.value.isAutocompound)

            // (2) selectedCoin is non-null with sTCY — proves selectToken was called with
            // the autocompound target AND isSwitchingAccounts returned to false (the
            // combine in collectSelectedAccount returns null while switching is true, so
            // a non-null selectedCoin can only mean the gate was released).
            val selected = vm.uiState.value.selectedCoin
            assertNotNull(selected)
            assertEquals("sTCY", selected.title)

            // (3) Both cached and hydrated balances flowed through accountsState — final
            // balance reflects the hydrated emission, and the cached value also reached
            // the mapper (proving the cached snapshot wasn't dropped). Without this the
            // user would only ever see the hydrated number once the network came back.
            assertEquals("999", selected.balance)
            assertTrue(
                balancesPassedToMapper.contains(BigInteger("100")),
                "cached balance never reached the mapper (autocompound dropped the cached snapshot)",
            )
            assertTrue(
                balancesPassedToMapper.contains(BigInteger("999")),
                "hydrated balance never reached the mapper",
            )
        }

    private fun thorAccount(coin: Coin, value: BigInteger): Account =
        Account(
            token = coin.copy(address = "thor1"),
            tokenValue = TokenValue(value = value, token = coin),
            fiatValue = null,
            price = null,
        )

    private fun buildViewModel(mapper: AccountToTokenBalanceUiModelMapper): SendFormViewModel =
        SendFormViewModel(
            savedStateHandle = savedStateHandle,
            navigator = mockk(relaxed = true),
            accountToTokenBalanceUiModelMapper = mapper,
            mapTokenValueToString = mockk(relaxed = true),
            requestQrScan = mockk(relaxed = true),
            accountsRepository = accountsRepository,
            appCurrencyRepository = appCurrencyRepository,
            chainAccountAddressRepository = mockk(relaxed = true),
            tokenPriceRepository = mockk(relaxed = true),
            transactionRepository = mockk(relaxed = true),
            blockChainSpecificRepository = mockk(relaxed = true),
            requestResultRepository = mockk(relaxed = true),
            addressParserRepository = mockk(relaxed = true),
            getAvailableTokenBalance = mockk(relaxed = true),
            gasFeeToEstimatedFee = mockk(relaxed = true),
            advanceGasUiRepository = mockk(relaxed = true),
            vaultRepository = vaultRepository,
            tokenRepository = mockk(relaxed = true),
            depositTransactionRepository = mockk(relaxed = true),
            stakingDetailsRepository = mockk(relaxed = true),
            feeServiceComposite = mockk(relaxed = true),
            chainValidationService = mockk(relaxed = true),
            requestAddressBookEntry = mockk(relaxed = true),
            getTronFrozenBalances = mockk(relaxed = true),
        )

    private companion object {
        const val VAULT_ID = "vault-1"
    }
}
