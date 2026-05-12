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
import kotlin.test.assertNull
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

    private val accounts = MutableStateFlow<List<Account>>(emptyList())
    private var defiType: DeFiNavActions? = null
    private var selectedToken: Coin? = null
    private val selectedTokens = mutableListOf<Coin>()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        defiType = null
        selectedToken = null
        accounts.value = emptyList()
        selectedTokens.clear()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ──────── null defi type → findPreselectedToken ────────

    @Test
    fun `preSelect null defi - tokenId match wins over chainId match`() =
        runTest(mainDispatcher) {
            defiType = null
            val ethNative = ethAccount(Coins.Ethereum.ETH)
            val ethUsdc = ethAccount(Coins.Ethereum.USDC)
            accounts.value = listOf(ethNative, ethUsdc)
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
            accounts.value = listOf(ethUsdc, ethNative)
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
            accounts.value = listOf(first)
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
            // Non-empty accounts list with no token-id match → falls through to the
            // default-coin map. (Empty accounts is an initial-emission sentinel; the service
            // intentionally waits for AccountsLoader to publish the real list.)
            accounts.value = listOf(thorAccount(Coins.ThorChain.RUNE))
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
            accounts.value = listOf(rujiAccount)
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
            // Need at least one account so the observer doesn't skip the empty initial
            // emission; no token-id match → falls through to the default-coin map.
            accounts.value = listOf(ethAccount(Coins.Ethereum.ETH))
            val service = build(backgroundScope)

            service.preSelect(preSelectedChainIds = listOf(null), preSelectedTokenId = null)
            advanceUntilIdle()

            assertEquals(1, selectedTokens.size)
            assertEquals("TRX", selectedTokens[0].ticker)
            assertEquals(Chain.Tron, selectedTokens[0].chain)
        }

    // ──────── forcePreselection ────────

    @Test
    fun `preSelect leaves the prior selection alone when force is false`() =
        runTest(mainDispatcher) {
            defiType = null
            selectedToken = Coins.Ethereum.USDC // user has already chosen
            accounts.value = listOf(ethAccount(Coins.Ethereum.ETH))
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
            accounts.value = listOf(ethAccount(Coins.Ethereum.ETH))
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
    fun `preSelect skips the empty initial emission and only fires once accounts arrives`() =
        runTest(mainDispatcher) {
            defiType = null
            accounts.value = emptyList() // initial StateFlow value
            val service = build(backgroundScope)

            service.preSelect(preSelectedChainIds = listOf(null), preSelectedTokenId = null)
            advanceUntilIdle()
            // Empty emission must not trigger preselection — otherwise we'd lock in a static
            // template that lacks an address binding.
            assertEquals(emptyList(), selectedTokens)

            // Real accounts land — now we preselect.
            accounts.value = listOf(ethAccount(Coins.Ethereum.ETH))
            advanceUntilIdle()
            assertEquals(listOf(Coins.Ethereum.ETH), selectedTokens)
        }

    @Test
    fun `preSelect with force=true fires once and then defers to the user`() =
        runTest(mainDispatcher) {
            defiType = null
            selectedToken = null
            accounts.value = listOf(ethAccount(Coins.Ethereum.ETH))
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
            accounts.value = listOf(ethAccount(Coins.Ethereum.ETH), ethAccount(Coins.Ethereum.USDC))
            advanceUntilIdle()
            assertEquals(listOf(Coins.Ethereum.ETH), selectedTokens) // unchanged
        }

    @Test
    fun `preSelect cancels the in-flight observer when invoked twice`() =
        runTest(mainDispatcher) {
            defiType = null
            accounts.value = listOf(ethAccount(Coins.Ethereum.ETH))
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
            accounts = accounts,
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

    @Suppress("unused") private fun unused(): Coin? = null.also { assertNull(it) }
}
