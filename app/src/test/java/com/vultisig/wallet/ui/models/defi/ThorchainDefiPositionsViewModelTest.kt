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
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.ThorChainLpPosition
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.data.repositories.BalanceVisibilityRepository
import com.vultisig.wallet.data.repositories.DefiPositionsRepository
import com.vultisig.wallet.data.repositories.TokenPriceRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.GetThorChainLpPositionsUseCase
import com.vultisig.wallet.data.usecases.ThorchainBondUseCase
import com.vultisig.wallet.data.utils.decimals
import com.vultisig.wallet.data.utils.symbol
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.screens.v2.defi.model.DeFiNavActions
import com.vultisig.wallet.ui.utils.UiText
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
        coEvery { getThorChainLpPositionsUseCase(any(), any(), any(), any()) } returns emptyList()
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
            listOf(lpPosition(BTC_POOL, runeRedeem = "300000000", assetRedeem = "100000000"))

        val vm = createViewModel().also { it.setData(VAULT_ID) }

        // 3 RUNE at 2. The BTC side prices to zero — the vault holds no BTC coin, so it falls
        // through to the contract lookup, which this test leaves unstubbed.
        assertEquals(BigDecimal("6.00"), vm.totalValueLpFiat.value)
        assertEquals("$6.00", vm.state.value.lp.positions.single().totalPriceLp)
        assertEquals("$6.00", vm.state.value.totalAmountPrice)
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
        assertFalse(state.bonded.isLoading)
        assertFalse(state.isTotalAmountLoading)
        assertEquals("$0.00", state.bonded.totalBondedPrice)
        assertEquals("$0.00", state.totalAmountPrice)
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
        assertTrue(positions.isNotEmpty())
        positions.forEach { position ->
            assertFalse(position.isLoading)
            assertEquals("0 ${position.coin.ticker}", position.stakedAmountDisplay)
            assertEquals("$0.00", position.stakedFiatDisplay)
        }
    }

    @Test
    fun `a vault without RUNE settles the header total rather than shimmering forever`() = runTest {
        // Both legs bail out early here, and neither used to release the total-loading flag.
        selectPositions("RUNE", "RUJI")
        coEvery { vaultRepository.get(VAULT_ID) } returns VAULT.copy(coins = listOf(RUJI_COIN))

        val vm = createViewModel().also { it.setData(VAULT_ID) }

        val state = vm.state.value
        assertFalse(state.isTotalAmountLoading)
        assertEquals("$0.00", state.totalAmountPrice)
    }

    @Test
    fun `the zero balance is formatted in the users currency, not hardcoded dollars`() = runTest {
        coEvery { appCurrencyRepository.getCurrencyFormat() } returns
            NumberFormat.getCurrencyInstance(Locale.GERMANY)
        selectPositions("RUJI")
        coEvery { rujiStakingService.getStakingDetails(any(), any()) } returns
            flow { throw RuntimeException("thornode down") }

        val vm = createViewModel().also { it.setData(VAULT_ID) }

        val fiat = vm.state.value.staking.positions.first().stakedFiatDisplay
        assertNotNull(fiat)
        assertFalse(fiat.contains("$"), "expected a euro-formatted zero but was $fiat")
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
            defiPositionsRepository.saveSelectedPositions(VAULT_ID, listOf("TCY"))
        }
        val state = vm.state.value
        assertFalse(state.showPositionSelectionDialog)
        assertEquals(listOf("TCY"), state.selectedPositions)
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

    private fun selectPositions(vararg keys: String) {
        coEvery { defiPositionsRepository.getSelectedPositions(VAULT_ID) } returns
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
            ioDispatcher = testDispatcher,
        )

    private companion object {
        const val VAULT_ID = "vault-1"
        const val RUNE_ADDRESS = "thor1runeaddress"
        const val RUJI_ADDRESS = "thor1rujiaddress"
        const val NODE_ADDRESS = "thor1nodeaddress"
        const val BTC_POOL = "BTC.BTC"

        val AppCurrencyUsd = com.vultisig.wallet.data.models.settings.AppCurrency.USD

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
