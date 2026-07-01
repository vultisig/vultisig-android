package com.vultisig.wallet.ui.models.defi

import com.vultisig.wallet.data.api.chains.ton.TonAccountStakingInfoJson
import com.vultisig.wallet.data.api.chains.ton.TonStakingApi
import com.vultisig.wallet.data.api.chains.ton.TonStakingPoolInfoJson
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.settings.AppCurrency
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.data.repositories.BalanceVisibilityRepository
import com.vultisig.wallet.data.repositories.TokenPriceRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class TonDeFiPositionsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var vaultRepository: VaultRepository
    private lateinit var tonStakingApi: TonStakingApi
    private lateinit var balanceVisibilityRepository: BalanceVisibilityRepository
    private lateinit var tokenPriceRepository: TokenPriceRepository
    private lateinit var appCurrencyRepository: AppCurrencyRepository
    private lateinit var navigator: Navigator<Destination>

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        vaultRepository = mockk(relaxed = true)
        tonStakingApi = mockk(relaxed = true)
        balanceVisibilityRepository = mockk(relaxed = true)
        tokenPriceRepository = mockk(relaxed = true)
        appCurrencyRepository = mockk(relaxed = true)
        navigator = mockk(relaxed = true)

        coEvery { vaultRepository.get(VAULT_ID) } returns VAULT
        coEvery { balanceVisibilityRepository.getVisibility(VAULT_ID) } returns true
        coEvery { appCurrencyRepository.currency } returns flowOf(AppCurrency.USD)
        coEvery { appCurrencyRepository.getCurrencyFormat() } returns
            NumberFormat.getCurrencyInstance(Locale.US)
        coEvery { tokenPriceRepository.getCachedPrice(any(), any()) } returns BigDecimal.ONE
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `surfaces the largest pool as the active position with formatted APY`() = runTest {
        coEvery { tonStakingApi.getNominatorPools(TON_ADDRESS) } returns
            listOf(
                TonAccountStakingInfoJson(pool = "pool-small", amount = 10_000_000_000L),
                TonAccountStakingInfoJson(
                    pool = POOL,
                    amount = 50_000_000_000L,
                    pendingDeposit = 800_000_000L,
                ),
            )
        coEvery { tonStakingApi.getStakingPool(POOL) } returns
            TonStakingPoolInfoJson(name = "Whales Nominators #1", apy = 13.27)

        val vm = createViewModel().also { it.setData(VAULT_ID) }

        val state = vm.state.value
        assertTrue(state is TonDeFiUiState.Success, "expected Success, was $state")
        val data = (state as TonDeFiUiState.Success).tonData
        assertTrue(data.hasPosition)
        assertEquals("Whales Nominators #1", data.poolName)
        assertEquals("13.27%", data.apy)
        assertFalse(data.isActionLocked)
    }

    @Test
    fun `renders empty position when the account holds no pools`() = runTest {
        coEvery { tonStakingApi.getNominatorPools(TON_ADDRESS) } returns emptyList()

        val vm = createViewModel().also { it.setData(VAULT_ID) }

        val state = vm.state.value
        assertTrue(state is TonDeFiUiState.Success)
        assertFalse((state as TonDeFiUiState.Success).tonData.hasPosition)
    }

    @Test
    fun `locks actions while a withdrawal is pending`() = runTest {
        coEvery { tonStakingApi.getNominatorPools(TON_ADDRESS) } returns
            listOf(
                TonAccountStakingInfoJson(
                    pool = POOL,
                    amount = 50_000_000_000L,
                    pendingWithdraw = 50_000_000_000L,
                )
            )
        coEvery { tonStakingApi.getStakingPool(POOL) } returns
            TonStakingPoolInfoJson(name = "Whales", apy = 5.0, cycleEnd = 9_999_999_999L)

        val vm = createViewModel().also { it.setData(VAULT_ID) }

        val data = (vm.state.value as TonDeFiUiState.Success).tonData
        assertTrue(data.isActionLocked)

        // A locked position must not navigate into the unstake flow.
        vm.onUnstake()
        coVerify(exactly = 0) { navigator.route(any<Route.Deposit>()) }
    }

    @Test
    fun `reports Error when the network fails and nothing is rendered yet`() = runTest {
        coEvery { tonStakingApi.getNominatorPools(TON_ADDRESS) } throws
            RuntimeException("network down")

        val vm = createViewModel().also { it.setData(VAULT_ID) }

        assertTrue(vm.state.value is TonDeFiUiState.Error)
    }

    @Test
    fun `a first-time stake opens the stake screen with no preselected pool`() = runTest {
        coEvery { tonStakingApi.getNominatorPools(TON_ADDRESS) } returns emptyList()

        val vm = createViewModel().also { it.setData(VAULT_ID) }
        vm.onStake()

        coVerify(exactly = 1) {
            navigator.route(Route.TonStake(vaultId = VAULT_ID, poolAddress = null))
        }
        coVerify(exactly = 0) { navigator.route(match { it is Route.Deposit }) }
    }

    @Test
    fun `adding to an existing position opens the stake screen with its pool prefilled`() =
        runTest {
            coEvery { tonStakingApi.getNominatorPools(TON_ADDRESS) } returns
                listOf(TonAccountStakingInfoJson(pool = POOL, amount = 50_000_000_000L))
            coEvery { tonStakingApi.getStakingPool(POOL) } returns
                TonStakingPoolInfoJson(name = "Whales", apy = 5.0)

            val vm = createViewModel().also { it.setData(VAULT_ID) }
            vm.onStake()

            coVerify(exactly = 1) {
                navigator.route(Route.TonStake(vaultId = VAULT_ID, poolAddress = POOL))
            }
            coVerify(exactly = 0) { navigator.route(match { it is Route.Deposit }) }
        }

    @Test
    fun `unstake opens the unstake confirmation carrying the existing pool and staked amount`() =
        runTest {
            coEvery { tonStakingApi.getNominatorPools(TON_ADDRESS) } returns
                listOf(TonAccountStakingInfoJson(pool = POOL, amount = 50_000_000_000L))
            coEvery { tonStakingApi.getStakingPool(POOL) } returns
                TonStakingPoolInfoJson(name = "Whales", apy = 5.0)

            val vm = createViewModel().also { it.setData(VAULT_ID) }
            vm.onUnstake()

            coVerify(exactly = 1) {
                navigator.route(
                    Route.TonUnstake(
                        vaultId = VAULT_ID,
                        poolAddress = POOL,
                        stakedDisplay = "50 GRAM",
                    )
                )
            }
        }

    private fun createViewModel(): TonDeFiPositionsViewModel =
        TonDeFiPositionsViewModel(
            vaultRepository = vaultRepository,
            tonStakingApi = tonStakingApi,
            balanceVisibilityRepository = balanceVisibilityRepository,
            tokenPriceRepository = tokenPriceRepository,
            appCurrencyRepository = appCurrencyRepository,
            navigator = navigator,
        )

    private companion object {
        const val VAULT_ID = "vault-1"
        const val TON_ADDRESS = "UQtonAddress"
        const val POOL = "0:a45b17f28409229b78360e3290420f13e4fe20f90d7e2bf8c4ac6703259e22fa"

        val TON_COIN = Coins.Ton.TON.copy(address = TON_ADDRESS)

        val VAULT =
            Vault(
                id = VAULT_ID,
                name = "Vultisig Wallet",
                pubKeyECDSA = "",
                pubKeyEDDSA = "",
                hexChainCode = "",
                localPartyID = "",
                signers = emptyList(),
                resharePrefix = "",
                libType = SigningLibType.DKLS,
                coins = listOf(TON_COIN),
            )
    }
}
