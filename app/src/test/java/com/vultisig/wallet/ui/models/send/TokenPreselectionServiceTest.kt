@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.send

import com.vultisig.wallet.data.blockchain.thorchain.RujiStakingService.Companion.RUJI_REWARDS_COIN
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.ui.screens.v2.defi.model.DeFiNavActions
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class TokenPreselectionServiceTest {

    private val scheduler = TestCoroutineScheduler()
    private val mainDispatcher = UnconfinedTestDispatcher(scheduler)

    private val accountsState = MutableStateFlow<AccountsLoadState>(AccountsLoadState.Uninitialized)
    private var defiType: DeFiNavActions? = null
    private var selectedToken: Coin? = null
    private val selectedTokens = mutableListOf<Coin>()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        defiType = null
        selectedToken = null
        accountsState.value = AccountsLoadState.Uninitialized
        selectedTokens.clear()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun loaded(vararg accounts: Account) {
        accountsState.value = AccountsLoadState.Loaded(accounts.toList())
    }

    // ──────── null defi type → findPreselectedToken ────────

    @Test
    fun `preSelect null defi - tokenId match wins over chainId match`() =
        runTest(mainDispatcher) {
            defiType = null
            val ethNative = ethAccount(Coins.Ethereum.ETH)
            val ethUsdc = ethAccount(Coins.Ethereum.USDC)
            loaded(ethNative, ethUsdc)
            val service = build(backgroundScope)

            service.preSelect(
                preSelectedChainIds = listOf(Chain.Ethereum.id),
                preSelectedTokenId = Coins.Ethereum.USDC.id,
            )
            advanceUntilIdle()

            // tokenId match returns immediately, even though native ETH would also match by chain.
            assertEquals(listOf(Coins.Ethereum.USDC), selectedTokens)
        }

    @Test
    fun `preSelect null defi - chainId match selects the native token of that chain`() =
        runTest(mainDispatcher) {
            defiType = null
            val ethUsdc = ethAccount(Coins.Ethereum.USDC) // non-native
            val ethNative = ethAccount(Coins.Ethereum.ETH) // native
            loaded(ethUsdc, ethNative)
            val service = build(backgroundScope)

            service.preSelect(
                preSelectedChainIds = listOf(Chain.Ethereum.id),
                preSelectedTokenId = "no-such-token",
            )
            advanceUntilIdle()

            // chainId match only fires for the native token.
            assertEquals(listOf(Coins.Ethereum.ETH), selectedTokens)
        }

    @Test
    fun `preSelect null defi - falls back to the first account when nothing matches`() =
        runTest(mainDispatcher) {
            defiType = null
            val first = ethAccount(Coins.Ethereum.USDC)
            loaded(first)
            val service = build(backgroundScope)

            service.preSelect(
                preSelectedChainIds = listOf("no-chain"),
                preSelectedTokenId = "no-token",
            )
            advanceUntilIdle()

            assertEquals(listOf(Coins.Ethereum.USDC), selectedTokens)
        }

    // ──────── DeFi defi type → findDeFiPreselectedToken ────────

    @Test
    fun `preSelect WITHDRAW_RUJI - default coin is the RUJI rewards coin when no account match`() =
        runTest(mainDispatcher) {
            defiType = DeFiNavActions.WITHDRAW_RUJI
            // Non-empty Loaded list with no token-id match → falls through to the default-coin
            // map. Uninitialized would still be skipped; Loaded(empty) would also fall through
            // to the default-coin map (covered separately).
            loaded(thorAccount(Coins.ThorChain.RUNE))
            val service = build(backgroundScope)

            service.preSelect(preSelectedChainIds = listOf(null), preSelectedTokenId = null)
            advanceUntilIdle()

            assertEquals(listOf(RUJI_REWARDS_COIN), selectedTokens)
        }

    @Test
    fun `preSelect STAKE_RUJI - tokenId match in account list wins over default-coin`() =
        runTest(mainDispatcher) {
            defiType = DeFiNavActions.STAKE_RUJI
            val rujiAccount = thorAccount(Coins.ThorChain.RUJI.copy(address = "thor-addr"))
            loaded(rujiAccount)
            val service = build(backgroundScope)

            service.preSelect(
                preSelectedChainIds = listOf(null),
                preSelectedTokenId = Coins.ThorChain.RUJI.id,
            )
            advanceUntilIdle()

            // The accounts-list copy beats the default-coin constant — preserves cached state.
            assertEquals(listOf(rujiAccount.token), selectedTokens)
        }

    @Test
    fun `preSelect FREEZE_TRX - default coin is TRX`() =
        runTest(mainDispatcher) {
            defiType = DeFiNavActions.FREEZE_TRX
            loaded(ethAccount(Coins.Ethereum.ETH))
            val service = build(backgroundScope)

            service.preSelect(preSelectedChainIds = listOf(null), preSelectedTokenId = null)
            advanceUntilIdle()

            assertEquals(1, selectedTokens.size)
            assertEquals("TRX", selectedTokens[0].ticker)
            assertEquals(Chain.Tron, selectedTokens[0].chain)
        }

    @Test
    fun `preSelect WITHDRAW_RUJI - Loaded(empty) does not preselect static template coin`() =
        runTest(mainDispatcher) {
            defiType = DeFiNavActions.WITHDRAW_RUJI
            // AccountsLoader publishes Loaded(emptyList) when prerequisites are missing
            // (e.g. no RUNE/RUJI in vault). Returning RUJI_REWARDS_COIN here would cause
            // collectSelectedAccount to synthesize an Account with tokenValue = null, making
            // the WITHDRAW form look submittable when there is nothing real to withdraw.
            accountsState.value = AccountsLoadState.Loaded(emptyList())
            val service = build(backgroundScope)

            service.preSelect(preSelectedChainIds = listOf(null), preSelectedTokenId = null)
            advanceUntilIdle()

            assertEquals(emptyList<Any>(), selectedTokens)
        }

    @Test
    fun `preSelect WITHDRAW_USDC_CIRCLE - Loaded(empty) does not preselect static template coin`() =
        runTest(mainDispatcher) {
            defiType = DeFiNavActions.WITHDRAW_USDC_CIRCLE
            accountsState.value = AccountsLoadState.Loaded(emptyList())
            val service = build(backgroundScope)

            service.preSelect(preSelectedChainIds = listOf(null), preSelectedTokenId = null)
            advanceUntilIdle()

            assertEquals(emptyList<Any>(), selectedTokens)
        }

    // ──────── forcePreselection ────────

    @Test
    fun `preSelect leaves the prior selection alone when force is false`() =
        runTest(mainDispatcher) {
            defiType = null
            selectedToken = Coins.Ethereum.USDC // user has already chosen
            loaded(ethAccount(Coins.Ethereum.ETH))
            val service = build(backgroundScope)

            service.preSelect(
                preSelectedChainIds = listOf(Chain.Ethereum.id),
                preSelectedTokenId = null,
                forcePreselection = false,
            )
            advanceUntilIdle()

            // selectedToken was non-null, force=false → onTokenSelected must not have fired.
            assertEquals(emptyList(), selectedTokens)
        }

    @Test
    fun `preSelect overrides the prior selection when force is true`() =
        runTest(mainDispatcher) {
            defiType = null
            selectedToken = Coins.Ethereum.USDC
            loaded(ethAccount(Coins.Ethereum.ETH))
            val service = build(backgroundScope)

            service.preSelect(
                preSelectedChainIds = listOf(Chain.Ethereum.id),
                preSelectedTokenId = null,
                forcePreselection = true,
            )
            advanceUntilIdle()

            assertEquals(listOf(Coins.Ethereum.ETH), selectedTokens)
        }

    @Test
    fun `preSelect skips the Uninitialized sentinel and only fires once Loaded arrives`() =
        runTest(mainDispatcher) {
            defiType = null
            accountsState.value = AccountsLoadState.Uninitialized
            val service = build(backgroundScope)

            service.preSelect(preSelectedChainIds = listOf(null), preSelectedTokenId = null)
            advanceUntilIdle()
            // Uninitialized must not trigger preselection — otherwise we'd lock in a static
            // template before AccountsLoader publishes the real list.
            assertEquals(emptyList(), selectedTokens)

            // Real accounts land — now we preselect.
            loaded(ethAccount(Coins.Ethereum.ETH))
            advanceUntilIdle()
            assertEquals(listOf(Coins.Ethereum.ETH), selectedTokens)
        }

    @Test
    fun `preSelect with force=true fires once and then defers to the user`() =
        runTest(mainDispatcher) {
            defiType = null
            selectedToken = null
            loaded(ethAccount(Coins.Ethereum.ETH))
            val service = build(backgroundScope)

            service.preSelect(
                preSelectedChainIds = listOf(Chain.Ethereum.id),
                preSelectedTokenId = null,
                forcePreselection = true,
            )
            advanceUntilIdle()
            assertEquals(listOf(Coins.Ethereum.ETH), selectedTokens)

            // Simulate the user typing into amount fields — selectedToken stays the one we
            // forced. A subsequent accounts hydration must not re-fire onTokenSelected and
            // wipe their input via resetUserInputCache.
            selectedToken = Coins.Ethereum.ETH
            loaded(ethAccount(Coins.Ethereum.ETH), ethAccount(Coins.Ethereum.USDC))
            advanceUntilIdle()
            assertEquals(listOf(Coins.Ethereum.ETH), selectedTokens) // unchanged
        }

    @Test
    fun `preSelect cancels the in-flight observer when invoked twice`() =
        runTest(mainDispatcher) {
            defiType = null
            loaded(ethAccount(Coins.Ethereum.ETH))
            val service = build(backgroundScope)

            // First call — token id match would pick USDC, but it's not in accounts, so it falls
            // back to first-account (ETH). Second call cancels the first, re-runs against the
            // same accounts list with a different tokenId target.
            service.preSelect(preSelectedChainIds = listOf(null), preSelectedTokenId = "X")
            service.preSelect(preSelectedChainIds = listOf(null), preSelectedTokenId = "Y")
            advanceUntilIdle()

            // After both calls run, the latest one is what survives. Both fall back to the only
            // account (ETH), so we expect at least one ETH selection from the surviving job.
            // Pin the value AND that something was actually selected — Iterable.all returns
            // true on an empty list (vacuous truth), which would hide a regression that
            // cancelled both jobs.
            assertTrue(selectedTokens.isNotEmpty())
            assertTrue(selectedTokens.all { it == Coins.Ethereum.ETH })
        }

    // ──────── helpers ────────

    private fun build(scope: CoroutineScope) =
        TokenPreselectionService(
            scope = scope,
            accountsState = accountsState,
            defiTypeProvider = { defiType },
            selectedTokenProvider = { selectedToken },
            onTokenSelected = { selectedTokens.add(it) },
        )

    private fun ethAccount(coin: Coin): Account =
        Account(
            token = coin,
            tokenValue = TokenValue(value = BigInteger.ZERO, token = coin),
            fiatValue = null,
            price = null,
        )

    private fun thorAccount(coin: Coin): Account =
        Account(
            token = coin,
            tokenValue = TokenValue(value = BigInteger.ZERO, token = coin),
            fiatValue = null,
            price = null,
        )
}
