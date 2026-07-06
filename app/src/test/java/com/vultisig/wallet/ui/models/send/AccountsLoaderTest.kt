@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.send

import com.vultisig.wallet.data.blockchain.model.StakingDetails
import com.vultisig.wallet.data.blockchain.thorchain.RujiStakingService.Companion.RUJI_REWARDS_COIN
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.data.repositories.StakingDetailsRepository
import com.vultisig.wallet.ui.screens.v2.defi.model.DeFiNavActions
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal
import java.math.BigInteger
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
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

internal class AccountsLoaderTest {

    private val scheduler = TestCoroutineScheduler()
    private val mainDispatcher = UnconfinedTestDispatcher(scheduler)

    private val accountsState = MutableStateFlow<AccountsLoadState>(AccountsLoadState.Uninitialized)
    private val accountsRepository: AccountsRepository = mockk(relaxed = true)
    private val stakingDetailsRepository: StakingDetailsRepository = mockk(relaxed = true)

    private var defiType: DeFiNavActions? = null
    private var mscaAddress: String? = null
    private var bondedAmount: BigInteger? = null

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        accountsState.value = AccountsLoadState.Uninitialized
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val loadedAccounts: List<Account>
        get() {
            val state = accountsState.value
            assertIs<AccountsLoadState.Loaded>(state)
            return state.accounts
        }

    // ──────── default + DeFi flow paths ────────

    @Test
    fun `load with null defi type collects loadAddresses (regular send path)`() =
        runTest(mainDispatcher) {
            defiType = null
            val ethAccount = ethAccount()
            every { accountsRepository.loadAddresses(VAULT_ID) } returns
                flowOf(
                    listOf(
                        Address(
                            chain = Chain.Ethereum,
                            address = "0x1",
                            accounts = listOf(ethAccount),
                        )
                    )
                )
            val loader = build(backgroundScope)

            loader.load(VAULT_ID)
            advanceUntilIdle()

            assertEquals(listOf(ethAccount), loadedAccounts)
            coVerify(exactly = 0) { accountsRepository.loadDeFiAddresses(any(), any()) }
        }

    @Test
    fun `load with UNSTAKE_TCY collects loadDeFiAddresses (defi-balance path)`() =
        runTest(mainDispatcher) {
            defiType = DeFiNavActions.UNSTAKE_TCY
            val tcyAccount = thorAccount(Coins.ThorChain.RUNE)
            coEvery { accountsRepository.loadDeFiAddresses(VAULT_ID, false) } returns
                flowOf(
                    listOf(
                        Address(
                            chain = Chain.ThorChain,
                            address = "thor1",
                            accounts = listOf(tcyAccount),
                        )
                    )
                )
            val loader = build(backgroundScope)

            loader.load(VAULT_ID)
            advanceUntilIdle()

            assertEquals(listOf(tcyAccount), loadedAccounts)
            coVerify(exactly = 0) { accountsRepository.loadAddresses(any()) }
        }

    @Test
    fun `load with BOND uses the regular loadAddresses flow`() =
        runTest(mainDispatcher) {
            defiType = DeFiNavActions.BOND
            val runeAccount = thorAccount(Coins.ThorChain.RUNE)
            every { accountsRepository.loadAddresses(VAULT_ID) } returns
                flowOf(
                    listOf(
                        Address(
                            chain = Chain.ThorChain,
                            address = "thor1",
                            accounts = listOf(runeAccount),
                        )
                    )
                )
            val loader = build(backgroundScope)

            loader.load(VAULT_ID)
            advanceUntilIdle()

            assertEquals(listOf(runeAccount), loadedAccounts)
        }

    // ──────── UNBOND ────────

    @Test
    fun `UNBOND overrides the RUNE balance with the node's bonded amount`() =
        runTest(mainDispatcher) {
            defiType = DeFiNavActions.UNBOND
            bondedAmount = BigInteger("5000000")
            // loadDeFiAddresses returns the wallet's spendable RUNE (1_000_000) — the bug source.
            val runeAccount = thorAccount(Coins.ThorChain.RUNE)
            coEvery { accountsRepository.loadDeFiAddresses(VAULT_ID, false) } returns
                flowOf(
                    listOf(
                        Address(
                            chain = Chain.ThorChain,
                            address = "thor1",
                            accounts = listOf(runeAccount),
                        )
                    )
                )
            val loader = build(backgroundScope)

            loader.load(VAULT_ID)
            advanceUntilIdle()

            assertEquals(BigInteger("5000000"), loadedAccounts.single().tokenValue?.value)
            coVerify(exactly = 0) { accountsRepository.loadAddresses(any()) }
        }

    @Test
    fun `UNBOND without a bonded amount falls back to the wallet balance`() =
        runTest(mainDispatcher) {
            defiType = DeFiNavActions.UNBOND
            bondedAmount = null
            val runeAccount = thorAccount(Coins.ThorChain.RUNE)
            coEvery { accountsRepository.loadDeFiAddresses(VAULT_ID, false) } returns
                flowOf(
                    listOf(
                        Address(
                            chain = Chain.ThorChain,
                            address = "thor1",
                            accounts = listOf(runeAccount),
                        )
                    )
                )
            val loader = build(backgroundScope)

            loader.load(VAULT_ID)
            advanceUntilIdle()

            assertEquals(listOf(runeAccount), loadedAccounts)
        }

    @Test
    fun `UNBOND recomputes RUNE fiat from price and leaves other accounts untouched`() =
        runTest(mainDispatcher) {
            defiType = DeFiNavActions.UNBOND
            bondedAmount = BigInteger("200000000") // 2 RUNE (8 decimals)
            val runeAccount =
                Account(
                    token = Coins.ThorChain.RUNE,
                    tokenValue =
                        TokenValue(value = BigInteger("100000000"), token = Coins.ThorChain.RUNE),
                    fiatValue = FiatValue(BigDecimal("3.00"), "USD"),
                    price = FiatValue(BigDecimal("3.00"), "USD"),
                )
            val otherAccount = ethAccount()
            coEvery { accountsRepository.loadDeFiAddresses(VAULT_ID, false) } returns
                flowOf(
                    listOf(
                        Address(
                            chain = Chain.ThorChain,
                            address = "thor1",
                            accounts = listOf(runeAccount, otherAccount),
                        )
                    )
                )
            val loader = build(backgroundScope)

            loader.load(VAULT_ID)
            advanceUntilIdle()

            val rune = loadedAccounts.first { it.token.id.equals(Coins.ThorChain.RUNE.id, true) }
            assertEquals(BigInteger("200000000"), rune.tokenValue?.value)
            // 2 RUNE * $3 = $6
            assertEquals(0, BigDecimal("6.00").compareTo(rune.fiatValue?.value))
            // non-RUNE account passes through unchanged
            assertEquals(
                otherAccount,
                loadedAccounts.first { it.token.id.equals(Coins.Ethereum.ETH.id, true) },
            )
        }

    // ──────── WITHDRAW_RUJI ────────

    @Test
    fun `WITHDRAW_RUJI publishes the rewards-thor-ruji trio when staking details exist`() =
        runTest(mainDispatcher) {
            defiType = DeFiNavActions.WITHDRAW_RUJI
            val runeAccount = thorAccount(Coins.ThorChain.RUNE.copy(address = "thor1"))
            val rujiAccount = thorAccount(Coins.ThorChain.RUJI.copy(address = "thor1"))
            every { accountsRepository.loadAddresses(VAULT_ID) } returns
                flowOf(
                    listOf(
                        Address(
                            chain = Chain.ThorChain,
                            address = "thor1",
                            accounts = listOf(runeAccount, rujiAccount),
                        )
                    )
                )
            coEvery {
                stakingDetailsRepository.getStakingDetailsByCoindId(
                    VAULT_ID,
                    Coins.ThorChain.RUJI.id,
                )
            } returns stakingDetails(rewards = BigDecimal("123"))
            val loader = build(backgroundScope)

            loader.load(VAULT_ID)
            advanceUntilIdle()

            // Order matters: rewards account first, then thor (RUNE), then ruji.
            val accounts = loadedAccounts
            assertEquals(3, accounts.size)
            assertTrue(accounts[0].token.ticker.equals(RUJI_REWARDS_COIN.ticker, true))
            assertEquals(BigInteger("123"), accounts[0].tokenValue?.value)
            assertEquals(runeAccount, accounts[1])
            assertEquals(rujiAccount, accounts[2])
        }

    @Test
    fun `WITHDRAW_RUJI publishes empty list when staking details are absent`() =
        runTest(mainDispatcher) {
            defiType = DeFiNavActions.WITHDRAW_RUJI
            val runeAccount = thorAccount(Coins.ThorChain.RUNE)
            val rujiAccount = thorAccount(Coins.ThorChain.RUJI)
            every { accountsRepository.loadAddresses(VAULT_ID) } returns
                flowOf(
                    listOf(
                        Address(
                            chain = Chain.ThorChain,
                            address = "thor1",
                            accounts = listOf(runeAccount, rujiAccount),
                        )
                    )
                )
            coEvery {
                stakingDetailsRepository.getStakingDetailsByCoindId(
                    VAULT_ID,
                    Coins.ThorChain.RUJI.id,
                )
            } returns null
            val loader = build(backgroundScope)

            loader.load(VAULT_ID)
            advanceUntilIdle()

            // Loaded(emptyList) — an intentional empty publish that TokenPreselectionService
            // is expected to handle (fall through to default-coin map).
            assertEquals(AccountsLoadState.Loaded(emptyList()), accountsState.value)
        }

    @Test
    fun `WITHDRAW_RUJI clears stale accounts when the RUNE account is missing`() =
        runTest(mainDispatcher) {
            defiType = DeFiNavActions.WITHDRAW_RUJI
            // No RUNE account in the vault — publishRewards short-circuits to empty.
            every { accountsRepository.loadAddresses(VAULT_ID) } returns flowOf(emptyList())
            val loader = build(backgroundScope)

            // Pre-existing state from a prior nav action — must not leak through.
            accountsState.value =
                AccountsLoadState.Loaded(listOf(thorAccount(Coins.ThorChain.RUJI)))
            loader.load(VAULT_ID)
            advanceUntilIdle()

            // Cleared — otherwise the UI would render stale rows from the previous flow.
            assertEquals(AccountsLoadState.Loaded(emptyList()), accountsState.value)
        }

    @Test
    fun `WITHDRAW_RUJI publishes cached snapshot first then hydrated balances`() =
        runTest(mainDispatcher) {
            defiType = DeFiNavActions.WITHDRAW_RUJI
            val cachedRune = thorAccount(Coins.ThorChain.RUNE.copy(address = "thor1"))
            val cachedRuji = thorAccount(Coins.ThorChain.RUJI.copy(address = "thor1"))
            // Hydrated emission uses a different balance so we can tell the two apart.
            val hydratedRune =
                cachedRune.copy(tokenValue = TokenValue(BigInteger("999999"), cachedRune.token))
            val hydratedRuji =
                cachedRuji.copy(tokenValue = TokenValue(BigInteger("888888"), cachedRuji.token))
            // yield() between emissions gives the accountsState subscriber a chance to
            // observe the cached snapshot — without it StateFlow conflates the two writes
            // and the test only sees the final hydrated value.
            every { accountsRepository.loadAddresses(VAULT_ID) } returns
                flow {
                    emit(
                        listOf(
                            Address(
                                chain = Chain.ThorChain,
                                address = "thor1",
                                accounts = listOf(cachedRune, cachedRuji),
                            )
                        )
                    )
                    yield()
                    emit(
                        listOf(
                            Address(
                                chain = Chain.ThorChain,
                                address = "thor1",
                                accounts = listOf(hydratedRune, hydratedRuji),
                            )
                        )
                    )
                }
            coEvery {
                stakingDetailsRepository.getStakingDetailsByCoindId(
                    VAULT_ID,
                    Coins.ThorChain.RUJI.id,
                )
            } returns stakingDetails(rewards = BigDecimal("123"))
            val loader = build(backgroundScope)

            val observed = collectLoadedEmissions(this)

            loader.load(VAULT_ID)
            advanceUntilIdle()

            // Two Loaded emissions: cached snapshot first, hydrated second. Asserting only
            // the final state would let a regression that drops the cached emission pass.
            assertEquals(2, observed.size)
            assertEquals(BigInteger("1000000"), observed[0][1].tokenValue?.value) // cached RUNE
            assertEquals(BigInteger("1000000"), observed[0][2].tokenValue?.value) // cached RUJI
            assertEquals(BigInteger("999999"), observed[1][1].tokenValue?.value) // hydrated RUNE
            assertEquals(BigInteger("888888"), observed[1][2].tokenValue?.value) // hydrated RUJI
        }

    @Test
    fun `WITHDRAW_RUJI resolves staking details exactly once across cached and hydrated emissions`() =
        runTest(mainDispatcher) {
            // The rewards row is sourced from the staking-details repo, not from the
            // addresses flow — its value doesn't change between the cached and the
            // hydrated emission. The lookup must run once per load(), not per upstream
            // emission, to avoid N DB hits when the flow produces N emissions.
            defiType = DeFiNavActions.WITHDRAW_RUJI
            val runeAccount = thorAccount(Coins.ThorChain.RUNE.copy(address = "thor1"))
            val rujiAccount = thorAccount(Coins.ThorChain.RUJI.copy(address = "thor1"))
            every { accountsRepository.loadAddresses(VAULT_ID) } returns
                flow {
                    emit(
                        listOf(
                            Address(
                                chain = Chain.ThorChain,
                                address = "thor1",
                                accounts = listOf(runeAccount, rujiAccount),
                            )
                        )
                    )
                    yield()
                    emit(
                        listOf(
                            Address(
                                chain = Chain.ThorChain,
                                address = "thor1",
                                accounts = listOf(runeAccount, rujiAccount),
                            )
                        )
                    )
                }
            coEvery {
                stakingDetailsRepository.getStakingDetailsByCoindId(
                    VAULT_ID,
                    Coins.ThorChain.RUJI.id,
                )
            } returns stakingDetails(rewards = BigDecimal("123"))
            val loader = build(backgroundScope)

            loader.load(VAULT_ID)
            advanceUntilIdle()

            coVerify(exactly = 1) {
                stakingDetailsRepository.getStakingDetailsByCoindId(
                    VAULT_ID,
                    Coins.ThorChain.RUJI.id,
                )
            }
        }

    // ──────── WITHDRAW_USDC_CIRCLE ────────

    @Test
    fun `WITHDRAW_USDC_CIRCLE without msca address publishes ETH plus zero USDC`() =
        runTest(mainDispatcher) {
            defiType = DeFiNavActions.WITHDRAW_USDC_CIRCLE
            mscaAddress = null
            val ethAccount = ethAccount()
            every { accountsRepository.loadAddresses(VAULT_ID) } returns
                flowOf(
                    listOf(
                        Address(
                            chain = Chain.Ethereum,
                            address = ethAccount.token.address,
                            accounts = listOf(ethAccount),
                        )
                    )
                )
            val loader = build(backgroundScope)

            loader.load(VAULT_ID)
            advanceUntilIdle()

            val accounts = loadedAccounts
            assertEquals(2, accounts.size)
            assertEquals(ethAccount, accounts[0])
            assertTrue(accounts[1].token.ticker.equals("USDC", true))
            assertEquals(BigInteger.ZERO, accounts[1].tokenValue?.value)
            // No staking-details lookup happens without an MSCA address.
            coVerify(exactly = 0) { stakingDetailsRepository.getStakingDetailsById(any(), any()) }
        }

    @Test
    fun `WITHDRAW_USDC_CIRCLE with msca address publishes USDC backed by the cached stake amount`() =
        runTest(mainDispatcher) {
            defiType = DeFiNavActions.WITHDRAW_USDC_CIRCLE
            mscaAddress = "0xMSCA"
            val ethAccount = ethAccount()
            every { accountsRepository.loadAddresses(VAULT_ID) } returns
                flowOf(
                    listOf(
                        Address(
                            chain = Chain.Ethereum,
                            address = ethAccount.token.address,
                            accounts = listOf(ethAccount),
                        )
                    )
                )
            coEvery { stakingDetailsRepository.getStakingDetailsById(VAULT_ID, any()) } returns
                stakingDetails(stakeAmount = BigInteger("789"))
            val loader = build(backgroundScope)

            loader.load(VAULT_ID)
            advanceUntilIdle()

            val accounts = loadedAccounts
            assertEquals(2, accounts.size)
            assertEquals(BigInteger("789"), accounts[1].tokenValue?.value)
        }

    @Test
    fun `WITHDRAW_USDC_CIRCLE bails out with empty list when ETH account is missing`() =
        runTest(mainDispatcher) {
            defiType = DeFiNavActions.WITHDRAW_USDC_CIRCLE
            mscaAddress = "0xMSCA"
            // No ETH account in the vault → falling back to a zero-address ETH placeholder
            // would silently produce a USDC token with no address bound, which breaks any
            // later submit. The loader must publish empty instead.
            every { accountsRepository.loadAddresses(VAULT_ID) } returns flowOf(emptyList())
            val loader = build(backgroundScope)

            accountsState.value = AccountsLoadState.Loaded(listOf(ethAccount())) // sentinel
            loader.load(VAULT_ID)
            advanceUntilIdle()

            assertEquals(AccountsLoadState.Loaded(emptyList()), accountsState.value)
        }

    @Test
    fun `WITHDRAW_USDC_CIRCLE publishes cached snapshot first then hydrated balances`() =
        runTest(mainDispatcher) {
            defiType = DeFiNavActions.WITHDRAW_USDC_CIRCLE
            mscaAddress = "0xMSCA"
            val cachedEth = ethAccount()
            // Hydrated emission uses a different balance so we can tell the two apart.
            val hydratedEth =
                cachedEth.copy(tokenValue = TokenValue(BigInteger("123456789"), Coins.Ethereum.ETH))
            // yield() between emissions — see WITHDRAW_RUJI dual-emission test.
            every { accountsRepository.loadAddresses(VAULT_ID) } returns
                flow {
                    emit(
                        listOf(
                            Address(
                                chain = Chain.Ethereum,
                                address = cachedEth.token.address,
                                accounts = listOf(cachedEth),
                            )
                        )
                    )
                    yield()
                    emit(
                        listOf(
                            Address(
                                chain = Chain.Ethereum,
                                address = hydratedEth.token.address,
                                accounts = listOf(hydratedEth),
                            )
                        )
                    )
                }
            coEvery { stakingDetailsRepository.getStakingDetailsById(VAULT_ID, any()) } returns
                stakingDetails(stakeAmount = BigInteger("789"))
            val loader = build(backgroundScope)

            val observed = collectLoadedEmissions(this)

            loader.load(VAULT_ID)
            advanceUntilIdle()

            // Two Loaded emissions: cached then hydrated. USDC stake amount stays consistent
            // (sourced from staking details, not balances), but the ETH balance reflects
            // each emission — proves loadCircleUSDCAccount re-runs publishCircleUsdc per
            // emission instead of dropping the cached snapshot.
            assertEquals(2, observed.size)
            assertEquals(
                BigInteger("1000000000000000000"),
                observed[0][0].tokenValue?.value,
            ) // cached ETH
            assertEquals(BigInteger("123456789"), observed[1][0].tokenValue?.value) // hydrated ETH
            assertEquals(BigInteger("789"), observed[1][1].tokenValue?.value) // USDC stake
        }

    @Test
    fun `WITHDRAW_USDC_CIRCLE resolves staking details exactly once across cached and hydrated emissions`() =
        runTest(mainDispatcher) {
            // Same N-DB-hit concern as the WITHDRAW_RUJI variant — generateId only
            // depends on Coins.Ethereum.USDC + mscaAddress (constant for the load), so the
            // staking-details lookup must run once per load(), not per upstream emission.
            defiType = DeFiNavActions.WITHDRAW_USDC_CIRCLE
            mscaAddress = "0xMSCA"
            val cachedEth = ethAccount()
            val hydratedEth =
                cachedEth.copy(tokenValue = TokenValue(BigInteger("42"), Coins.Ethereum.ETH))
            every { accountsRepository.loadAddresses(VAULT_ID) } returns
                flow {
                    emit(
                        listOf(
                            Address(
                                chain = Chain.Ethereum,
                                address = cachedEth.token.address,
                                accounts = listOf(cachedEth),
                            )
                        )
                    )
                    yield()
                    emit(
                        listOf(
                            Address(
                                chain = Chain.Ethereum,
                                address = hydratedEth.token.address,
                                accounts = listOf(hydratedEth),
                            )
                        )
                    )
                }
            coEvery { stakingDetailsRepository.getStakingDetailsById(VAULT_ID, any()) } returns
                stakingDetails(stakeAmount = BigInteger("789"))
            val loader = build(backgroundScope)

            loader.load(VAULT_ID)
            advanceUntilIdle()

            coVerify(exactly = 1) {
                stakingDetailsRepository.getStakingDetailsById(VAULT_ID, any())
            }
        }

    // ──────── never-closing channelFlow semantics ────────

    @Test
    fun `load consumes cached and hydrated emissions from a never-closing channelFlow`() =
        runTest(mainDispatcher) {
            // Mirrors production: loadAddresses returns a channelFlow that emits cached +
            // hydrated then suspends in awaitClose() forever — never completes. flowOf()
            // would let collect return after the last emission, which hides the real
            // shape from these tests.
            defiType = null
            val cachedEth = ethAccount()
            val hydratedEth =
                cachedEth.copy(tokenValue = TokenValue(BigInteger("42"), Coins.Ethereum.ETH))
            every { accountsRepository.loadAddresses(VAULT_ID) } returns
                channelFlow {
                    send(
                        listOf(
                            Address(
                                chain = Chain.Ethereum,
                                address = cachedEth.token.address,
                                accounts = listOf(cachedEth),
                            )
                        )
                    )
                    // yield() lets the accountsState subscriber observe the cached
                    // snapshot — without it StateFlow conflates the two writes and only
                    // the hydrated value is seen.
                    yield()
                    send(
                        listOf(
                            Address(
                                chain = Chain.Ethereum,
                                address = hydratedEth.token.address,
                                accounts = listOf(hydratedEth),
                            )
                        )
                    )
                    awaitClose()
                }
            val loader = build(backgroundScope)
            val observed = collectLoadedEmissions(this)

            loader.load(VAULT_ID)
            advanceUntilIdle()

            // Both emissions observed even though the flow never closes. The collect inside
            // AccountsLoader keeps running (it's only released by job cancellation), but
            // the cached -> hydrated transition still propagates.
            assertEquals(2, observed.size)
            assertEquals(BigInteger("1000000000000000000"), observed[0][0].tokenValue?.value)
            assertEquals(BigInteger("42"), observed[1][0].tokenValue?.value)
        }

    @Test
    fun `second load cancels the prior never-closing collector and drops its stale publishes`() =
        runTest(mainDispatcher) {
            // The previous collector is parked inside awaitClose() when load() is called
            // again — Job.cancel() is cooperative so it could still execute one more
            // publishLoaded after the reset to Uninitialized. Generation gating (added in
            // the publishes-gated-by-generation commit) must drop those stale writes so
            // accountsState only reflects the new vault's data.
            defiType = null
            val firstEth =
                ethAccount().copy(tokenValue = TokenValue(BigInteger("1"), Coins.Ethereum.ETH))
            val secondEth =
                ethAccount().copy(tokenValue = TokenValue(BigInteger("2"), Coins.Ethereum.ETH))
            every { accountsRepository.loadAddresses(VAULT_ID) } returns
                channelFlow {
                    send(
                        listOf(
                            Address(
                                chain = Chain.Ethereum,
                                address = firstEth.token.address,
                                accounts = listOf(firstEth),
                            )
                        )
                    )
                    awaitClose()
                }
            every { accountsRepository.loadAddresses(VAULT_ID_2) } returns
                channelFlow {
                    send(
                        listOf(
                            Address(
                                chain = Chain.Ethereum,
                                address = secondEth.token.address,
                                accounts = listOf(secondEth),
                            )
                        )
                    )
                    awaitClose()
                }
            val loader = build(backgroundScope)
            val observed = collectLoadedEmissions(this)

            loader.load(VAULT_ID)
            advanceUntilIdle()
            loader.load(VAULT_ID_2)
            advanceUntilIdle()

            // First load emits firstEth; reset to Uninitialized flips the derived flow
            // off (filterIsInstance drops it from `observed`); second load emits
            // secondEth. The final state must be secondEth — not firstEth bleeding
            // through after cancel.
            assertEquals(BigInteger("2"), (observed.last())[0].tokenValue?.value)
            assertIs<AccountsLoadState.Loaded>(accountsState.value)
            assertEquals(
                BigInteger("2"),
                (accountsState.value as AccountsLoadState.Loaded).accounts[0].tokenValue?.value,
            )
        }

    // ──────── helpers ────────

    // Captures every Loaded emission published to accountsState into the returned list,
    // so dual-emission tests can assert cached and hydrated snapshots independently
    // instead of only checking the final state. The collector runs on the test's
    // backgroundScope (auto-cancelled at test end).
    private fun collectLoadedEmissions(
        scope: kotlinx.coroutines.test.TestScope
    ): List<List<Account>> {
        val emissions = mutableListOf<List<Account>>()
        scope.backgroundScope.launch {
            accountsState.filterIsInstance<AccountsLoadState.Loaded>().collect {
                emissions.add(it.accounts)
            }
        }
        return emissions
    }

    private fun build(scope: CoroutineScope) =
        AccountsLoader(
            scope = scope,
            accountsState = accountsState,
            accountsRepository = accountsRepository,
            stakingDetailsRepository = stakingDetailsRepository,
            defiTypeProvider = { defiType },
            mscaAddressProvider = { mscaAddress },
            bondedAmountProvider = { bondedAmount },
        )

    private fun ethAccount(): Account =
        Account(
            token = Coins.Ethereum.ETH.copy(address = "0xeth"),
            tokenValue =
                TokenValue(value = BigInteger("1000000000000000000"), token = Coins.Ethereum.ETH),
            fiatValue = null,
            price = null,
        )

    private fun thorAccount(coin: com.vultisig.wallet.data.models.Coin): Account =
        Account(
            token = coin,
            tokenValue = TokenValue(value = BigInteger("1000000"), token = coin),
            fiatValue = null,
            price = null,
        )

    private fun stakingDetails(
        rewards: BigDecimal? = null,
        stakeAmount: BigInteger = BigInteger.ZERO,
    ): StakingDetails =
        StakingDetails(
            id = "id",
            coin = Coins.ThorChain.RUJI,
            stakeAmount = stakeAmount,
            apr = null,
            estimatedRewards = null,
            nextPayoutDate = Date(0),
            rewards = rewards,
            rewardsCoin = null,
        )

    private companion object {
        const val VAULT_ID = "vault-id"
        const val VAULT_ID_2 = "vault-id-2"
    }
}
