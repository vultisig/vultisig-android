@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.defi

import com.vultisig.wallet.R
import com.vultisig.wallet.data.api.models.thorchain.ThorChainPoolStatsJson
import com.vultisig.wallet.data.blockchain.model.BondedNodePosition
import com.vultisig.wallet.data.blockchain.model.StakingDetails
import com.vultisig.wallet.data.blockchain.thorchain.DefaultStakingPositionService
import com.vultisig.wallet.data.blockchain.thorchain.RujiStakingService
import com.vultisig.wallet.data.blockchain.thorchain.TCYStakingService
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.ThorChainLpPosition
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.settings.AppCurrency
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.data.repositories.BalanceVisibilityRepository
import com.vultisig.wallet.data.repositories.DefiPositionsRepository
import com.vultisig.wallet.data.repositories.TokenPriceRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.GetThorChainLpPositionsUseCase
import com.vultisig.wallet.data.usecases.GetThorChainPendingLpDepositsUseCase
import com.vultisig.wallet.data.usecases.ThorChainLpPositions
import com.vultisig.wallet.data.usecases.ThorchainBondUseCase
import com.vultisig.wallet.data.utils.decimals
import com.vultisig.wallet.data.utils.symbol
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.screens.v2.defi.defaultSelectedPositionsDialog
import com.vultisig.wallet.ui.screens.v2.defi.model.DeFiNavActions
import com.vultisig.wallet.ui.utils.UiText
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import java.math.BigDecimal
import java.math.BigInteger
import java.text.NumberFormat
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import wallet.core.jni.CoinType

/**
 * Characterization tests: they pin the behavior this ViewModel has today so the pending
 * Thorchain/Maya deduplication can be verified as behavior-preserving. They describe what the code
 * does, not necessarily what it ought to do.
 *
 * WalletCore's `CoinTypeConfiguration` is a JNI call with no host-JVM binary, so `coinType.symbol`
 * and `coinType.decimals` are stubbed to THORChain's real values (RUNE, 8). Everything downstream —
 * `toValue`, `formatAmount`, `formatRuneReward` — then runs its real arithmetic.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class ThorchainDefiPositionsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var navigator: Navigator<Destination>
    private lateinit var vaultRepository: VaultRepository
    private lateinit var bondUseCase: ThorchainBondUseCase
    private lateinit var tokenPriceRepository: TokenPriceRepository
    private lateinit var appCurrencyRepository: AppCurrencyRepository
    private lateinit var rujiStakingService: RujiStakingService
    private lateinit var tcyStakingService: TCYStakingService
    private lateinit var defiPositionsRepository: DefiPositionsRepository
    private lateinit var defaultStakingPositionService: DefaultStakingPositionService
    private lateinit var balanceVisibilityRepository: BalanceVisibilityRepository
    private lateinit var getThorChainLpPositionsUseCase: GetThorChainLpPositionsUseCase
    private lateinit var getThorChainPendingLpDepositsUseCase: GetThorChainPendingLpDepositsUseCase

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic("com.vultisig.wallet.data.utils.CoinTypeKt")
        every { any<CoinType>().symbol } returns "RUNE"
        every { any<CoinType>().decimals } returns 8

        navigator = mockk(relaxed = true)
        vaultRepository = mockk(relaxed = true)
        bondUseCase = mockk(relaxed = true)
        tokenPriceRepository = mockk(relaxed = true)
        appCurrencyRepository = mockk(relaxed = true)
        rujiStakingService = mockk(relaxed = true)
        tcyStakingService = mockk(relaxed = true)
        defiPositionsRepository = mockk(relaxed = true)
        defaultStakingPositionService = mockk(relaxed = true)
        balanceVisibilityRepository = mockk(relaxed = true)
        getThorChainLpPositionsUseCase = mockk(relaxed = true)
        getThorChainPendingLpDepositsUseCase = mockk(relaxed = true)

        coEvery { vaultRepository.get(VAULT_ID) } returns VAULT
        coEvery { balanceVisibilityRepository.getVisibility(VAULT_ID) } returns true
        coEvery { appCurrencyRepository.currency } returns flowOf(AppCurrencyUsd)
        coEvery { appCurrencyRepository.getCurrencyFormat() } returns
            NumberFormat.getCurrencyInstance(Locale.US)
        coEvery { tokenPriceRepository.getCachedPrice(any(), any()) } returns BigDecimal("2")

        selectPositions()
        coEvery { bondUseCase.getActiveNodes(any(), any()) } returns flowOf(emptyList())
        coEvery { rujiStakingService.getStakingDetails(any(), any()) } returns flowOf()
        coEvery { tcyStakingService.getStakingDetails(any(), any()) } returns flowOf()
        coEvery { defaultStakingPositionService.getStakingDetails(any(), any()) } returns flowOf()
        coEvery { getThorChainLpPositionsUseCase.fetchAvailablePools(any()) } returns emptyList()
        coEvery { getThorChainPendingLpDepositsUseCase(any(), any()) } returns emptyList()
        coEvery { getThorChainLpPositionsUseCase(any(), any(), any(), any()) } returns
            ThorChainLpPositions()
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic("com.vultisig.wallet.data.utils.CoinTypeKt")
        Dispatchers.resetMain()
    }

    @Test
    fun `the persisted selection replaces the defaults verbatim`() = runTest {
        selectPositions("RUNE", "TCY")

        val vm = createViewModel().also { it.setData(VAULT_ID) }

        assertEquals(listOf("RUNE", "TCY"), vm.state.value.selectedPositions)
        assertTrue(vm.state.value.isBalanceVisible)
    }

    @Test
    fun `an empty persisted selection clears every tab rather than restoring defaults`() = runTest {
        // Unlike the Maya screen, THORChain applies the stored set as-is with no fallback.
        selectPositions()

        val vm = createViewModel().also { it.setData(VAULT_ID) }

        val state = vm.state.value
        assertTrue(state.selectedPositions.isEmpty())
        assertTrue(state.bonded.nodes.isEmpty())
        assertTrue(state.staking.positions.isEmpty())
        assertEquals(BigInteger.ZERO, vm.totalValueBond.value)
    }

    @Test
    fun `bonding resolves the RUNE address even when another THORChain coin comes first`() =
        runTest {
            selectPositions("RUNE")

            createViewModel().also { it.setData(VAULT_ID) }

            // VAULT lists RUJI ahead of RUNE; picking the first THORChain coin would bond the
            // wrong account.
            coVerify { bondUseCase.getActiveNodes(VAULT_ID, RUNE_ADDRESS) }
            coVerify(exactly = 0) { bondUseCase.getActiveNodes(VAULT_ID, RUJI_ADDRESS) }
        }

    @Test
    fun `bonded nodes are mapped and totalled at RUNE precision`() = runTest {
        selectPositions("RUNE")
        coEvery { bondUseCase.getActiveNodes(VAULT_ID, RUNE_ADDRESS) } returns
            flowOf(
                listOf(bondedNode(BigInteger("1000000000")), bondedNode(BigInteger("250000000")))
            )

        val vm = createViewModel().also { it.setData(VAULT_ID) }

        val bonded = vm.state.value.bonded
        assertFalse(bonded.isLoading)
        // (10 + 2.5) RUNE at 8 decimals.
        assertEquals("12.50000000 RUNE", bonded.totalBondedAmount)
        assertEquals("$25.00", bonded.totalBondedPrice)
        assertEquals(2, bonded.nodes.size)
        assertEquals(BigInteger("1250000000"), vm.totalValueBond.value)
    }

    @Test
    fun `deselecting the bond position empties the bonded tab`() = runTest {
        selectPositions("TCY")
        coEvery { bondUseCase.getActiveNodes(any(), any()) } returns
            flowOf(listOf(bondedNode(BigInteger("1000000000"))))

        val vm = createViewModel().also { it.setData(VAULT_ID) }

        assertTrue(vm.state.value.bonded.nodes.isEmpty())
        assertEquals(BigInteger.ZERO, vm.totalValueBond.value)
    }

    @Test
    fun `a vault without a RUNE coin settles the bonded tab instead of hanging`() = runTest {
        selectPositions("RUNE")
        coEvery { vaultRepository.get(VAULT_ID) } returns VAULT.copy(coins = listOf(RUJI_COIN))

        val vm = createViewModel().also { it.setData(VAULT_ID) }

        assertFalse(vm.state.value.bonded.isLoading)
        assertTrue(vm.state.value.bonded.nodes.isEmpty())
    }

    @Test
    fun `the RUJI position exposes withdraw only while rewards are positive`() = runTest {
        selectPositions("RUJI")
        coEvery { rujiStakingService.getStakingDetails(RUNE_ADDRESS, VAULT_ID) } returns
            flowOf(
                listOf(
                    stakingDetails(
                        coin = Coins.ThorChain.RUJI,
                        stakeAmount = BigInteger("500000000"),
                        apr = 0.2,
                        rewards = BigDecimal("1000000"),
                        rewardsCoin = Coins.ThorChain.RUNE,
                    )
                )
            )

        val vm = createViewModel().also { it.setData(VAULT_ID) }

        val position = vm.state.value.staking.positions.single { it.coin.id == RUJI_ID }
        assertEquals("5 RUJI", position.stakedAmountDisplay)
        assertEquals("$10.00", position.stakedFiatDisplay)
        assertEquals("20.00%", position.apy)
        assertTrue(position.canWithdraw)
        assertTrue(position.canUnstake)
        assertEquals(BigInteger("500000000"), vm.totalValueRujiStake.value)
    }

    @Test
    fun `both RUJI positions render as independent cards`() = runTest {
        // A holder of one, the other, or both must see exactly what they hold: the bonded position
        // carries the APR and the claimable USDC, the auto-compounding one is stat-free (#5419).
        selectPositions("RUJI")
        coEvery { rujiStakingService.getStakingDetails(RUNE_ADDRESS, VAULT_ID) } returns
            flowOf(
                listOf(
                    stakingDetails(
                        coin = Coins.ThorChain.RUJI,
                        stakeAmount = BigInteger("500000000"),
                        apr = 0.2,
                        rewards = BigDecimal("1000000"),
                        rewardsCoin = Coins.ThorChain.RUNE,
                    ),
                    stakingDetails(
                        coin = Coins.ThorChain.sRUJI,
                        stakeAmount = BigInteger("300000000"),
                    ),
                )
            )

        val vm = createViewModel().also { it.setData(VAULT_ID) }

        val bonded = vm.state.value.staking.positions.single { it.coin.id == RUJI_ID }
        val compounded =
            vm.state.value.staking.positions.single { it.coin.id == Coins.ThorChain.sRUJI.id }
        assertEquals("5 RUJI", bonded.stakedAmountDisplay)
        assertTrue(bonded.canWithdraw)
        // Valued in RUJI, not shown as a raw sRUJI share count.
        assertEquals("3 RUJI", compounded.stakedAmountDisplay)
        assertTrue(compounded.canUnstake)
        assertFalse(compounded.canWithdraw)
        assertEquals(null, compounded.apy)
        // Both are RUJI-denominated, so the tab's RUJI total is their sum.
        assertEquals(BigInteger("800000000"), vm.totalValueRujiStake.value)
    }

    @Test
    fun `a bonded-only holder still sees the bonded amount`() = runTest {
        // The regression this issue was filed for: the "Standard" pool holds no receipt, so the
        // auto-compounding zero must not suppress the bonded card.
        selectPositions("RUJI")
        coEvery { rujiStakingService.getStakingDetails(RUNE_ADDRESS, VAULT_ID) } returns
            flowOf(
                listOf(
                    stakingDetails(
                        coin = Coins.ThorChain.RUJI,
                        stakeAmount = BigInteger("500000000"),
                    ),
                    stakingDetails(coin = Coins.ThorChain.sRUJI, stakeAmount = BigInteger.ZERO),
                )
            )

        val vm = createViewModel().also { it.setData(VAULT_ID) }

        val bonded = vm.state.value.staking.positions.single { it.coin.id == RUJI_ID }
        assertEquals("5 RUJI", bonded.stakedAmountDisplay)
        assertTrue(bonded.canUnstake)
        assertFalse(
            vm.state.value.staking.positions
                .single { it.coin.id == Coins.ThorChain.sRUJI.id }
                .canUnstake
        )
    }

    @Test
    fun `a RUJI position with no rewards cannot withdraw`() = runTest {
        selectPositions("RUJI")
        coEvery { rujiStakingService.getStakingDetails(RUNE_ADDRESS, VAULT_ID) } returns
            flowOf(
                listOf(
                    stakingDetails(
                        coin = Coins.ThorChain.RUJI,
                        stakeAmount = BigInteger("500000000"),
                        rewards = BigDecimal.ZERO,
                    )
                )
            )

        val vm = createViewModel().also { it.setData(VAULT_ID) }

        assertFalse(vm.state.value.staking.positions.single { it.coin.id == RUJI_ID }.canWithdraw)
    }

    @Test
    fun `the TCY position is unstakeable but never withdrawable`() = runTest {
        selectPositions("TCY")
        coEvery { tcyStakingService.getStakingDetails(RUNE_ADDRESS, VAULT_ID) } returns
            flowOf(
                stakingDetails(coin = Coins.ThorChain.TCY, stakeAmount = BigInteger("300000000"))
            )

        val vm = createViewModel().also { it.setData(VAULT_ID) }

        val tcy = vm.state.value.staking.positions.single { it.coin.id == TCY_ID }
        assertFalse(tcy.canWithdraw)
        assertTrue(tcy.canUnstake)
        assertEquals("3 TCY", tcy.stakedAmountDisplay)
        assertEquals(BigInteger("300000000"), vm.totalValueTCYStake.value)
    }

    @Test
    fun `a non-RUJI position offering withdraw is corrected on the way into state`() = runTest {
        selectPositions("TCY")
        val vm = createViewModel().also { it.setData(VAULT_ID) }

        // Only RUJI pays claimable rewards. updateExistingPosition is the single choke point that
        // enforces it, so a caller wrongly setting canWithdraw must still land as false.
        vm.updateExistingPosition(
            stakePositionUiModel(coin = Coins.ThorChain.TCY, canWithdraw = true)
        )

        assertFalse(vm.state.value.staking.positions.single { it.coin.id == TCY_ID }.canWithdraw)
    }

    @Test
    fun `a RUJI position keeps the withdraw flag it was given`() = runTest {
        selectPositions("RUJI")
        val vm = createViewModel().also { it.setData(VAULT_ID) }

        vm.updateExistingPosition(
            stakePositionUiModel(coin = Coins.ThorChain.RUJI, canWithdraw = true)
        )

        assertTrue(vm.state.value.staking.positions.single { it.coin.id == RUJI_ID }.canWithdraw)
    }

    @Test
    fun `yRUNE is offered as mintable and sTCY as transferable`() = runTest {
        selectPositions("yRUNE", "sTCY")
        coEvery { defaultStakingPositionService.getStakingDetails(RUNE_ADDRESS, VAULT_ID) } returns
            flowOf(
                listOf(
                    stakingDetails(Coins.ThorChain.yRUNE, BigInteger("100000000")),
                    stakingDetails(Coins.ThorChain.sTCY, BigInteger("200000000")),
                )
            )

        val vm = createViewModel().also { it.setData(VAULT_ID) }

        val positions = vm.state.value.staking.positions
        val yRune = positions.single { it.coin.id == Coins.ThorChain.yRUNE.id }
        assertTrue(yRune.supportsMint)
        assertFalse(yRune.canTransfer)

        val sTcy = positions.single { it.coin.id == Coins.ThorChain.sTCY.id }
        assertTrue(sTcy.canTransfer)
        assertFalse(sTcy.supportsMint)
    }

    /**
     * These position tokens usually aren't vault coins, and the periodic price refresh only ever
     * covers vault coins — so unless the screen refreshes them itself they have no cache row, and
     * the contract fallback has nothing to hit. That combination is what left staked sTCY/yTCY
     * reading $0.00.
     */
    @Test
    fun `staking positions refresh their own prices rather than relying on vault membership`() =
        runTest {
            selectPositions("yRUNE", "sTCY")
            coEvery {
                defaultStakingPositionService.getStakingDetails(RUNE_ADDRESS, VAULT_ID)
            } returns
                flowOf(
                    listOf(
                        stakingDetails(Coins.ThorChain.yRUNE, BigInteger("100000000")),
                        stakingDetails(Coins.ThorChain.sTCY, BigInteger("200000000")),
                    )
                )

            createViewModel().also { it.setData(VAULT_ID) }

            coVerify {
                tokenPriceRepository.refresh(
                    match { coins ->
                        coins.map { it.id }.toSet() ==
                            setOf(Coins.ThorChain.yRUNE.id, Coins.ThorChain.sTCY.id)
                    }
                )
            }
        }

    @Test
    fun `a failed price refresh still prices the cards from the cache`() = runTest {
        selectPositions("sTCY")
        coEvery { defaultStakingPositionService.getStakingDetails(RUNE_ADDRESS, VAULT_ID) } returns
            flowOf(listOf(stakingDetails(Coins.ThorChain.sTCY, BigInteger("200000000"))))
        coEvery { tokenPriceRepository.refresh(any()) } throws RuntimeException("thornode down")

        val vm = createViewModel().also { it.setData(VAULT_ID) }

        // 2 sTCY at the cached 2 — the refresh is an optimization, not a precondition.
        vm.state.value.staking.positions
            .single { it.coin.id == Coins.ThorChain.sTCY.id }
            .stakedFiatDisplay shouldBe "$4.00"
    }

    @Test
    fun `a zero generic position cannot be unstaked`() = runTest {
        selectPositions("yTCY")
        coEvery { defaultStakingPositionService.getStakingDetails(RUNE_ADDRESS, VAULT_ID) } returns
            flowOf(listOf(stakingDetails(Coins.ThorChain.yTCY, BigInteger.ZERO)))

        val vm = createViewModel().also { it.setData(VAULT_ID) }

        assertFalse(
            vm.state.value.staking.positions
                .single { it.coin.id == Coins.ThorChain.yTCY.id }
                .canUnstake
        )
    }

    @Test
    fun `the LP tab stays loading until the available-pool fetch resolves`() = runTest {
        selectPositions("RUNE")
        coEvery { getThorChainLpPositionsUseCase.fetchAvailablePools(any()) } throws
            RuntimeException("midgard down")

        val vm = createViewModel().also { it.setData(VAULT_ID) }

        // availablePools stays null so the tab must not flash an empty state.
        assertTrue(vm.state.value.lp.isLoading)
        assertFalse(vm.state.value.lpDialogLoaded)
    }

    @Test
    fun `a selected pool with no liquidity still renders a placeholder card`() = runTest {
        selectPositions(BTC_POOL)
        coEvery { getThorChainLpPositionsUseCase.fetchAvailablePools(any()) } returns
            listOf(poolStats(BTC_POOL))

        val vm = createViewModel().also { it.setData(VAULT_ID) }

        val lp = vm.state.value.lp
        assertFalse(lp.isLoading)
        val position = lp.positions.single()
        assertEquals("RUNE/BTC Pool", position.titleLp)
        assertEquals("0 RUNE + 0 BTC", position.position)
        assertEquals("$0.00", position.totalPriceLp)
        // Nothing to withdraw from an empty position.
        assertFalse(position.canRemove)
        assertNull(position.apr)
    }

    @Test
    fun `LP value counts toward the header total`() = runTest {
        // A vault holding only LP used to read $0.00 in the header while the LP cards below it
        // showed real money: the total summed bond and stake but never LP.
        selectPositions(BTC_POOL)
        coEvery { getThorChainLpPositionsUseCase.fetchAvailablePools(any()) } returns
            listOf(poolStats(BTC_POOL))
        coEvery { getThorChainLpPositionsUseCase(any(), any(), any(), any()) } returns
            ThorChainLpPositions(
                listOf(lpPosition(BTC_POOL, runeRedeem = "300000000", assetRedeem = "100000000"))
            )

        val vm = createViewModel().also { it.setData(VAULT_ID) }

        // 3 RUNE at 2 plus 1 BTC at 2. The vault holds no BTC coin, but the pool names its asset
        // by chain and ticker, which resolves to the curated BTC and so to a real price — that
        // side used to read zero because it went straight to a contract lookup BTC has no
        // contract address for.
        vm.totalValueLpFiat.value shouldBe
            LpLegTotal.Priced(FiatValue(BigDecimal("8.00"), AppCurrencyUsd.ticker))
        vm.state.value.lp.positions.single().totalPriceLp shouldBe "$8.00"
        vm.state.value.totalAmountPrice shouldBe "$8.00"
    }

    @Test
    fun `a failed bond load settles the header total instead of stranding it`() = runTest {
        // The bonded .catch cleared only the tab's own spinner, so the total-loading flag stayed
        // set and the header never rendered a value at all.
        selectPositions("RUNE")
        coEvery { bondUseCase.getActiveNodes(VAULT_ID, RUNE_ADDRESS) } returns
            flow { throw RuntimeException("thornode down") }

        val vm = createViewModel().also { it.setData(VAULT_ID) }

        val state = vm.state.value
        state.bonded.isLoading shouldBe false
        state.isTotalAmountLoading shouldBe false
        state.bonded.totalBondedPrice shouldBe "$0.00"
        state.totalAmountPrice shouldBe "$0.00"
    }

    @Test
    fun `a failed staking load leaves the card priced at zero rather than blank`() = runTest {
        // Cards used to be seeded with a bare ticker and no fiat, so a failed load rendered as a
        // lone "RUJI" with the dollar line hidden entirely.
        selectPositions("RUJI")
        coEvery { rujiStakingService.getStakingDetails(any(), any()) } returns
            flow { throw RuntimeException("thornode down") }

        val vm = createViewModel().also { it.setData(VAULT_ID) }

        val positions = vm.state.value.staking.positions
        positions.isNotEmpty() shouldBe true
        positions.forEach { position ->
            position.isLoading shouldBe false
            position.stakedAmountDisplay shouldBe "0 ${position.coin.ticker}"
            position.stakedFiatDisplay shouldBe "$0.00"
        }
    }

    @Test
    fun `a vault without RUNE settles the header total rather than shimmering forever`() = runTest {
        // Both legs bail out early here, and neither used to release the total-loading flag.
        selectPositions("RUNE", "RUJI")
        coEvery { vaultRepository.get(VAULT_ID) } returns VAULT.copy(coins = listOf(RUJI_COIN))

        val vm = createViewModel().also { it.setData(VAULT_ID) }

        val state = vm.state.value
        state.isTotalAmountLoading shouldBe false
        state.totalAmountPrice shouldBe "$0.00"
    }

    @Test
    fun `the zero balance is formatted in the users currency, not hardcoded dollars`() = runTest {
        val germanFormat = NumberFormat.getCurrencyInstance(Locale.GERMANY)
        coEvery { appCurrencyRepository.getCurrencyFormat() } returns germanFormat
        selectPositions("RUJI")
        coEvery { rujiStakingService.getStakingDetails(any(), any()) } returns
            flow { throw RuntimeException("thornode down") }

        val vm = createViewModel().also { it.setData(VAULT_ID) }

        // Compare against the locale's own zero rather than merely asserting the absence of "$":
        // the unavailable dash would pass that weaker check while meaning the opposite.
        vm.state.value.staking.positions.first().stakedFiatDisplay shouldBe
            germanFormat.format(BigDecimal.ZERO)
    }

    @Test
    fun `a bond load that throws before the flow starts still settles the header`() = runTest {
        // The outer catch wraps the vault lookup. It used to drop the total-loading flag straight
        // into the UI state without reporting the leg, so the price pipeline never ran again and
        // the header sat on a permanent dash with no spinner to explain it.
        selectPositions("RUNE")
        coEvery { vaultRepository.get(VAULT_ID) } throws RuntimeException("db closed")

        val vm = createViewModel().also { it.setData(VAULT_ID) }

        val state = vm.state.value
        state.bonded.isLoading shouldBe false
        state.bonded.totalBondedPrice shouldBe "$0.00"
        state.isTotalAmountLoading shouldBe false
        state.totalAmountPrice shouldBe "$0.00"
    }

    @Test
    fun `the header total waits for the LP leg before settling`() = runTest {
        // LP joined the total but never gated it, so the header settled on bond and stake alone
        // and then jumped once the LP value landed. Holding the pool fetch keeps the LP tab parked
        // before it can report, which is exactly the window the old code published a total in.
        selectPositions("RUNE", BTC_POOL)
        val heldPools = MutableStateFlow<List<ThorChainPoolStatsJson>?>(null)
        coEvery { getThorChainLpPositionsUseCase.fetchAvailablePools(any()) } coAnswers
            {
                heldPools.filterNotNull().first()
            }
        coEvery { getThorChainLpPositionsUseCase(any(), any(), any(), any()) } returns
            ThorChainLpPositions(
                listOf(lpPosition(BTC_POOL, runeRedeem = "300000000", assetRedeem = "0"))
            )

        val vm = createViewModel().also { it.setData(VAULT_ID) }

        // Bond has already reported an empty node list; LP has not reported at all.
        vm.state.value.isTotalAmountLoading shouldBe true
        vm.state.value.totalAmountPrice shouldBe null

        heldPools.value = listOf(poolStats(BTC_POOL))

        vm.state.value.isTotalAmountLoading shouldBe false
        vm.state.value.totalAmountPrice shouldBe "$6.00"
    }

    @Test
    fun `a failed RUJI load resets its leg instead of keeping the previous total`() = runTest {
        // The .catch settled the cards but left the RUJI raw total untouched, so a refresh that
        // failed kept pricing the header off the amount from the run before it.
        selectPositions("RUJI")
        coEvery { rujiStakingService.getStakingDetails(any(), any()) } returns
            flowOf(listOf(stakingDetails(Coins.ThorChain.RUJI, BigInteger("100000000"))))

        val vm = createViewModel().also { it.setData(VAULT_ID) }
        vm.state.value.totalAmountPrice shouldBe "$2.00"

        coEvery { rujiStakingService.getStakingDetails(any(), any()) } returns
            flow { throw RuntimeException("thornode down") }
        vm.setData(VAULT_ID)

        vm.totalValueRujiStake.value shouldBe BigInteger.ZERO
        vm.state.value.isTotalAmountLoading shouldBe false
        vm.state.value.totalAmountPrice shouldBe "$0.00"
    }

    @Test
    fun `a failed LP load leaves the placeholder priced at zero, not unavailable`() = runTest {
        // The placeholder used to snapshot a zero still being resolved on another coroutine, and a
        // failed load then froze that null in as the terminal state.
        selectPositions(BTC_POOL)
        coEvery { getThorChainLpPositionsUseCase.fetchAvailablePools(any()) } returns
            listOf(poolStats(BTC_POOL))
        coEvery { getThorChainLpPositionsUseCase(any(), any(), any(), any()) } throws
            RuntimeException("midgard down")

        val vm = createViewModel().also { it.setData(VAULT_ID) }

        val state = vm.state.value
        state.lp.isLoading shouldBe false
        state.lp.positions.single().totalPriceLp shouldBe "$0.00"
        state.isTotalAmountLoading shouldBe false
    }

    @Test
    fun `switching currency re-prices LP instead of relabelling the old magnitude`() = runTest {
        // LP is stored already converted, so it cannot be re-based the way the raw legs are. The
        // total used to stamp the new currency's ticker onto the old magnitude, summing EUR-priced
        // bond against USD-priced LP under one symbol.
        selectPositions("RUNE", BTC_POOL)
        val currency = MutableStateFlow(AppCurrencyUsd)
        coEvery { appCurrencyRepository.currency } returns currency
        coEvery { getThorChainLpPositionsUseCase.fetchAvailablePools(any()) } returns
            listOf(poolStats(BTC_POOL))
        coEvery { getThorChainLpPositionsUseCase(any(), any(), any(), any()) } returns
            ThorChainLpPositions(
                listOf(lpPosition(BTC_POOL, runeRedeem = "300000000", assetRedeem = "0"))
            )

        val vm = createViewModel().also { it.setData(VAULT_ID) }
        vm.totalValueLpFiat.value shouldBe
            LpLegTotal.Priced(FiatValue(BigDecimal("6.00"), AppCurrencyUsd.ticker))

        // A different EUR price on purpose: with both currencies priced the same, a magnitude
        // carried across unchanged and one genuinely re-converted are the same number, and the
        // regression this test is named for would slip through a formatter-only assertion.
        coEvery { tokenPriceRepository.getCachedPrice(any(), AppCurrency.EUR) } returns
            BigDecimal("3")
        coEvery { appCurrencyRepository.getCurrencyFormat() } returns
            NumberFormat.getCurrencyInstance(Locale.GERMANY)
        currency.value = AppCurrency.EUR

        // 3 RUNE at the EUR price of 3, not the 6.00 it was worth in USD.
        vm.totalValueLpFiat.value shouldBe
            LpLegTotal.Priced(FiatValue(BigDecimal("9.00"), AppCurrency.EUR.ticker))
        vm.state.value.isTotalAmountLoading shouldBe false
        vm.state.value.totalAmountPrice shouldBe germanFormat.format(BigDecimal("9.00"))
    }

    @Test
    fun `switching currency parks the header on its spinner, not on the old total`() = runTest {
        // Dropping the LP leg stops the combine from emitting, so the header simply kept its
        // settled prior-currency figure — no spinner, no sign anything was in flight — for the
        // whole refetch.
        selectPositions("RUNE", BTC_POOL)
        val currency = MutableStateFlow(AppCurrencyUsd)
        coEvery { appCurrencyRepository.currency } returns currency
        coEvery { getThorChainLpPositionsUseCase.fetchAvailablePools(any()) } returns
            listOf(poolStats(BTC_POOL))
        val heldPositions =
            MutableStateFlow<ThorChainLpPositions?>(
                ThorChainLpPositions(
                    listOf(lpPosition(BTC_POOL, runeRedeem = "300000000", assetRedeem = "0"))
                )
            )
        coEvery { getThorChainLpPositionsUseCase(any(), any(), any(), any()) } coAnswers
            {
                heldPositions.filterNotNull().first()
            }

        val vm = createViewModel().also { it.setData(VAULT_ID) }
        vm.state.value.totalAmountPrice shouldBe "$6.00"

        // Hold the re-price so the switch can be observed mid-flight.
        heldPositions.value = null
        coEvery { appCurrencyRepository.getCurrencyFormat() } returns
            NumberFormat.getCurrencyInstance(Locale.GERMANY)
        currency.value = AppCurrency.EUR

        vm.state.value.totalAmountPrice shouldBe null
        vm.state.value.isTotalAmountLoading shouldBe true

        heldPositions.value =
            ThorChainLpPositions(
                listOf(lpPosition(BTC_POOL, runeRedeem = "300000000", assetRedeem = "0"))
            )

        vm.state.value.isTotalAmountLoading shouldBe false
        vm.state.value.totalAmountPrice shouldBe germanFormat.format(BigDecimal("6.00"))
    }

    @Test
    fun `switching currency re-prices the bonded and staking cards, not just the header`() =
        runTest {
            // Card fiat strings are formatted once per load from one-shot flows, so nothing
            // re-converts on its own. The header moved to the new currency while Bonded and
            // Staking kept showing the old one.
            selectPositions("RUNE", "TCY")
            val currency = MutableStateFlow(AppCurrencyUsd)
            coEvery { appCurrencyRepository.currency } returns currency
            coEvery { bondUseCase.getActiveNodes(VAULT_ID, RUNE_ADDRESS) } returns
                flowOf(listOf(bondedNode(BigInteger("100000000"))))
            coEvery { tcyStakingService.getStakingDetails(any(), any()) } returns
                flowOf(stakingDetails(Coins.ThorChain.TCY, BigInteger("100000000")))

            val vm = createViewModel().also { it.setData(VAULT_ID) }
            vm.state.value.bonded.totalBondedPrice shouldBe "$2.00"

            // A different EUR price, so re-converting and merely re-formatting produce different
            // numbers.
            coEvery { tokenPriceRepository.getCachedPrice(any(), AppCurrency.EUR) } returns
                BigDecimal("3")
            coEvery { appCurrencyRepository.getCurrencyFormat() } returns
                NumberFormat.getCurrencyInstance(Locale.GERMANY)
            currency.value = AppCurrency.EUR

            val expected = germanFormat.format(BigDecimal("3.00"))
            vm.state.value.bonded.totalBondedPrice shouldBe expected
            vm.state.value.staking.positions
                .single { it.coin.id == Coins.ThorChain.TCY.id }
                .stakedFiatDisplay shouldBe expected
        }

    @Test
    fun `a superseded staking load cannot overwrite the load that replaced it`() = runTest {
        // loadStakingPositions launched untracked, so a currency switch left two loads running at
        // once. The older one had no idea it had been replaced, and wrote its cards and its leg
        // whenever its fetch happened to land — after the newer one, if it was the slower of them.
        selectPositions("TCY")
        val currency = MutableStateFlow(AppCurrencyUsd)
        coEvery { appCurrencyRepository.currency } returns currency

        val supersededLoad = MutableStateFlow<StakingDetails?>(null)
        var loads = 0
        coEvery { tcyStakingService.getStakingDetails(any(), any()) } coAnswers
            {
                if (loads++ == 0) {
                    flow { emit(supersededLoad.filterNotNull().first()) }
                } else {
                    flowOf(stakingDetails(Coins.ThorChain.TCY, BigInteger("200000000")))
                }
            }

        val vm = createViewModel().also { it.setData(VAULT_ID) }

        coEvery { tokenPriceRepository.getCachedPrice(any(), AppCurrency.EUR) } returns
            BigDecimal("3")
        coEvery { appCurrencyRepository.getCurrencyFormat() } returns
            NumberFormat.getCurrencyInstance(Locale.GERMANY)
        currency.value = AppCurrency.EUR

        vm.totalValueTCYStake.value shouldBe BigInteger("200000000")
        vm.state.value.totalAmountPrice shouldBe germanFormat.format(BigDecimal("6.00"))

        // The first load's fetch lands at last, carrying the amount it read before the switch.
        supersededLoad.value = stakingDetails(Coins.ThorChain.TCY, BigInteger("100000000"))

        vm.totalValueTCYStake.value shouldBe BigInteger("200000000")
        vm.state.value.totalAmountPrice shouldBe germanFormat.format(BigDecimal("6.00"))
    }

    @Test
    fun `a superseded staking load leaves its leg to the load that replaced it`() = runTest {
        // Cancelling the old load is only half of it: onCompletion runs on the way out too, and
        // reporting zero there hands the replacement's still-pending leg a value it never sent,
        // settling the header on a total with the staking amount missing from it.
        selectPositions("TCY")
        val currency = MutableStateFlow(AppCurrencyUsd)
        coEvery { appCurrencyRepository.currency } returns currency

        val replacementLoad = MutableStateFlow<StakingDetails?>(null)
        var loads = 0
        coEvery { tcyStakingService.getStakingDetails(any(), any()) } coAnswers
            {
                if (loads++ == 0) {
                    flow<StakingDetails> { awaitCancellation() }
                } else {
                    flow { emit(replacementLoad.filterNotNull().first()) }
                }
            }

        val vm = createViewModel().also { it.setData(VAULT_ID) }

        coEvery { appCurrencyRepository.getCurrencyFormat() } returns
            NumberFormat.getCurrencyInstance(Locale.GERMANY)
        currency.value = AppCurrency.EUR

        vm.totalValueTCYStake.value shouldBe null
        vm.state.value.isTotalAmountLoading shouldBe true
        vm.state.value.totalAmountPrice shouldBe null

        replacementLoad.value = stakingDetails(Coins.ThorChain.TCY, BigInteger("100000000"))

        vm.totalValueTCYStake.value shouldBe BigInteger("100000000")
        vm.state.value.isTotalAmountLoading shouldBe false
    }

    @Test
    fun `a bonded collector dropped for a newer refresh leaves its leg to that refresh`() =
        runTest {
            // Same shape on the bonded side, where flatMapLatest is what does the dropping: the
            // collector it discards used to report zero on its way out, understating the header
            // for as long as the refresh that replaced it took to arrive.
            selectPositions("RUNE")
            val currency = MutableStateFlow(AppCurrencyUsd)
            coEvery { appCurrencyRepository.currency } returns currency

            var collections = 0
            coEvery { bondUseCase.getActiveNodes(VAULT_ID, RUNE_ADDRESS) } returns
                flow {
                    if (collections++ == 0) {
                        emit(listOf(bondedNode(BigInteger("100000000"))))
                    }
                    // getActiveNodes stays open on a live feed; being dropped is how it ends.
                    awaitCancellation()
                }

            val vm = createViewModel().also { it.setData(VAULT_ID) }
            vm.state.value.totalAmountPrice shouldBe "$2.00"

            coEvery { appCurrencyRepository.getCurrencyFormat() } returns
                NumberFormat.getCurrencyInstance(Locale.GERMANY)
            currency.value = AppCurrency.EUR

            vm.state.value.isTotalAmountLoading shouldBe true
            vm.state.value.totalAmountPrice shouldBe null
        }

    @Test
    fun `a pool that failed to load makes the header unavailable rather than understated`() =
        runTest {
            // The per-pool failure is swallowed inside the use case, so the pool reads as zero
            // liquidity. Folding that in produced a header total that looked as settled as a
            // correct one while silently understating what the vault holds.
            selectPositions("RUNE", BTC_POOL)
            coEvery { getThorChainLpPositionsUseCase.fetchAvailablePools(any()) } returns
                listOf(poolStats(BTC_POOL))
            coEvery { getThorChainLpPositionsUseCase(any(), any(), any(), any()) } returns
                ThorChainLpPositions(positions = emptyList(), failedPools = setOf(BTC_POOL))

            val vm = createViewModel().also { it.setData(VAULT_ID) }

            val state = vm.state.value
            state.isTotalAmountLoading shouldBe false
            state.totalAmountPrice shouldBe null
            state.lp.positions.single().totalPriceLp shouldBe null
        }

    @Test
    fun `a failure in a pool the user did not select leaves the total priced`() = runTest {
        // The use case queries every available pool, so an unrelated pool erroring must not blank
        // a total the user's own positions priced perfectly well.
        selectPositions("RUNE", BTC_POOL)
        coEvery { getThorChainLpPositionsUseCase.fetchAvailablePools(any()) } returns
            listOf(poolStats(BTC_POOL), poolStats(ETH_POOL))
        coEvery { getThorChainLpPositionsUseCase(any(), any(), any(), any()) } returns
            ThorChainLpPositions(
                positions =
                    listOf(lpPosition(BTC_POOL, runeRedeem = "300000000", assetRedeem = "0")),
                failedPools = setOf(ETH_POOL),
            )

        val vm = createViewModel().also { it.setData(VAULT_ID) }

        vm.state.value.totalAmountPrice shouldBe "$6.00"
    }

    @Test
    fun `unbond carries the raw bonded amount resolved from the live node list`() = runTest {
        selectPositions("RUNE")
        coEvery { bondUseCase.getActiveNodes(VAULT_ID, RUNE_ADDRESS) } returns
            flowOf(listOf(bondedNode(BigInteger("777000000"))))

        val vm = createViewModel().also { it.setData(VAULT_ID) }
        vm.onClickUnBond(NODE_ADDRESS)

        coVerify(exactly = 1) {
            navigator.route(
                Route.Send(
                    vaultId = VAULT_ID,
                    type = DeFiNavActions.UNBOND.type,
                    chainId = Chain.ThorChain.id,
                    tokenId = Coins.ThorChain.RUNE.id,
                    address = NODE_ADDRESS,
                    bondedAmount = "777000000",
                )
            )
        }
    }

    @Test
    fun `unbonding an unknown node sends no bonded amount`() = runTest {
        selectPositions("RUNE")

        val vm = createViewModel().also { it.setData(VAULT_ID) }
        vm.onClickUnBond("thor1unknownnode")

        coVerify(exactly = 1) {
            navigator.route(
                Route.Send(
                    vaultId = VAULT_ID,
                    type = DeFiNavActions.UNBOND.type,
                    chainId = Chain.ThorChain.id,
                    tokenId = Coins.ThorChain.RUNE.id,
                    address = "thor1unknownnode",
                    bondedAmount = null,
                )
            )
        }
    }

    @Test
    fun `bond routes to send against the RUNE token`() = runTest {
        val vm = createViewModel().also { it.setData(VAULT_ID) }

        vm.onClickBond(NODE_ADDRESS)
        vm.bondToNode()

        coVerify(exactly = 1) {
            navigator.route(
                Route.Send(
                    vaultId = VAULT_ID,
                    type = DeFiNavActions.BOND.type,
                    chainId = Chain.ThorChain.id,
                    tokenId = Coins.ThorChain.RUNE.id,
                    address = NODE_ADDRESS,
                )
            )
        }
        coVerify(exactly = 1) {
            navigator.route(
                Route.Send(
                    vaultId = VAULT_ID,
                    type = DeFiNavActions.BOND.type,
                    chainId = Chain.ThorChain.id,
                    tokenId = Coins.ThorChain.RUNE.id,
                )
            )
        }
    }

    @Test
    fun `a vault without RUNE routes nowhere on bond`() = runTest {
        coEvery { vaultRepository.get(VAULT_ID) } returns VAULT.copy(coins = listOf(RUJI_COIN))

        val vm = createViewModel().also { it.setData(VAULT_ID) }
        vm.onClickBond(NODE_ADDRESS)

        coVerify(exactly = 0) { navigator.route(any<Route.Send>()) }
    }

    @Test
    fun `staking actions carry the token the action operates on`() = runTest {
        val vm = createViewModel().also { it.setData(VAULT_ID) }

        vm.onNavigateToFunctions(DeFiNavActions.REDEEM_YRUNE)
        vm.onNavigateToFunctions(DeFiNavActions.UNSTAKE_STCY)
        vm.onNavigateToFunctions(DeFiNavActions.MINT_YRUNE)

        // Redeem burns the yToken; minting spends the underlying. Swapping them would route the
        // user into the wrong asset's balance.
        coVerify { navigator.route(match<Route.Send> { it.tokenId == Coins.ThorChain.yRUNE.id }) }
        coVerify { navigator.route(match<Route.Send> { it.tokenId == Coins.ThorChain.sTCY.id }) }
        coVerify {
            navigator.route(
                match<Route.Send> {
                    it.tokenId == Coins.ThorChain.RUNE.id &&
                        it.type == DeFiNavActions.MINT_YRUNE.type
                }
            )
        }
    }

    @Test
    fun `an action with no token mapping falls back to the deposit flow`() = runTest {
        val vm = createViewModel().also { it.setData(VAULT_ID) }

        vm.onNavigateToFunctions(DeFiNavActions.ADD_LP)

        coVerify(exactly = 1) {
            navigator.route(
                Route.Deposit(
                    vaultId = VAULT_ID,
                    chainId = Chain.ThorChain.id,
                    depositType = DeFiNavActions.ADD_LP.type,
                )
            )
        }
    }

    @Test
    fun `add and remove LP route with the pool id`() = runTest {
        val vm = createViewModel().also { it.setData(VAULT_ID) }

        vm.onClickAddLp(BTC_POOL)
        vm.onClickRemoveLp(BTC_POOL)

        coVerify(exactly = 1) {
            navigator.route(
                Route.Deposit(
                    vaultId = VAULT_ID,
                    chainId = Chain.ThorChain.id,
                    depositType = DeFiNavActions.ADD_LP.type,
                    poolId = BTC_POOL,
                )
            )
        }
        coVerify(exactly = 1) {
            navigator.route(
                Route.Deposit(
                    vaultId = VAULT_ID,
                    chainId = Chain.ThorChain.id,
                    depositType = DeFiNavActions.REMOVE_LP.type,
                    poolId = BTC_POOL,
                )
            )
        }
    }

    @Test
    fun `opening the selection dialog rebases the draft on the committed selection`() = runTest {
        selectPositions("RUNE")
        val vm = createViewModel().also { it.setData(VAULT_ID) }

        vm.onPositionSelectionChange("TCY", isSelected = true)
        vm.setPositionSelectionDialogVisibility(true)

        val state = vm.state.value
        assertTrue(state.showPositionSelectionDialog)
        assertEquals(state.selectedPositions, state.tempSelectedPositions)
    }

    @Test
    fun `confirming the dialog persists the draft and commits it`() = runTest {
        selectPositions("RUNE")
        val vm = createViewModel().also { it.setData(VAULT_ID) }

        vm.setPositionSelectionDialogVisibility(true)
        vm.onPositionSelectionChange("TCY", isSelected = true)
        vm.onPositionSelectionChange("RUNE", isSelected = false)
        vm.onPositionSelectionDone()

        coVerify(exactly = 1) {
            defiPositionsRepository.saveSelectedPositions(Chain.ThorChain, VAULT_ID, listOf("TCY"))
        }
        val state = vm.state.value
        assertFalse(state.showPositionSelectionDialog)
        assertEquals(listOf("TCY"), state.selectedPositions)
    }

    @Test
    fun `adding a pool parks the header on its spinner, not on the pre-add total`() = runTest {
        // Confirming the dialog reloads every leg exactly as a currency switch does, but left the
        // legs holding their pre-selection values, so the header read as a settled total — short by
        // the pool just added — for the whole refetch.
        selectPositions("RUNE", BTC_POOL)
        coEvery { getThorChainLpPositionsUseCase.fetchAvailablePools(any()) } returns
            listOf(poolStats(BTC_POOL), poolStats(ETH_POOL))
        val bothPools =
            ThorChainLpPositions(
                listOf(
                    lpPosition(BTC_POOL, runeRedeem = "300000000", assetRedeem = "0"),
                    lpPosition(ETH_POOL, runeRedeem = "300000000", assetRedeem = "0"),
                )
            )
        val heldPositions =
            MutableStateFlow<ThorChainLpPositions?>(
                ThorChainLpPositions(
                    listOf(lpPosition(BTC_POOL, runeRedeem = "300000000", assetRedeem = "0"))
                )
            )
        coEvery { getThorChainLpPositionsUseCase(any(), any(), any(), any()) } coAnswers
            {
                heldPositions.filterNotNull().first()
            }

        val vm = createViewModel().also { it.setData(VAULT_ID) }
        vm.state.value.totalAmountPrice shouldBe "$6.00"

        // Hold the refetch so the add can be observed mid-flight.
        heldPositions.value = null
        vm.setPositionSelectionDialogVisibility(true)
        vm.onPositionSelectionChange(ETH_POOL, isSelected = true)
        vm.onPositionSelectionDone()

        vm.state.value.totalAmountPrice shouldBe null
        vm.state.value.isTotalAmountLoading shouldBe true

        heldPositions.value = bothPools

        vm.state.value.isTotalAmountLoading shouldBe false
        vm.state.value.totalAmountPrice shouldBe "$12.00"
    }

    @Test
    fun `confirming a selection nothing changed in leaves the header settled`() = runTest {
        // Done is reachable without touching a checkbox, and reloading for it would blank a settled
        // header to a spinner and refetch all three legs for an identical selection.
        selectPositions("RUNE", BTC_POOL)
        coEvery { getThorChainLpPositionsUseCase.fetchAvailablePools(any()) } returns
            listOf(poolStats(BTC_POOL))
        val heldPositions =
            MutableStateFlow<ThorChainLpPositions?>(
                ThorChainLpPositions(
                    listOf(lpPosition(BTC_POOL, runeRedeem = "300000000", assetRedeem = "0"))
                )
            )
        coEvery { getThorChainLpPositionsUseCase(any(), any(), any(), any()) } coAnswers
            {
                heldPositions.filterNotNull().first()
            }

        val vm = createViewModel().also { it.setData(VAULT_ID) }
        vm.state.value.totalAmountPrice shouldBe "$6.00"

        // Hold the refetch: a reload would leave the header on its spinner here.
        heldPositions.value = null
        vm.setPositionSelectionDialogVisibility(true)
        vm.onPositionSelectionDone()

        assertFalse(vm.state.value.showPositionSelectionDialog)
        vm.state.value.isTotalAmountLoading shouldBe false
        vm.state.value.totalAmountPrice shouldBe "$6.00"
        coVerify(exactly = 0) {
            defiPositionsRepository.saveSelectedPositions(Chain.ThorChain, VAULT_ID, any())
        }
    }

    @Test
    fun `switching tabs only changes the selected tab`() = runTest {
        selectPositions("RUNE")
        val vm = createViewModel().also { it.setData(VAULT_ID) }
        val before = vm.state.value

        vm.onTabSelected(com.vultisig.wallet.ui.screens.v2.defi.DeFiTab.LP)

        val after = vm.state.value
        assertEquals(
            com.vultisig.wallet.ui.screens.v2.defi.DeFiTab.LP.displayNameRes,
            after.selectedTab,
        )
        assertEquals(before.selectedPositions, after.selectedPositions)
        assertEquals(before.bonded, after.bonded)
    }

    @Test
    fun `a vault that never chose on this chain gets the default selection`() = runTest {
        // The store no longer holds defaults of its own: null is "never chose", and it is the only
        // case they apply to. An empty set is a selection the user cleared and stays empty.
        coEvery { defiPositionsRepository.getSelectedPositions(Chain.ThorChain, VAULT_ID) } returns
            flowOf<Set<String>?>(null)

        val vm = createViewModel().also { it.setData(VAULT_ID) }

        vm.state.value.selectedPositions shouldBe defaultSelectedPositionsDialog()
    }

    private fun selectPositions(vararg keys: String) {
        coEvery { defiPositionsRepository.getSelectedPositions(Chain.ThorChain, VAULT_ID) } returns
            flowOf(keys.toSet())
    }

    private fun bondedNode(amount: BigInteger) =
        BondedNodePosition(
            id = "rune-node",
            node = BondedNodePosition.BondedNode(address = NODE_ADDRESS, state = "active"),
            amount = amount,
            coin = RUNE_COIN,
            apy = 0.1,
            nextReward = 0.0,
            nextChurn = null,
        )

    private fun stakingDetails(
        coin: Coin,
        stakeAmount: BigInteger,
        apr: Double? = null,
        rewards: BigDecimal? = null,
        rewardsCoin: Coin? = null,
    ) =
        StakingDetails(
            id = "${coin.id}-default",
            coin = coin,
            stakeAmount = stakeAmount,
            apr = apr,
            estimatedRewards = null,
            nextPayoutDate = null,
            rewards = rewards,
            rewardsCoin = rewardsCoin,
        )

    private fun stakePositionUiModel(coin: Coin, canWithdraw: Boolean) =
        StakePositionUiModel(
            coin = coin,
            stakeAssetHeader = UiText.StringResource(R.string.staked_tcy_header),
            stakedAmountDisplay = "1 ${coin.ticker}",
            apy = null,
            canWithdraw = canWithdraw,
        )

    private fun poolStats(asset: String) =
        ThorChainPoolStatsJson(asset = asset, status = "available")

    private fun lpPosition(pool: String, runeRedeem: String, assetRedeem: String) =
        ThorChainLpPosition(
            pool = pool,
            units = BigInteger("1"),
            runeRedeemValue = BigInteger(runeRedeem),
            assetRedeemValue = BigInteger(assetRedeem),
            annualPercentageRate = null,
        )

    private fun createViewModel(): ThorchainDefiPositionsViewModel =
        ThorchainDefiPositionsViewModel(
            navigator = navigator,
            vaultRepository = vaultRepository,
            bondUseCase = bondUseCase,
            tokenPriceRepository = tokenPriceRepository,
            // Real calculator over the mocked price repository, so fiat assertions stay end-to-end.
            fiatValueCalculator = DefiFiatValueCalculator(tokenPriceRepository),
            appCurrencyRepository = appCurrencyRepository,
            rujiStakingService = rujiStakingService,
            tcyStakingService = tcyStakingService,
            defiPositionsRepository = defiPositionsRepository,
            defaultStakingPositionService = defaultStakingPositionService,
            balanceVisibilityRepository = balanceVisibilityRepository,
            getThorChainLpPositionsUseCase = getThorChainLpPositionsUseCase,
            getThorChainPendingLpDepositsUseCase = getThorChainPendingLpDepositsUseCase,
            ioDispatcher = testDispatcher,
        )

    private companion object {
        const val VAULT_ID = "vault-1"
        const val RUNE_ADDRESS = "thor1runeaddress"
        const val RUJI_ADDRESS = "thor1rujiaddress"
        const val NODE_ADDRESS = "thor1nodeaddress"
        const val BTC_POOL = "BTC.BTC"
        const val ETH_POOL = "ETH.ETH"

        val AppCurrencyUsd = com.vultisig.wallet.data.models.settings.AppCurrency.USD

        val germanFormat: NumberFormat = NumberFormat.getCurrencyInstance(Locale.GERMANY)

        val RUJI_ID = Coins.ThorChain.RUJI.id
        val TCY_ID = Coins.ThorChain.TCY.id

        val RUNE_COIN = Coins.ThorChain.RUNE.copy(address = RUNE_ADDRESS)
        val RUJI_COIN = Coins.ThorChain.RUJI.copy(address = RUJI_ADDRESS)

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
                // RUJI deliberately first: the ViewModel must match RUNE by ticker, not position.
                coins = listOf(RUJI_COIN, RUNE_COIN),
            )
    }
}
