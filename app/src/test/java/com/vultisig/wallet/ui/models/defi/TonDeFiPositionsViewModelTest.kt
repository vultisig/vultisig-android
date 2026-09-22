package com.vultisig.wallet.ui.models.defi

import com.vultisig.wallet.data.api.chains.ton.TonAccountStakingInfoJson
import com.vultisig.wallet.data.api.chains.ton.TonStakingApi
import com.vultisig.wallet.data.api.chains.ton.TonStakingPoolInfoJson
import com.vultisig.wallet.data.blockchain.ton.TonLiquidPoolState
import com.vultisig.wallet.data.blockchain.ton.TonLiquidPosition
import com.vultisig.wallet.data.blockchain.ton.TonLiquidStakingService
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
import java.math.BigInteger
import java.text.NumberFormat
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
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
    private lateinit var liquidStakingService: TonLiquidStakingService
    private lateinit var balanceVisibilityRepository: BalanceVisibilityRepository
    private lateinit var tokenPriceRepository: TokenPriceRepository
    private lateinit var appCurrencyRepository: AppCurrencyRepository
    private lateinit var navigator: Navigator<Destination>
    // The real cache, not a mock: these tests assert the round trip a nav pop and a re-entry make.
    private lateinit var snapshotCache: DeFiPositionsSnapshotCache

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        vaultRepository = mockk(relaxed = true)
        tonStakingApi = mockk(relaxed = true)
        liquidStakingService = mockk()
        balanceVisibilityRepository = mockk(relaxed = true)
        tokenPriceRepository = mockk(relaxed = true)
        appCurrencyRepository = mockk(relaxed = true)
        navigator = mockk(relaxed = true)
        snapshotCache = DeFiPositionsSnapshotCache()

        coEvery { vaultRepository.get(VAULT_ID) } returns VAULT
        coEvery { balanceVisibilityRepository.getVisibility(VAULT_ID) } returns true
        coEvery { appCurrencyRepository.currency } returns flowOf(AppCurrency.USD)
        coEvery { appCurrencyRepository.getCurrencyFormat() } returns
            NumberFormat.getCurrencyInstance(Locale.US)
        coEvery { tokenPriceRepository.getCachedPrice(any(), any()) } returns BigDecimal.ONE
        // Default: no Tonstakers position, pool readable. Individual tests override.
        coEvery { liquidStakingService.getPosition(TON_ADDRESS) } returns
            TonLiquidPosition(tsTonBalance = BigInteger.ZERO, jettonWalletAddress = null)
        coEvery { liquidStakingService.getPoolState() } returns POOL_STATE
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

        // A locked position must not navigate into the unstake flow. Assert against the real
        // target (Route.TonUnstake) — the old Route.Deposit check passed even with the guard gone.
        vm.onUnstake()
        coVerify(exactly = 0) { navigator.route(any<Route.TonUnstake>()) }
    }

    @Test
    fun `a locked position blocks the stake action too`() = runTest {
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

        vm.onStake()
        coVerify(exactly = 0) { navigator.route(any<Route.TonStake>()) }
    }

    @Test
    fun `drops a non-nominator position so no failing stake or unstake is offered`() = runTest {
        coEvery { tonStakingApi.getNominatorPools(TON_ADDRESS) } returns
            listOf(TonAccountStakingInfoJson(pool = POOL, amount = 50_000_000_000L))
        // A liquid-staking implementation this app can't deposit into.
        coEvery { tonStakingApi.getStakingPool(POOL) } returns
            TonStakingPoolInfoJson(name = "Tonstakers", apy = 5.0, implementation = "liquidTF")

        val vm = createViewModel().also { it.setData(VAULT_ID) }

        val data = (vm.state.value as TonDeFiUiState.Success).tonData
        assertFalse(data.hasPosition)

        // No cached pool → unstake can't route.
        vm.onUnstake()
        coVerify(exactly = 0) { navigator.route(any<Route.TonUnstake>()) }
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

    @Test
    fun `a re-entry paints the card the screen was last showing`() = runTest {
        // Popping back to the DeFi list destroys this view-model; without a snapshot the next open
        // sits on the first-open skeleton for as long as tonapi takes.
        snapshotCache.write(VAULT_ID, LAST_RENDERED)
        // Suspend the network so the only state on screen is the restored one.
        coEvery { tonStakingApi.getNominatorPools(TON_ADDRESS) } coAnswers
            {
                CompletableDeferred<List<TonAccountStakingInfoJson>>().await()
            }

        val state = createViewModel().also { it.setData(VAULT_ID) }.state.value

        assertTrue(state is TonDeFiUiState.Success, "expected Success, was $state")
        assertEquals("50 GRAM", state.tonData.stakedDisplay)
        assertEquals("Whales", state.tonData.poolName)
        assertTrue(state.tonData.hasPosition)
    }

    @Test
    fun `a re-entry keeps the pool it was routing to when the refresh fails`() = runTest {
        // The pool address lives outside the state, so it has to travel with the snapshot —
        // otherwise a restored card offers an Unstake that silently does nothing.
        snapshotCache.write(VAULT_ID, LAST_RENDERED)
        coEvery { tonStakingApi.getNominatorPools(TON_ADDRESS) } throws
            RuntimeException("network down")

        val vm = createViewModel().also { it.setData(VAULT_ID) }
        vm.onUnstake()

        coVerify(exactly = 1) {
            navigator.route(
                Route.TonUnstake(vaultId = VAULT_ID, poolAddress = POOL, stakedDisplay = "50 GRAM")
            )
        }
    }

    @Test
    fun `hands the rendered card to the cache when the screen is popped`() = runTest {
        coEvery { tonStakingApi.getNominatorPools(TON_ADDRESS) } returns
            listOf(TonAccountStakingInfoJson(pool = POOL, amount = 50_000_000_000L))
        coEvery { tonStakingApi.getStakingPool(POOL) } returns
            TonStakingPoolInfoJson(name = "Whales", apy = 5.0)
        val vm = createViewModel().also { it.setData(VAULT_ID) }
        val rendered = vm.state.value as TonDeFiUiState.Success

        vm.clearForTest()

        assertEquals(
            TonStakingSnapshot(state = rendered, poolAddress = POOL, stakedDisplay = "50 GRAM"),
            snapshotCache.read(VAULT_ID, TonStakingSnapshot::class),
        )
    }

    @Test
    fun `renders the Tonstakers position priced at the pool rate next to the nominator card`() =
        runTest {
            coEvery { tonStakingApi.getNominatorPools(TON_ADDRESS) } returns emptyList()
            coEvery { liquidStakingService.getPosition(TON_ADDRESS) } returns
                TonLiquidPosition(
                    tsTonBalance = BigInteger.valueOf(4_300_000_000L),
                    jettonWalletAddress = "EQjettonWallet",
                )

            val vm = createViewModel().also { it.setData(VAULT_ID) }

            val state = vm.state.value as TonDeFiUiState.Success
            val liquid = state.liquidData
            assertTrue(liquid.hasPosition)
            assertEquals("4.3 tsTON", liquid.tsTonDisplay)
            // 4.3 tsTON × 1.16 = 4.988 GRAM, priced at the $1 TON price the test pins.
            assertEquals("4.988 GRAM", liquid.tonValueDisplay)
            assertEquals("$4.99", liquid.fiatDisplay)
            assertEquals("13.36%", liquid.apy)
            // The banner sums both cards: no nominator stake, so it is the liquid value alone.
            assertEquals("$4.99", state.totalAmountPrice)
            assertFalse(state.tonData.hasPosition)
        }

    @Test
    fun `banner total adds the nominator stake and the liquid position`() = runTest {
        coEvery { tonStakingApi.getNominatorPools(TON_ADDRESS) } returns
            listOf(TonAccountStakingInfoJson(pool = POOL, amount = 50_000_000_000L))
        coEvery { tonStakingApi.getStakingPool(POOL) } returns
            TonStakingPoolInfoJson(name = "Whales", apy = 13.27)
        coEvery { liquidStakingService.getPosition(TON_ADDRESS) } returns
            TonLiquidPosition(
                tsTonBalance = BigInteger.valueOf(100_000_000_000L),
                jettonWalletAddress = "EQjettonWallet",
            )

        val vm = createViewModel().also { it.setData(VAULT_ID) }

        val state = vm.state.value as TonDeFiUiState.Success
        // 50 GRAM staked + 100 tsTON × 1.16 = 166 GRAM, at $1.
        assertEquals("$166.00", state.totalAmountPrice)
        assertEquals("$50.00", state.tonData.totalAmountPrice)
    }

    @Test
    fun `liquid stake opens the Tonstakers stake screen and unstake needs a position`() = runTest {
        coEvery { tonStakingApi.getNominatorPools(TON_ADDRESS) } returns emptyList()
        val vm = createViewModel().also { it.setData(VAULT_ID) }

        vm.onLiquidUnstake()
        coVerify(exactly = 0) { navigator.route(match { it is Route.TonLiquidUnstake }) }

        vm.onLiquidStake()
        coVerify { navigator.route(Route.TonLiquidStake(vaultId = VAULT_ID)) }
    }

    @Test
    fun `liquid unstake opens the Tonstakers unstake screen when tsTON is held`() = runTest {
        coEvery { tonStakingApi.getNominatorPools(TON_ADDRESS) } returns emptyList()
        coEvery { liquidStakingService.getPosition(TON_ADDRESS) } returns
            TonLiquidPosition(
                tsTonBalance = BigInteger.valueOf(4_300_000_000L),
                jettonWalletAddress = "EQjettonWallet",
            )
        val vm = createViewModel().also { it.setData(VAULT_ID) }

        vm.onLiquidUnstake()

        coVerify { navigator.route(Route.TonLiquidUnstake(vaultId = VAULT_ID)) }
    }

    @Test
    fun `a failed liquid read surfaces as a refresh failure rather than a crash`() = runTest {
        coEvery { tonStakingApi.getNominatorPools(TON_ADDRESS) } returns emptyList()
        coEvery { liquidStakingService.getPoolState() } throws RuntimeException("tonapi down")

        val vm = createViewModel().also { it.setData(VAULT_ID) }

        assertTrue(vm.state.value is TonDeFiUiState.Error, "expected Error, was ${vm.state.value}")
    }

    private fun createViewModel(): TonDeFiPositionsViewModel =
        TonDeFiPositionsViewModel(
            vaultRepository = vaultRepository,
            tonStakingApi = tonStakingApi,
            liquidStakingService = liquidStakingService,
            balanceVisibilityRepository = balanceVisibilityRepository,
            tokenPriceRepository = tokenPriceRepository,
            appCurrencyRepository = appCurrencyRepository,
            snapshotCache = snapshotCache,
            navigator = navigator,
        )

    private companion object {
        /** tsTON→TON rate of 1.16, as the pool's `total_balance / supply` states it. */
        val POOL_STATE =
            TonLiquidPoolState(
                totalBalance = BigInteger.valueOf(116_000_000_000L),
                supply = BigInteger.valueOf(100_000_000_000L),
                apy = 13.36,
                minStake = BigInteger.valueOf(1_000_000_000L),
                isDepositOpen = true,
                isOptimistic = true,
            )

        /** A settled screen, as the cache would have it after the user walked away from one. */
        val LAST_RENDERED =
            TonStakingSnapshot(
                state =
                    TonDeFiUiState.Success(
                        tonData =
                            TonStakingUiModel(
                                totalAmountPrice = "$100.00",
                                ticker = "TON",
                                poolName = "Whales",
                                stakedDisplay = "50 GRAM",
                                stakedFiatDisplay = "$100.00",
                                hasPosition = true,
                            )
                    ),
                poolAddress = POOL,
                stakedDisplay = "50 GRAM",
            )

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
