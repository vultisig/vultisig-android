@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.defi

import com.vultisig.wallet.data.api.MayaLpPoolStats
import com.vultisig.wallet.data.api.MayaMemberDetails
import com.vultisig.wallet.data.api.MayaMemberPool
import com.vultisig.wallet.data.api.MayaNodePool
import com.vultisig.wallet.data.blockchain.maya.MayaCacaoStakingDetails
import com.vultisig.wallet.data.blockchain.maya.MayaCacaoStakingService
import com.vultisig.wallet.data.blockchain.model.BondedNodePosition
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.monoToneLogo
import com.vultisig.wallet.data.models.settings.AppCurrency
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.data.repositories.BalanceVisibilityRepository
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.data.repositories.DefiPositionsRepository
import com.vultisig.wallet.data.repositories.MayachainBondRepository
import com.vultisig.wallet.data.repositories.TokenPriceRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.MayachainBondUseCase
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.screens.v2.defi.MAYA_BOND_CACAO_KEY
import com.vultisig.wallet.ui.screens.v2.defi.MAYA_STAKE_CACAO_KEY
import com.vultisig.wallet.ui.screens.v2.defi.model.BondNodeState
import com.vultisig.wallet.ui.screens.v2.defi.model.DeFiNavActions
import io.kotest.matchers.shouldBe
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
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * Characterization tests: they pin the behavior this ViewModel has today so the upcoming split into
 * a shared Thorchain/Maya base can be verified as behavior-preserving. They describe what the code
 * does, not necessarily what it ought to do.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class MayachainDefiPositionsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var navigator: Navigator<Destination>
    private lateinit var vaultRepository: VaultRepository
    private lateinit var bondUseCase: MayachainBondUseCase
    private lateinit var mayachainBondRepository: MayachainBondRepository
    private lateinit var chainAccountAddressRepository: ChainAccountAddressRepository
    private lateinit var mayaCacaoStakingService: MayaCacaoStakingService
    private lateinit var tokenPriceRepository: TokenPriceRepository
    private lateinit var appCurrencyRepository: AppCurrencyRepository
    private lateinit var balanceVisibilityRepository: BalanceVisibilityRepository
    private lateinit var defiPositionsRepository: DefiPositionsRepository
    // The real cache, not a mock: these tests assert the round trip a nav pop and a re-entry make.
    private lateinit var snapshotCache: DeFiPositionsSnapshotCache

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        navigator = mockk(relaxed = true)
        vaultRepository = mockk(relaxed = true)
        bondUseCase = mockk(relaxed = true)
        mayachainBondRepository = mockk(relaxed = true)
        chainAccountAddressRepository = mockk(relaxed = true)
        mayaCacaoStakingService = mockk(relaxed = true)
        tokenPriceRepository = mockk(relaxed = true)
        appCurrencyRepository = mockk(relaxed = true)
        balanceVisibilityRepository = mockk(relaxed = true)
        defiPositionsRepository = mockk(relaxed = true)
        snapshotCache = DeFiPositionsSnapshotCache()

        coEvery { vaultRepository.get(VAULT_ID) } returns VAULT
        coEvery { balanceVisibilityRepository.getVisibility(VAULT_ID) } returns true
        coEvery { appCurrencyRepository.currency } returns flowOf(AppCurrency.USD)
        coEvery { appCurrencyRepository.getCurrencyFormat() } returns
            NumberFormat.getCurrencyInstance(Locale.US)
        coEvery { tokenPriceRepository.getCachedPrice(any(), any()) } returns BigDecimal("2")
        coEvery { chainAccountAddressRepository.isValid(any(), any()) } returns true

        selectPositions(MAYA_BOND_CACAO_KEY, MAYA_STAKE_CACAO_KEY)
        coEvery { mayachainBondRepository.getMayaNodePools() } returns emptyList()
        coEvery { mayachainBondRepository.getMemberDetails(any()) } returns MayaMemberDetails()
        coEvery { mayachainBondRepository.getLpPoolStats() } returns emptyList()
        coEvery { bondUseCase.getActiveNodes(any(), any()) } returns flowOf(emptyList())
        coEvery { mayaCacaoStakingService.getStakingDetails(any()) } returns
            flowOf(MayaCacaoStakingDetails(BigInteger.ZERO, apr = null, canUnstake = false))
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `setData emits Success seeded with the default Maya bond and stake selection`() = runTest {
        val vm = createViewModel().also { it.setData(VAULT_ID) }

        val data = successData(vm)
        assertEquals(listOf(MAYA_BOND_CACAO_KEY, MAYA_STAKE_CACAO_KEY), data.selectedPositions)
        assertTrue(data.isBalanceVisible)
    }

    @Test
    fun `a vault that never chose on this chain gets the Maya defaults`() = runTest {
        coEvery { defiPositionsRepository.getSelectedPositions(Chain.MayaChain, VAULT_ID) } returns
            flowOf<Set<String>?>(null)

        val vm = createViewModel().also { it.setData(VAULT_ID) }

        assertEquals(
            listOf(MAYA_BOND_CACAO_KEY, MAYA_STAKE_CACAO_KEY),
            successData(vm).selectedPositions,
        )
    }

    @Test
    fun `a stored selection is used verbatim even when no key in it is Maya-specific`() = runTest {
        // The key carries the chain, so whatever is on it was written here. Reading a set of plain
        // tickers as "the Thorchain screen's" and falling back to the defaults for it is what used
        // to revive positions the user had turned off.
        selectPositions("RUNE", "TCY")

        val vm = createViewModel().also { it.setData(VAULT_ID) }

        successData(vm).selectedPositions shouldBe listOf("RUNE", "TCY")
    }

    @Test
    fun `a saved selection holding a Maya key is used verbatim`() = runTest {
        selectPositions(MAYA_BOND_CACAO_KEY)

        val vm = createViewModel().also { it.setData(VAULT_ID) }

        assertEquals(listOf(MAYA_BOND_CACAO_KEY), successData(vm).selectedPositions)
    }

    @Test
    fun `a dotted LP asset key is kept like any other stored key`() = runTest {
        selectPositions("BTC.BTC")

        val vm = createViewModel().also { it.setData(VAULT_ID) }

        assertEquals(listOf("BTC.BTC"), successData(vm).selectedPositions)
    }

    @Test
    fun `bonded nodes are mapped with truncated address and formatted amounts`() = runTest {
        coEvery { bondUseCase.getActiveNodes(VAULT_ID, CACAO_ADDRESS) } returns
            flowOf(listOf(bondedNode(amount = HUNDRED_CACAO)))

        val vm = createViewModel().also { it.setData(VAULT_ID) }

        val bonded = successData(vm).bonded
        assertFalse(bonded.isLoading)
        assertEquals("100.00000000 CACAO", bonded.totalBondedAmount)
        assertEquals("$200.00", bonded.totalBondedPrice)

        val node = bonded.nodes.single()
        assertEquals("maya1qwer...fgh", node.address)
        assertEquals(NODE_ADDRESS, node.fullAddress)
        assertEquals(BondNodeState.ACTIVE, node.status)
        assertEquals("15.25%", node.apy)
        assertEquals("100.00000000 CACAO", node.bondedAmount)
        assertEquals("1.2345 CACAO", node.nextAward)
        assertEquals("N/A", node.nextChurn)
    }

    @Test
    fun `a vault without a CACAO coin leaves the bonded tab empty and settled`() = runTest {
        coEvery { vaultRepository.get(VAULT_ID) } returns VAULT.copy(coins = emptyList())

        val vm = createViewModel().also { it.setData(VAULT_ID) }

        val bonded = successData(vm).bonded
        assertFalse(bonded.isLoading)
        assertTrue(bonded.nodes.isEmpty())
    }

    @Test
    fun `the CACAO staking position carries its maturity hints through to the UI`() = runTest {
        coEvery { mayaCacaoStakingService.getStakingDetails(CACAO_ADDRESS) } returns
            flowOf(
                MayaCacaoStakingDetails(
                    stakeAmount = FIFTY_CACAO,
                    apr = 0.0812,
                    canUnstake = false,
                    unstakeUnlocksInSeconds = 3_600L,
                    isUnstakeMaturityUnknown = true,
                )
            )

        val vm = createViewModel().also { it.setData(VAULT_ID) }

        val position = successData(vm).staking.positions.single()
        assertEquals("50.0000 CACAO", position.stakedAmountDisplay)
        assertEquals("$100.00", position.stakedFiatDisplay)
        assertEquals("8.12%", position.apy)
        assertFalse(position.canUnstake)
        assertEquals(3_600L, position.unstakeUnlocksInSeconds)
        assertTrue(position.isUnstakeMaturityUnknown)
    }

    @Test
    fun `deselecting the stake key clears the staking tab`() = runTest {
        selectPositions(MAYA_BOND_CACAO_KEY)
        coEvery { mayaCacaoStakingService.getStakingDetails(CACAO_ADDRESS) } returns
            flowOf(MayaCacaoStakingDetails(FIFTY_CACAO, apr = 0.05, canUnstake = true))

        val vm = createViewModel().also { it.setData(VAULT_ID) }

        assertTrue(successData(vm).staking.positions.isEmpty())
    }

    @Test
    fun `the header total sums the bonded and staked amounts`() = runTest {
        coEvery { bondUseCase.getActiveNodes(VAULT_ID, CACAO_ADDRESS) } returns
            flowOf(listOf(bondedNode(amount = HUNDRED_CACAO)))
        coEvery { mayaCacaoStakingService.getStakingDetails(CACAO_ADDRESS) } returns
            flowOf(MayaCacaoStakingDetails(FIFTY_CACAO, apr = null, canUnstake = false))

        val vm = createViewModel().also { it.setData(VAULT_ID) }

        // (100 bonded + 50 staked) CACAO at a price of 2.
        val data = successData(vm)
        assertEquals("$300.00", data.totalAmountPrice)
        assertFalse(data.isTotalAmountLoading)
    }

    @Test
    fun `an LP position is derived from the members share of the pool depths`() = runTest {
        selectPositions(MAYA_BOND_CACAO_KEY, MAYA_STAKE_CACAO_KEY, BTC_POOL)
        givenLpPool(liquidityUnits = "100", units = "1000")

        val vm = createViewModel().also { it.setData(VAULT_ID) }

        val lp = successData(vm).lp
        assertFalse(lp.isLoading)
        val position = lp.positions.single()
        assertEquals("CACAO/BTC Pool", position.titleLp)
        assertEquals("BTC", position.assetTicker)
        // 10% of a pool holding 1 BTC and 1 CACAO.
        assertEquals("0.1 CACAO + 0.1 BTC", position.position)
        assertEquals("$0.40", position.totalPriceLp)
        assertEquals("25.00%", position.apr)
        assertTrue(position.canRemove)
        assertEquals(Chain.Bitcoin.monoToneLogo, position.chainLogo)
    }

    @ParameterizedTest(name = "{0} prices its asset leg")
    @ValueSource(strings = ["THOR.RUNE", "ADA.ADA", "ZEC.ZEC"])
    fun `every Available Maya pool resolves to a chain`(pool: String) = runTest {
        // The prefix table is what turns a pool string into a chain, and a prefix it misses leaves
        // the asset leg at zero — the card and the header then report half the position's value.
        selectPositions(MAYA_BOND_CACAO_KEY, MAYA_STAKE_CACAO_KEY, pool)
        givenLpPool(liquidityUnits = "100", units = "1000", pool = pool)

        val position =
            successData(createViewModel().also { it.setData(VAULT_ID) }).lp.positions.single()

        // 10% of a pool holding 1 asset and 1 CACAO, both priced at $2.
        assertEquals("$0.40", position.totalPriceLp)
    }

    @Test
    fun `a pool reporting zero units falls back to the raw added amounts`() = runTest {
        selectPositions(MAYA_BOND_CACAO_KEY, MAYA_STAKE_CACAO_KEY, BTC_POOL)
        givenLpPool(
            liquidityUnits = "0",
            units = "0",
            assetAdded = "50000000",
            cacaoAdded = "20000000000",
        )

        val vm = createViewModel().also { it.setData(VAULT_ID) }

        val position = successData(vm).lp.positions.single()
        assertEquals("2 CACAO + 0.5 BTC", position.position)
        assertEquals("$5.00", position.totalPriceLp)
        // Nothing to withdraw without liquidity units.
        assertFalse(position.canRemove)
    }

    @Test
    fun `LP value counts toward the header total`() = runTest {
        // The total summed bond and stake only, so a holder whose value sat in LP saw a header
        // that disagreed with the cards underneath it.
        selectPositions(MAYA_BOND_CACAO_KEY, MAYA_STAKE_CACAO_KEY, BTC_POOL)
        givenLpPool(liquidityUnits = "100", units = "1000")

        val vm = createViewModel().also { it.setData(VAULT_ID) }

        // Nothing bonded or staked, so the header is the LP position's own $0.40.
        val data = successData(vm)
        data.lp.positions.single().totalPriceLp shouldBe "$0.40"
        data.totalAmountPrice shouldBe "$0.40"
        data.isTotalAmountLoading shouldBe false
    }

    @Test
    fun `a failed staking load leaves the CACAO card priced at zero rather than blank`() = runTest {
        // Clearing the spinner alone left the card with no fiat line at all, which the tab hid.
        coEvery { mayaCacaoStakingService.getStakingDetails(CACAO_ADDRESS) } returns
            flow { throw RuntimeException("maya node down") }

        val vm = createViewModel().also { it.setData(VAULT_ID) }

        val position = successData(vm).staking.positions.single()
        position.isLoading shouldBe false
        position.stakedFiatDisplay shouldBe "$0.00"
    }

    @Test
    fun `a failed bond load settles the header total instead of stranding it`() = runTest {
        coEvery { bondUseCase.getActiveNodes(VAULT_ID, CACAO_ADDRESS) } returns
            flow { throw RuntimeException("maya node down") }

        val vm = createViewModel().also { it.setData(VAULT_ID) }

        val data = successData(vm)
        data.bonded.isLoading shouldBe false
        data.isTotalAmountLoading shouldBe false
        data.bonded.totalBondedPrice shouldBe "$0.00"
    }

    @Test
    fun `a vault without CACAO prices the bond card at zero rather than unavailable`() = runTest {
        // Nothing bonded is a real zero, not a price we failed to resolve, so the card has to say
        // so. Clearing the spinner alone left it on the unavailable dash.
        coEvery { vaultRepository.get(VAULT_ID) } returns VAULT.copy(coins = listOf(BTC_COIN))

        val vm = createViewModel().also { it.setData(VAULT_ID) }

        val data = successData(vm)
        data.bonded.isLoading shouldBe false
        data.bonded.totalBondedPrice shouldBe "$0.00"
        data.isTotalAmountLoading shouldBe false
    }

    @Test
    fun `a bond load that throws before the flow starts still prices the card`() = runTest {
        // The outer catch wraps the vault lookup. It used to clear the spinner without filling the
        // price, which is the same blank-line bug as the in-flow failure below.
        coEvery { vaultRepository.get(VAULT_ID) } throws RuntimeException("db closed")

        val vm = createViewModel().also { it.setData(VAULT_ID) }

        val data = successData(vm)
        data.bonded.isLoading shouldBe false
        data.bonded.totalBondedPrice shouldBe "$0.00"
        data.isTotalAmountLoading shouldBe false
    }

    @Test
    fun `the header total waits for every leg instead of settling on the seeded zeros`() = runTest {
        // The legs are seeded flows; combining them unconditionally published $0.00 with the
        // spinner already off, before bond, stake or LP had loaded anything.
        selectPositions(MAYA_BOND_CACAO_KEY, MAYA_STAKE_CACAO_KEY, BTC_POOL)
        givenLpPool(liquidityUnits = "100", units = "1000")
        val heldBond = MutableStateFlow<List<BondedNodePosition>?>(null)
        coEvery { bondUseCase.getActiveNodes(VAULT_ID, CACAO_ADDRESS) } returns
            heldBond.filterNotNull()

        val vm = createViewModel().also { it.setData(VAULT_ID) }

        // Bond has not reported yet, so the total must still be pending.
        successData(vm).isTotalAmountLoading shouldBe true
        successData(vm).totalAmountPrice shouldBe null

        heldBond.value = listOf(bondedNode(HUNDRED_CACAO))

        val settled = successData(vm)
        settled.isTotalAmountLoading shouldBe false
        // 100 CACAO at 2, plus the LP position's 0.40.
        settled.totalAmountPrice shouldBe "$200.40"
    }

    @Test
    fun `a failed LP load leaves the placeholder priced at zero, not unavailable`() = runTest {
        // The placeholder used to snapshot a zero that was still being resolved on another
        // coroutine, then a failed load froze that null in as the terminal state.
        selectPositions(MAYA_BOND_CACAO_KEY, MAYA_STAKE_CACAO_KEY, BTC_POOL)
        givenLpPool(liquidityUnits = "100", units = "1000")
        coEvery { mayachainBondRepository.getLpPoolStats() } throws RuntimeException("midgard down")

        val vm = createViewModel().also { it.setData(VAULT_ID) }

        val data = successData(vm)
        data.lp.isLoading shouldBe false
        data.lp.positions.single().totalPriceLp shouldBe "$0.00"
        data.isTotalAmountLoading shouldBe false
    }

    @Test
    fun `switching currency re-prices LP instead of relabelling the old magnitude`() = runTest {
        // LP is stored already converted, so it cannot be re-based the way the raw legs are. The
        // total used to add that stale magnitude straight into a sum priced in the new currency.
        selectPositions(MAYA_BOND_CACAO_KEY, MAYA_STAKE_CACAO_KEY, BTC_POOL)
        givenLpPool(liquidityUnits = "100", units = "1000")
        val currency = MutableStateFlow(AppCurrency.USD)
        coEvery { appCurrencyRepository.currency } returns currency

        val vm = createViewModel().also { it.setData(VAULT_ID) }
        successData(vm).totalAmountPrice shouldBe "$0.40"

        // A different EUR price on purpose: with both currencies priced the same, a magnitude
        // carried across unchanged and one genuinely re-converted are the same number, and only
        // the formatter would be left to assert on — which relabelling passes just as well.
        coEvery { tokenPriceRepository.getCachedPrice(any(), AppCurrency.EUR) } returns
            BigDecimal("3")
        coEvery { appCurrencyRepository.getCurrencyFormat() } returns
            NumberFormat.getCurrencyInstance(Locale.GERMANY)
        currency.value = AppCurrency.EUR

        // Re-priced under the new currency rather than carrying the old number across: the same
        // 0.1 CACAO + 0.1 BTC at 3 apiece, not the 0.40 they were worth in USD.
        val settled = successData(vm)
        settled.isTotalAmountLoading shouldBe false
        settled.totalAmountPrice shouldBe germanFormat.format(BigDecimal("0.60"))
    }

    @Test
    fun `a superseded staking load leaves its leg to the load that replaced it`() = runTest {
        // A currency switch cancels the staking load and starts another, but onCompletion runs on
        // the way out of a cancelled collector too. Reporting zero there hands the replacement's
        // still-pending leg a value it never sent, settling the header on a total with the staked
        // amount missing from it.
        selectPositions(MAYA_STAKE_CACAO_KEY)
        val currency = MutableStateFlow(AppCurrency.USD)
        coEvery { appCurrencyRepository.currency } returns currency

        val replacementLoad = MutableStateFlow<MayaCacaoStakingDetails?>(null)
        var loads = 0
        coEvery { mayaCacaoStakingService.getStakingDetails(CACAO_ADDRESS) } coAnswers
            {
                if (loads++ == 0) {
                    flow { awaitCancellation() }
                } else {
                    flow { emit(replacementLoad.filterNotNull().first()) }
                }
            }

        val vm = createViewModel().also { it.setData(VAULT_ID) }

        coEvery { appCurrencyRepository.getCurrencyFormat() } returns
            NumberFormat.getCurrencyInstance(Locale.GERMANY)
        currency.value = AppCurrency.EUR

        successData(vm).isTotalAmountLoading shouldBe true
        successData(vm).totalAmountPrice shouldBe null

        replacementLoad.value = MayaCacaoStakingDetails(FIFTY_CACAO, apr = null, canUnstake = false)

        val settled = successData(vm)
        settled.isTotalAmountLoading shouldBe false
        settled.totalAmountPrice shouldBe germanFormat.format(BigDecimal("100.00"))
    }

    @Test
    fun `a bonded collector dropped for a newer refresh leaves its leg to that refresh`() =
        runTest {
            // Same shape on the bonded side, where flatMapLatest is what does the dropping.
            selectPositions(MAYA_BOND_CACAO_KEY)
            val currency = MutableStateFlow(AppCurrency.USD)
            coEvery { appCurrencyRepository.currency } returns currency

            var collections = 0
            coEvery { bondUseCase.getActiveNodes(VAULT_ID, CACAO_ADDRESS) } returns
                flow {
                    if (collections++ == 0) {
                        emit(listOf(bondedNode(amount = HUNDRED_CACAO)))
                    }
                    // getActiveNodes stays open on a live feed; being dropped is how it ends.
                    awaitCancellation()
                }

            val vm = createViewModel().also { it.setData(VAULT_ID) }
            successData(vm).totalAmountPrice shouldBe "$200.00"

            coEvery { appCurrencyRepository.getCurrencyFormat() } returns
                NumberFormat.getCurrencyInstance(Locale.GERMANY)
            currency.value = AppCurrency.EUR

            successData(vm).isTotalAmountLoading shouldBe true
            successData(vm).totalAmountPrice shouldBe null
        }

    @Test
    fun `switching currency parks the header on its spinner, not on the old total`() = runTest {
        // Dropping the LP leg stops the combine from emitting, so the header simply kept its
        // settled prior-currency figure — no spinner, no sign anything was in flight — for the
        // whole refetch.
        selectPositions(MAYA_BOND_CACAO_KEY, MAYA_STAKE_CACAO_KEY, BTC_POOL)
        givenLpPool(liquidityUnits = "100", units = "1000")
        val currency = MutableStateFlow(AppCurrency.USD)
        coEvery { appCurrencyRepository.currency } returns currency
        val loadedMemberDetails = memberDetails(liquidityUnits = "100")
        val heldMemberDetails = MutableStateFlow<MayaMemberDetails?>(loadedMemberDetails)
        coEvery { mayachainBondRepository.getMemberDetails(CACAO_ADDRESS) } coAnswers
            {
                heldMemberDetails.filterNotNull().first()
            }

        val vm = createViewModel().also { it.setData(VAULT_ID) }
        successData(vm).totalAmountPrice shouldBe "$0.40"

        // Hold the re-price so the switch can be observed mid-flight.
        heldMemberDetails.value = null
        coEvery { appCurrencyRepository.getCurrencyFormat() } returns
            NumberFormat.getCurrencyInstance(Locale.GERMANY)
        currency.value = AppCurrency.EUR

        successData(vm).totalAmountPrice shouldBe null
        successData(vm).isTotalAmountLoading shouldBe true

        heldMemberDetails.value = loadedMemberDetails

        val settled = successData(vm)
        settled.isTotalAmountLoading shouldBe false
        settled.totalAmountPrice shouldBe germanFormat.format(BigDecimal("0.40"))
    }

    @Test
    fun `switching currency re-prices the bonded and staking cards, not just the header`() =
        runTest {
            // Card fiat strings are formatted once per load from one-shot flows, so nothing
            // re-converts on its own. The header moved to the new currency while Bonded and CACAO
            // Staking kept showing the old one.
            selectPositions(MAYA_BOND_CACAO_KEY, MAYA_STAKE_CACAO_KEY)
            val currency = MutableStateFlow(AppCurrency.USD)
            coEvery { appCurrencyRepository.currency } returns currency
            coEvery { bondUseCase.getActiveNodes(VAULT_ID, CACAO_ADDRESS) } returns
                flowOf(listOf(bondedNode(HUNDRED_CACAO)))
            coEvery { mayaCacaoStakingService.getStakingDetails(any()) } returns
                flowOf(MayaCacaoStakingDetails(HUNDRED_CACAO, apr = null, canUnstake = false))

            val vm = createViewModel().also { it.setData(VAULT_ID) }
            successData(vm).bonded.totalBondedPrice shouldBe "$200.00"

            // A different EUR price, so re-converting and merely re-formatting produce different
            // numbers.
            coEvery { tokenPriceRepository.getCachedPrice(any(), AppCurrency.EUR) } returns
                BigDecimal("3")
            coEvery { appCurrencyRepository.getCurrencyFormat() } returns
                NumberFormat.getCurrencyInstance(Locale.GERMANY)
            currency.value = AppCurrency.EUR

            val expected = germanFormat.format(BigDecimal("300.00"))
            val settled = successData(vm)
            settled.bonded.totalBondedPrice shouldBe expected
            settled.staking.positions.single().stakedFiatDisplay shouldBe expected
        }

    @Test
    fun `a chain reporting no LP pools still settles the header`() = runTest {
        // reloadLpTab used to wait on a non-empty dialog list, which cannot tell "not fetched yet"
        // from "fetched, no pools". With an LP key persisted, the leg never reported and the
        // header spun forever — pull-to-refresh included.
        selectPositions(MAYA_BOND_CACAO_KEY, MAYA_STAKE_CACAO_KEY, BTC_POOL)
        coEvery { mayachainBondRepository.getMayaNodePools() } returns emptyList()

        val vm = createViewModel().also { it.setData(VAULT_ID) }

        val data = successData(vm)
        data.isTotalAmountLoading shouldBe false
        data.totalAmountPrice shouldBe "$0.00"
    }

    @Test
    fun `member details that fail to load make the header unavailable rather than zero`() =
        runTest {
            // The failure fell back to an empty MayaMemberDetails, which reads as "holds no
            // liquidity". Every selected pool then folded into the header total as zero, giving a
            // confident figure that understated what the vault holds.
            selectPositions(MAYA_BOND_CACAO_KEY, MAYA_STAKE_CACAO_KEY, BTC_POOL)
            givenLpPool(liquidityUnits = "100", units = "1000")
            coEvery { mayachainBondRepository.getMemberDetails(CACAO_ADDRESS) } throws
                RuntimeException("midgard down")

            val vm = createViewModel().also { it.setData(VAULT_ID) }

            val data = successData(vm)
            data.isTotalAmountLoading shouldBe false
            data.totalAmountPrice shouldBe null
            data.lp.positions.single().totalPriceLp shouldBe null
        }

    @Test
    fun `the LP tab stays empty while only the static Maya keys are selected`() = runTest {
        givenLpPool(liquidityUnits = "100", units = "1000")

        val vm = createViewModel().also { it.setData(VAULT_ID) }

        val lp = successData(vm).lp
        assertFalse(lp.isLoading)
        assertTrue(lp.positions.isEmpty())
    }

    @Test
    fun `bond and unbond route to the deposit flow carrying the node address`() = runTest {
        val vm = createViewModel().also { it.setData(VAULT_ID) }

        vm.onClickBond(NODE_ADDRESS)
        vm.onClickUnBond(NODE_ADDRESS)

        coVerify(exactly = 1) {
            navigator.route(
                Route.Deposit(
                    vaultId = VAULT_ID,
                    chainId = Chain.MayaChain.id,
                    depositType = DeFiNavActions.BOND.type,
                    bondAddress = NODE_ADDRESS,
                )
            )
        }
        coVerify(exactly = 1) {
            navigator.route(
                Route.Deposit(
                    vaultId = VAULT_ID,
                    chainId = Chain.MayaChain.id,
                    depositType = DeFiNavActions.UNBOND.type,
                    bondAddress = NODE_ADDRESS,
                )
            )
        }
    }

    @Test
    fun `stake and LP actions route with their pool context`() = runTest {
        val vm = createViewModel().also { it.setData(VAULT_ID) }

        vm.onNavigateToStake(DeFiNavActions.STAKE_CACAO)
        vm.onNavigateToLp(BTC_POOL, DeFiNavActions.ADD_LP)
        vm.bondToNode()

        coVerify(exactly = 1) {
            navigator.route(
                Route.Deposit(
                    vaultId = VAULT_ID,
                    chainId = Chain.MayaChain.id,
                    depositType = DeFiNavActions.STAKE_CACAO.type,
                )
            )
        }
        coVerify(exactly = 1) {
            navigator.route(
                Route.Deposit(
                    vaultId = VAULT_ID,
                    chainId = Chain.MayaChain.id,
                    depositType = DeFiNavActions.ADD_LP.type,
                    poolId = BTC_POOL,
                )
            )
        }
        coVerify(exactly = 1) {
            navigator.route(Route.BondForm(vaultId = VAULT_ID, chainId = Chain.MayaChain.id))
        }
    }

    @Test
    fun `opening the selection dialog rebases the draft on the committed selection`() = runTest {
        val vm = createViewModel().also { it.setData(VAULT_ID) }

        vm.onPositionSelectionChange(BTC_POOL, selected = true)
        // Reopening must discard the abandoned draft.
        vm.setPositionSelectionDialogVisibility(true)

        val data = successData(vm)
        assertTrue(data.showPositionSelectionDialog)
        assertEquals(data.selectedPositions, data.tempSelectedPositions)
    }

    @Test
    fun `confirming the dialog persists the draft and closes it`() = runTest {
        val vm = createViewModel().also { it.setData(VAULT_ID) }

        vm.setPositionSelectionDialogVisibility(true)
        vm.onPositionSelectionChange(BTC_POOL, selected = true)
        vm.onPositionSelectionChange(MAYA_BOND_CACAO_KEY, selected = false)
        vm.onPositionSelectionDone()

        val expected = listOf(MAYA_STAKE_CACAO_KEY, BTC_POOL)
        coVerify(exactly = 1) {
            defiPositionsRepository.saveSelectedPositions(Chain.MayaChain, VAULT_ID, expected)
        }
        val data = successData(vm)
        assertFalse(data.showPositionSelectionDialog)
        assertEquals(expected, data.selectedPositions)
    }

    @Test
    fun `adding a pool parks the header on its spinner, not on the pre-add total`() = runTest {
        // Confirming the dialog writes the selection, and the saved-positions collector reloads
        // every leg for it — but left the legs holding their pre-selection values, so the header
        // read as a settled total, short by the pool just added, for the whole refetch.
        val saved = MutableStateFlow(setOf(MAYA_BOND_CACAO_KEY, MAYA_STAKE_CACAO_KEY))
        coEvery { defiPositionsRepository.getSelectedPositions(Chain.MayaChain, VAULT_ID) } returns
            saved
        coEvery {
            defiPositionsRepository.saveSelectedPositions(Chain.MayaChain, VAULT_ID, any())
        } answers { saved.value = thirdArg<List<String>>().toSet() }
        givenLpPool(liquidityUnits = "100", units = "1000")
        val loadedMemberDetails = memberDetails(liquidityUnits = "100")
        val heldMemberDetails = MutableStateFlow<MayaMemberDetails?>(loadedMemberDetails)
        coEvery { mayachainBondRepository.getMemberDetails(CACAO_ADDRESS) } coAnswers
            {
                heldMemberDetails.filterNotNull().first()
            }

        val vm = createViewModel().also { it.setData(VAULT_ID) }
        successData(vm).totalAmountPrice shouldBe "$0.00"

        // Hold the new pool's fetch so the add can be observed mid-flight.
        heldMemberDetails.value = null
        vm.setPositionSelectionDialogVisibility(true)
        vm.onPositionSelectionChange(BTC_POOL, selected = true)
        vm.onPositionSelectionDone()

        successData(vm).totalAmountPrice shouldBe null
        successData(vm).isTotalAmountLoading shouldBe true

        heldMemberDetails.value = loadedMemberDetails

        val settled = successData(vm)
        settled.isTotalAmountLoading shouldBe false
        settled.totalAmountPrice shouldBe "$0.40"
    }

    @Test
    fun `confirming a selection nothing changed in writes nothing`() = runTest {
        // Done is reachable without touching a checkbox, and the write would come back through the
        // collector as a reload of all three legs.
        val saved = MutableStateFlow(setOf(MAYA_BOND_CACAO_KEY, MAYA_STAKE_CACAO_KEY))
        coEvery { defiPositionsRepository.getSelectedPositions(Chain.MayaChain, VAULT_ID) } returns
            saved
        val vm = createViewModel().also { it.setData(VAULT_ID) }
        val settled = successData(vm)
        settled.isTotalAmountLoading shouldBe false

        vm.setPositionSelectionDialogVisibility(true)
        vm.onPositionSelectionDone()

        coVerify(exactly = 0) {
            defiPositionsRepository.saveSelectedPositions(Chain.MayaChain, VAULT_ID, any())
        }
        val data = successData(vm)
        assertFalse(data.showPositionSelectionDialog)
        data.isTotalAmountLoading shouldBe false
        data.totalAmountPrice shouldBe settled.totalAmountPrice
    }

    @Test
    fun `a store emission that leaves the selection alone keeps the header settled`() = runTest {
        // A vault's first-ever save flips the key from absent to present, which the store cannot
        // read as unchanged even though both sides map to the same Maya defaults — and the stored
        // set need not come back in the order the default list has.
        val saved = MutableStateFlow<Set<String>?>(null)
        coEvery { defiPositionsRepository.getSelectedPositions(Chain.MayaChain, VAULT_ID) } returns
            saved
        val heldStaking =
            MutableStateFlow<MayaCacaoStakingDetails?>(
                MayaCacaoStakingDetails(BigInteger.ZERO, apr = null, canUnstake = false)
            )
        coEvery { mayaCacaoStakingService.getStakingDetails(any()) } returns
            heldStaking.filterNotNull()

        val vm = createViewModel().also { it.setData(VAULT_ID) }
        val settled = successData(vm)
        settled.isTotalAmountLoading shouldBe false

        // Hold the staking leg: a reload would leave the header on its spinner here.
        heldStaking.value = null
        saved.value = setOf(MAYA_STAKE_CACAO_KEY, MAYA_BOND_CACAO_KEY)

        val data = successData(vm)
        data.isTotalAmountLoading shouldBe false
        data.totalAmountPrice shouldBe settled.totalAmountPrice
        assertEquals(settled.selectedPositions, data.selectedPositions)
    }

    @Test
    fun `clearing every position settles the header instead of freezing it`() = runTest {
        // An empty set is a selection this screen cleared, so deriving it to the Maya defaults
        // produced the same set the collector last saw and the dedup dropped the emission — the
        // tabs cleared while the header kept the total of the positions just removed.
        val saved = MutableStateFlow(setOf(MAYA_BOND_CACAO_KEY, MAYA_STAKE_CACAO_KEY))
        coEvery { defiPositionsRepository.getSelectedPositions(Chain.MayaChain, VAULT_ID) } returns
            saved
        coEvery {
            defiPositionsRepository.saveSelectedPositions(Chain.MayaChain, VAULT_ID, any())
        } answers { saved.value = thirdArg<List<String>>().toSet() }
        coEvery { bondUseCase.getActiveNodes(VAULT_ID, CACAO_ADDRESS) } returns
            flowOf(listOf(bondedNode(amount = HUNDRED_CACAO)))

        val vm = createViewModel().also { it.setData(VAULT_ID) }
        successData(vm).totalAmountPrice shouldBe "$200.00"

        vm.setPositionSelectionDialogVisibility(true)
        vm.onPositionSelectionChange(MAYA_BOND_CACAO_KEY, selected = false)
        vm.onPositionSelectionChange(MAYA_STAKE_CACAO_KEY, selected = false)
        vm.onPositionSelectionDone()

        val data = successData(vm)
        assertTrue(data.selectedPositions.isEmpty())
        data.isTotalAmountLoading shouldBe false
        data.totalAmountPrice shouldBe "$0.00"
        assertTrue(data.bonded.nodes.isEmpty())
        assertTrue(data.staking.positions.isEmpty())
    }

    @Test
    fun `an emptied stored selection is honoured, not re-derived to the defaults`() = runTest {
        // The store returns null for a vault that never chose, so an empty set can only be one this
        // screen cleared — the defaults must not claim it back.
        val saved = MutableStateFlow(setOf(MAYA_BOND_CACAO_KEY, MAYA_STAKE_CACAO_KEY))
        coEvery { defiPositionsRepository.getSelectedPositions(Chain.MayaChain, VAULT_ID) } returns
            saved
        coEvery { bondUseCase.getActiveNodes(VAULT_ID, CACAO_ADDRESS) } returns
            flowOf(listOf(bondedNode(amount = HUNDRED_CACAO)))

        val vm = createViewModel().also { it.setData(VAULT_ID) }
        successData(vm).totalAmountPrice shouldBe "$200.00"

        saved.value = emptySet()

        val data = successData(vm)
        assertTrue(data.selectedPositions.isEmpty())
        data.isTotalAmountLoading shouldBe false
        data.totalAmountPrice shouldBe "$0.00"
    }

    @Test
    fun `deselecting bond drops its card and its leg from the total`() = runTest {
        // Bond is a checkbox like the others; the loader used to ignore it, so the card stayed and
        // the header went on counting a position the user had removed.
        selectPositions(MAYA_STAKE_CACAO_KEY)
        coEvery { bondUseCase.getActiveNodes(VAULT_ID, CACAO_ADDRESS) } returns
            flowOf(listOf(bondedNode(amount = HUNDRED_CACAO)))

        val vm = createViewModel().also { it.setData(VAULT_ID) }

        val data = successData(vm)
        assertFalse(data.bonded.isLoading)
        assertTrue(data.bonded.nodes.isEmpty())
        data.isTotalAmountLoading shouldBe false
        data.totalAmountPrice shouldBe "$0.00"
    }

    @Test
    fun `a Thorchain save for the same vault leaves this screen's selection alone`() = runTest {
        // Both screens used to write one key per vault, so a Thorchain save landed on the selection
        // this screen reads. Unchecking everything there persisted an empty set, which this screen
        // read as its own clearing and zeroed Bond, Staking and LP for; any plain-ticker save there
        // held nothing this screen could show, so it fell back to the Maya defaults and revived a
        // position the user had turned off. Neither reaches this screen now the key carries the
        // chain.
        val mayaSaved = MutableStateFlow<Set<String>?>(setOf(MAYA_STAKE_CACAO_KEY))
        coEvery { defiPositionsRepository.getSelectedPositions(Chain.MayaChain, VAULT_ID) } returns
            mayaSaved
        val thorchainSaved = MutableStateFlow<Set<String>?>(setOf("RUNE", "TCY"))
        coEvery { defiPositionsRepository.getSelectedPositions(Chain.ThorChain, VAULT_ID) } returns
            thorchainSaved

        val vm = createViewModel().also { it.setData(VAULT_ID) }
        val settled = successData(vm)
        settled.isTotalAmountLoading shouldBe false

        // Every Thorchain position unchecked, then its selection edited again.
        thorchainSaved.value = emptySet()
        thorchainSaved.value = setOf("RUNE")

        val data = successData(vm)
        data.selectedPositions shouldBe listOf(MAYA_STAKE_CACAO_KEY)
        data.isTotalAmountLoading shouldBe false
        data.totalAmountPrice shouldBe settled.totalAmountPrice
    }

    @Test
    fun `a refresh leaves the settled bonded total up instead of blanking it`() = runTest {
        coEvery { bondUseCase.getActiveNodes(VAULT_ID, CACAO_ADDRESS) } returns
            flowOf(listOf(bondedNode(amount = HUNDRED_CACAO)))
        val vm = createViewModel().also { it.setData(VAULT_ID) }
        assertEquals("$200.00", successData(vm).bonded.totalBondedPrice)

        // The refresh never answers, so what is on screen is what the previous load left.
        coEvery { bondUseCase.getActiveNodes(VAULT_ID, CACAO_ADDRESS) } returns
            flow { awaitCancellation() }
        vm.setData(VAULT_ID)

        val bonded = successData(vm).bonded
        assertFalse(bonded.isLoading, "a priced total must not go back to its shimmer on a refresh")
        assertEquals("100.00000000 CACAO", bonded.totalBondedAmount)
        assertEquals("$200.00", bonded.totalBondedPrice)
    }

    @Test
    fun `a refresh leaves the settled staking card up instead of replacing it`() = runTest {
        coEvery { mayaCacaoStakingService.getStakingDetails(CACAO_ADDRESS) } returns
            flowOf(
                MayaCacaoStakingDetails(stakeAmount = FIFTY_CACAO, apr = null, canUnstake = true)
            )
        val vm = createViewModel().also { it.setData(VAULT_ID) }
        val settled = successData(vm).staking.positions.single()
        assertFalse(settled.isLoading)

        // The refresh never answers, so what is on screen is what the previous load left.
        coEvery { mayaCacaoStakingService.getStakingDetails(CACAO_ADDRESS) } returns
            flow { awaitCancellation() }
        vm.setData(VAULT_ID)

        val position = successData(vm).staking.positions.single()
        assertFalse(position.isLoading, "a settled card must not be swapped for a placeholder")
        assertEquals(settled.stakedAmountDisplay, position.stakedAmountDisplay)
        assertEquals(settled.stakedFiatDisplay, position.stakedFiatDisplay)
    }

    @Test
    fun `a re-entry paints the state the screen was last showing`() = runTest {
        // Popping back to the DeFi list destroys this view-model, so the next open used to
        // cold-start: zeroed cards, header on a spinner, and the enabled set flashing back to the
        // CACAO defaults until the store answered.
        snapshotCache.write(VAULT_ID, LAST_RENDERED)
        neverAnswersSelection()

        val data = successData(createViewModel().also { it.setData(VAULT_ID) })

        assertEquals("$12.34", data.totalAmountPrice)
        assertFalse(data.isTotalAmountLoading)
        assertEquals("5 CACAO", data.bonded.totalBondedAmount)
        // An empty selection is a choice the user made, and it survives the re-entry rather than
        // being replaced by the defaults.
        assertEquals(emptyList(), data.selectedPositions)
        assertEquals(data.selectedPositions, data.tempSelectedPositions)
    }

    @Test
    fun `a re-entry does not reopen the position picker`() = runTest {
        snapshotCache.write(VAULT_ID, LAST_RENDERED.copy(showPositionSelectionDialog = true))
        neverAnswersSelection()

        val data = successData(createViewModel().also { it.setData(VAULT_ID) })

        assertFalse(data.showPositionSelectionDialog)
    }

    @Test
    fun `hands the rendered state to the cache when the screen is popped`() = runTest {
        val vm = createViewModel().also { it.setData(VAULT_ID) }
        val rendered = successData(vm)

        vm.clearForTest()

        assertEquals(rendered, snapshotCache.read(VAULT_ID, MayachainDefiPositionsUiModel::class))
    }

    /**
     * Leaves the saved-selection read suspended, so nothing the loads do can overwrite a restored
     * snapshot while the assertion runs.
     */
    private fun neverAnswersSelection() {
        coEvery { defiPositionsRepository.getSelectedPositions(Chain.MayaChain, VAULT_ID) } returns
            flow { awaitCancellation() }
    }

    private fun selectPositions(vararg keys: String) {
        coEvery { defiPositionsRepository.getSelectedPositions(Chain.MayaChain, VAULT_ID) } returns
            flowOf(keys.toSet())
    }

    private fun memberDetails(liquidityUnits: String) =
        MayaMemberDetails(
            pools =
                listOf(
                    MayaMemberPool(
                        pool = BTC_POOL,
                        assetAdded = "0",
                        cacaoAdded = "0",
                        liquidityUnits = liquidityUnits,
                    )
                )
        )

    private fun givenLpPool(
        liquidityUnits: String,
        units: String,
        assetAdded: String = "0",
        cacaoAdded: String = "0",
        pool: String = BTC_POOL,
    ) {
        coEvery { mayachainBondRepository.getMayaNodePools() } returns
            listOf(MayaNodePool(asset = pool, status = "Available"))
        coEvery { mayachainBondRepository.getMemberDetails(CACAO_ADDRESS) } returns
            MayaMemberDetails(
                pools =
                    listOf(
                        MayaMemberPool(
                            pool = pool,
                            assetAdded = assetAdded,
                            cacaoAdded = cacaoAdded,
                            liquidityUnits = liquidityUnits,
                        )
                    )
            )
        coEvery { mayachainBondRepository.getLpPoolStats() } returns
            listOf(
                MayaLpPoolStats(
                    asset = pool,
                    annualPercentageRate = "0.25",
                    status = "Available",
                    assetDepth = "100000000",
                    cacaoDepth = "10000000000",
                    units = units,
                )
            )
    }

    private fun bondedNode(amount: BigInteger) =
        BondedNodePosition(
            id = "cacao-node",
            node = BondedNodePosition.BondedNode(address = NODE_ADDRESS, state = "active"),
            amount = amount,
            coin = CACAO_COIN,
            apy = 0.1525,
            nextReward = 12_345_678_901.0,
            nextChurn = null,
        )

    private fun successData(vm: MayachainDefiPositionsViewModel): MayachainDefiPositionsUiModel {
        val state = vm.state.value
        assertTrue(state is MayachainDefiUiState.Success, "expected Success, was $state")
        return (state as MayachainDefiUiState.Success).data
    }

    private fun createViewModel(): MayachainDefiPositionsViewModel =
        MayachainDefiPositionsViewModel(
            navigator = navigator,
            vaultRepository = vaultRepository,
            bondUseCase = bondUseCase,
            mayachainBondRepository = mayachainBondRepository,
            chainAccountAddressRepository = chainAccountAddressRepository,
            mayaCacaoStakingService = mayaCacaoStakingService,
            appCurrencyRepository = appCurrencyRepository,
            balanceVisibilityRepository = balanceVisibilityRepository,
            defiPositionsRepository = defiPositionsRepository,
            // Real calculator over the mocked price repository: the fiat assertions below stay
            // end-to-end rather than asserting against a stubbed conversion.
            fiatValueCalculator = DefiFiatValueCalculator(tokenPriceRepository),
            snapshotCache = snapshotCache,
            ioDispatcher = testDispatcher,
        )

    private companion object {
        /** A settled screen, as the cache would have it after the user walked away from one. */
        val LAST_RENDERED =
            MayachainDefiPositionsUiModel(
                totalAmountPrice = "$12.34",
                isTotalAmountLoading = false,
                bonded =
                    BondedTabUiModel(totalBondedAmount = "5 CACAO", totalBondedPrice = "$10.00"),
                selectedPositions = emptyList(),
                tempSelectedPositions = emptyList(),
            )

        const val VAULT_ID = "vault-1"
        const val CACAO_ADDRESS = "maya1cacaoaddress"
        const val NODE_ADDRESS = "maya1qwertyuiopasdfgh"
        const val BTC_POOL = "BTC.BTC"

        val germanFormat: NumberFormat = NumberFormat.getCurrencyInstance(Locale.GERMANY)

        val HUNDRED_CACAO: BigInteger = BigInteger("1000000000000")
        val FIFTY_CACAO: BigInteger = BigInteger("500000000000")

        val CACAO_COIN = Coins.MayaChain.CACAO.copy(address = CACAO_ADDRESS)
        val BTC_COIN = Coins.Bitcoin.BTC.copy(address = "bc1qbtcaddress")

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
                coins = listOf(CACAO_COIN, BTC_COIN),
            )
    }
}
