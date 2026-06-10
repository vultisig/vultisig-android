@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.cosmosstaking

import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosChainApyData
import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosDelegation
import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosDelegatorReward
import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosDelegatorRewards
import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosStakePositionRow
import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosStakingAPYResolver
import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosStakingCoin
import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosStakingService
import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosUnbondingDelegation
import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosUnbondingEntry
import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosValidator
import com.vultisig.wallet.data.blockchain.cosmos.staking.KeybaseAvatarService
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.settings.AppCurrency
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.data.repositories.CosmosStakingSnapshotCache
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
import java.time.Instant
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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
internal class CosmosStakingPositionsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val vaultRepository: VaultRepository = mockk()
    private val cosmosStakingService: CosmosStakingService = mockk()
    private val apyResolver: CosmosStakingAPYResolver = mockk(relaxed = true)
    private val keybaseAvatarService: KeybaseAvatarService = mockk(relaxed = true)
    private val tokenPriceRepository: TokenPriceRepository = mockk(relaxed = true)
    private val appCurrencyRepository: AppCurrencyRepository = mockk()
    private val snapshotCache = CosmosStakingSnapshotCache()
    private val navigator: Navigator<Destination> = mockk(relaxed = true)

    private val activeVal = "terravaloper1active"
    private val churnedVal = "terravaloper1churned"

    private val coin =
        Coin(
            chain = Chain.Terra,
            ticker = "LUNA",
            logo = "",
            address = "terra1delegator",
            decimal = 6,
            hexPublicKey = "02".repeat(33),
            priceProviderID = "terra-luna",
            contractAddress = "",
            isNativeToken = true,
        )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { vaultRepository.get(any()) } returns
            Vault(
                id = "v1",
                name = "v",
                pubKeyECDSA = "pk",
                localPartyID = "lp",
                coins = listOf(coin),
            )
        coEvery { cosmosStakingService.fetchDelegations(any(), any()) } returns
            listOf(
                CosmosDelegation(activeVal, CosmosStakingCoin("uluna", "3000000"), "3000000"),
                CosmosDelegation(churnedVal, CosmosStakingCoin("uluna", "1000000"), "1000000"),
            )
        // Only the active validator is in the bonded list — the other is churned out.
        coEvery { cosmosStakingService.fetchValidators(any()) } returns
            listOf(
                CosmosValidator(
                    operatorAddress = activeVal,
                    moniker = "Active Val",
                    commission = BigDecimal("0.05"),
                    jailed = false,
                    status = CosmosValidator.Status.Bonded,
                    votingPower = BigDecimal("100"),
                    identity = "ABCDEF",
                )
            )
        coEvery { cosmosStakingService.fetchDelegatorRewards(any(), any()) } returns
            CosmosDelegatorRewards(
                rewards =
                    listOf(
                        CosmosDelegatorReward(
                            activeVal,
                            listOf(CosmosStakingCoin("uluna", "250000")),
                        )
                    ),
                total = emptyList(),
            )
        coEvery { cosmosStakingService.fetchUnbondingDelegations(any(), any()) } returns emptyList()
        coEvery { apyResolver.chainApy(any(), any()) } returns
            CosmosChainApyData(
                inflation = BigDecimal("0.07"),
                bondedRatio = BigDecimal("0.5"),
                communityTax = BigDecimal("0.02"),
            )
        coEvery { keybaseAvatarService.avatarUrl(any()) } returns "https://keybase.io/a.jpg"
        // LUNA spot price = $0.058 → fiat = stakedAmount × 0.058, formatted in USD.
        coEvery { appCurrencyRepository.currency } returns flowOf(AppCurrency.USD)
        coEvery { appCurrencyRepository.getCurrencyFormat() } returns
            NumberFormat.getCurrencyInstance(Locale.US)
        coEvery { tokenPriceRepository.refresh(any()) } returns Unit
        coEvery { tokenPriceRepository.getCachedPrice(any(), any()) } returns BigDecimal("0.058")
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun vm() =
        CosmosStakingPositionsViewModel(
                vaultRepository = vaultRepository,
                cosmosStakingService = cosmosStakingService,
                apyResolver = apyResolver,
                keybaseAvatarService = keybaseAvatarService,
                tokenPriceRepository = tokenPriceRepository,
                appCurrencyRepository = appCurrencyRepository,
                snapshotCache = snapshotCache,
                navigator = navigator,
                ioDispatcher = testDispatcher,
            )
            .also { it.setData(vaultId = "v1", chainId = "Terra") }

    @Test
    fun `hasClaimableRewards is true when a reward exceeds one base unit`() = runTest {
        // Fixture reward is 250000 uluna (0.25 LUNA) — well above one base unit, so claimable.
        assertEquals(true, vm().state.value.hasClaimableRewards)
    }

    @Test
    fun `hasClaimableRewards is false when rewards round down to zero base units`() = runTest {
        // 0.5 uluna accrued — a fractional cosmos.Dec below one whole base unit, so withdrawal
        // would yield 0; the Claim CTA must stay hidden rather than burn a fee on nothing.
        coEvery { cosmosStakingService.fetchDelegatorRewards(any(), any()) } returns
            CosmosDelegatorRewards(
                rewards =
                    listOf(
                        CosmosDelegatorReward(activeVal, listOf(CosmosStakingCoin("uluna", "0.5")))
                    ),
                total = emptyList(),
            )
        assertEquals(false, vm().state.value.hasClaimableRewards)
    }

    @Test
    fun `builds rows with correct status and total staked`() = runTest {
        val model = vm()
        val s = model.state.value
        assertEquals(2, s.positions.size)
        // total staked = 3 + 1 = 4 LUNA
        assertEquals(0, BigDecimal("4").compareTo(s.totalStaked))

        val active = s.positions.first { it.validatorAddress == activeVal }
        assertEquals(CosmosStakePositionRow.ValidatorStatus.Active, active.validatorStatus)
        val churned = s.positions.first { it.validatorAddress == churnedVal }
        assertEquals(CosmosStakePositionRow.ValidatorStatus.ChurnedOut, churned.validatorStatus)
    }

    @Test
    fun `active validator gets an APY, churned-out does not`() = runTest {
        val model = vm()
        val active = model.state.value.positions.first { it.validatorAddress == activeVal }
        val churned = model.state.value.positions.first { it.validatorAddress == churnedVal }
        assertNotNull(active.apyPercent)
        // A churned-out validator is absent from the bonded set (validator == null) and earns
        // nothing — the APY branch must be gated on validator metadata so it does NOT render the
        // full uncut chain rate (commission defaults to 0) under its own "Churned Out" badge.
        assertNull(churned.apyPercent)
    }

    @Test
    fun `keybase avatar resolves onto the matching row`() = runTest {
        val model = vm()
        val active = model.state.value.positions.first { it.validatorAddress == activeVal }
        assertEquals("https://keybase.io/a.jpg", active.validatorAvatarUrl)
    }

    @Test
    fun `unstake on a churned-out validator navigates to the undelegate route`() = runTest {
        val model = vm()
        val churned = model.state.value.positions.first { it.validatorAddress == churnedVal }
        model.unstake(churned)
        // Churned-out (jailed / unbonded) can no longer accept stake, but the user must still be
        // able to exit the position — Unstake is the only sensible action (iOS parity).
        coVerify { navigator.route(any<Route.CosmosStakingUndelegate>(), any()) }
    }

    @Test
    fun `move on a churned-out validator navigates to the redelegate route`() = runTest {
        val model = vm()
        val churned = model.state.value.positions.first { it.validatorAddress == churnedVal }
        model.move(churned)
        // cosmos-sdk MsgBeginRedelegate places no bonded-status requirement on the source, so
        // redelegating AWAY from a churned-out (jailed/slashed) validator must stay available — it
        // is the only instant escape, since undelegate forces the 21-day unbonding wait.
        coVerify { navigator.route(any<Route.CosmosStakingRedelegate>(), any()) }
    }

    @Test
    fun `unstake on an active validator navigates to the undelegate route`() = runTest {
        val model = vm()
        val active = model.state.value.positions.first { it.validatorAddress == activeVal }
        model.unstake(active)
        coVerify { navigator.route(any<Route.CosmosStakingUndelegate>(), any()) }
    }

    @Test
    fun `fiat values are resolved from spot price for banner total and rows`() = runTest {
        val model = vm()
        val s = model.state.value
        // iOS parity: the banner shows the staked total in fiat (not the liquid balance), so it
        // carries the same value as the Total Staked card. 4 LUNA × $0.058 = $0.232 → "$0.23".
        assertEquals("$0.23", s.totalStakedFiat)
        assertEquals(s.totalStakedFiat, s.totalAmountPrice)
        // Per-row = each validator's staked amount × price (3 LUNA → $0.17, 1 LUNA → $0.06).
        val active = s.positions.first { it.validatorAddress == activeVal }
        val churned = s.positions.first { it.validatorAddress == churnedVal }
        assertEquals("$0.17", active.stakedFiatDisplay)
        assertEquals("$0.06", churned.stakedFiatDisplay)
    }

    @Test
    fun `price cache miss degrades fiat to zero without erroring`() = runTest {
        coEvery { tokenPriceRepository.getCachedPrice(any(), any()) } returns null
        val model = vm()
        val s = model.state.value
        assertEquals(null, s.errorMessage)
        assertEquals(2, s.positions.size)
        assertEquals("$0.00", s.totalStakedFiat)
        assertEquals("$0.00", s.positions.first().stakedFiatDisplay)
    }

    @Test
    fun `delegations fetch failure surfaces an error instead of empty positions`() = runTest {
        // A transient LCD failure on the load-bearing delegations read must not be swallowed into a
        // false "no positions" empty state — it has to surface an error so the user sees a retry.
        coEvery { cosmosStakingService.fetchDelegations(any(), any()) } throws
            RuntimeException("LCD 503")
        val model = vm()
        val s = model.state.value
        assertNotNull(s.errorMessage)
        assertEquals(true, s.positions.isEmpty())
        assertEquals(false, s.isLoading)
    }

    @Test
    fun `rewards fetch failure degrades silently to zero pending reward`() = runTest {
        // Unlike delegations, a rewards-read failure degrades per-row: the position still renders
        // with zero pending reward rather than dropping the row or erroring the whole view.
        coEvery { cosmosStakingService.fetchDelegatorRewards(any(), any()) } throws
            RuntimeException("LCD 503")
        val model = vm()
        val s = model.state.value
        assertEquals(2, s.positions.size)
        val active = s.positions.first { it.validatorAddress == activeVal }
        assertEquals(0, BigDecimal.ZERO.compareTo(active.pendingReward))
    }

    @Test
    fun `unbonding validator disables actions`() = runTest {
        coEvery { cosmosStakingService.fetchUnbondingDelegations(any(), any()) } returns
            listOf(
                CosmosUnbondingDelegation(
                    validatorAddress = activeVal,
                    entries =
                        listOf(
                            CosmosUnbondingEntry(
                                creationHeight = 1,
                                completionTime = Instant.now().plusSeconds(86_400L),
                                initialBalance = BigDecimal("100"),
                                balance = BigDecimal("100"),
                            )
                        ),
                )
            )
        val model = vm()
        val active = model.state.value.positions.first { it.validatorAddress == activeVal }
        assertNotNull(active.pendingUnbondingUnlockDate)
        model.move(active)
        coVerify(exactly = 0) { navigator.route(any<Route.CosmosStakingRedelegate>(), any()) }
    }

    @Test
    fun `a successful load writes a snapshot into the cache`() = runTest {
        // First open with an empty cache must persist the computed result so the next open within
        // the session can render it instantly instead of flashing the empty state (issue #4764).
        vm()
        val snapshot = snapshotCache.read("Terra:terra1delegator")
        assertNotNull(snapshot)
        assertEquals(2, snapshot.positions.size)
        assertEquals(0, BigDecimal("4").compareTo(snapshot.totalStaked))
        assertEquals("$0.23", snapshot.totalStakedFiat)
    }

    @Test
    fun `a cached snapshot renders immediately without a loading flash`() = runTest {
        // Prime the cache, then make the live delegations read hang-fail. A second VM must show the
        // cached positions right away rather than the empty/zero state the issue reports.
        vm()
        coEvery { cosmosStakingService.fetchDelegations(any(), any()) } throws
            RuntimeException("LCD 503")

        val reopened = vm()
        val s = reopened.state.value
        assertEquals(2, s.positions.size)
        assertEquals(false, s.isLoading)
        // A background-refresh failure on a screen seeded from cache keeps the data visible — it
        // must NOT replace the readable list with an error banner.
        assertNull(s.errorMessage)
    }

    @Test
    fun `the refresh spinner flag is cleared once a load finishes`() = runTest {
        // Regression for the forever-spinning pull-to-refresh: the VM owns the flag and must reset
        // it when the run completes, even on a cache-seeded refresh that never toggles isLoading.
        val model = vm()
        assertEquals(false, model.isRefreshing.value)
    }

    @Test
    fun `a degraded validators read is not frozen into the cache`() = runTest {
        // A failed validators fetch folds every delegation to "Churned Out"; persisting that as the
        // known-good snapshot would reseed the alarming view on reopen. The write must be skipped.
        coEvery { cosmosStakingService.fetchValidators(any()) } throws RuntimeException("LCD 503")
        val model = vm()
        // The live view still renders (degraded) — only the cache is withheld.
        assertEquals(2, model.state.value.positions.size)
        assertNull(snapshotCache.read("Terra:terra1delegator"))
    }

    @Test
    fun `a missing price on a feed-bearing chain is not frozen into the cache`() = runTest {
        // A price miss on a chain that HAS a feed renders every fiat slot as $0.00; freezing that
        // snapshot would reseed a zeroed-out screen on reopen, so the write is skipped until a real
        // price is available.
        coEvery { tokenPriceRepository.getCachedPrice(any(), any()) } returns null
        vm()
        assertNull(snapshotCache.read("Terra:terra1delegator"))
    }

    @Test
    fun `a no-price-feed chain still writes the cache despite a null price`() = runTest {
        // QBTC has no price feed, so $0.00 fiat is correct rather than a failed fetch — the
        // snapshot
        // must still be cached so reopens render instantly instead of cold-loading every time.
        coEvery { vaultRepository.get(any()) } returns
            Vault(
                id = "v1",
                name = "v",
                pubKeyECDSA = "pk",
                localPartyID = "lp",
                coins = listOf(coin.copy(priceProviderID = "")),
            )
        coEvery { tokenPriceRepository.getCachedPrice(any(), any()) } returns null
        vm()
        assertNotNull(snapshotCache.read("Terra:terra1delegator"))
    }

    @Test
    fun `a refresh failure with no cache still surfaces the error`() = runTest {
        // The keep-cached-data guard must not swallow the very first failure: with nothing cached
        // there is nothing to fall back to, so the user has to see the error + retry.
        coEvery { cosmosStakingService.fetchDelegations(any(), any()) } throws
            RuntimeException("LCD 503")
        val s = vm().state.value
        assertNotNull(s.errorMessage)
        assertEquals(true, s.positions.isEmpty())
        assertNull(snapshotCache.read("Terra:terra1delegator"))
    }
}
