package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.mappers.ChainAndTokensToAddressMapperImpl
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.models.TokenBalance
import com.vultisig.wallet.data.models.TokenBalanceAndPrice
import com.vultisig.wallet.data.models.TokenBalanceWrapped
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.Vault
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class AccountsRepositoryImplTest {

    private lateinit var vaultRepository: VaultRepository
    private lateinit var balanceRepository: BalanceRepository
    private lateinit var tokenPriceRepository: TokenPriceRepository
    private lateinit var splTokenRepository: SplTokenRepository
    private lateinit var repository: AccountsRepositoryImpl

    @BeforeEach
    fun setUp() {
        vaultRepository = mockk()
        balanceRepository = mockk()
        tokenPriceRepository = mockk()
        splTokenRepository = mockk(relaxed = true)
        repository =
            AccountsRepositoryImpl(
                vaultRepository = vaultRepository,
                balanceRepository = balanceRepository,
                tokenPriceRepository = tokenPriceRepository,
                chainAndTokensToAddressMapper = ChainAndTokensToAddressMapperImpl(),
                splTokenRepository = splTokenRepository,
            )
    }

    @Test
    fun `emits cached DB snapshot before network balances`() = runTest {
        val sol = Coins.Solana.SOL.copy(address = SOL_ADDRESS)
        stubVault(sol)
        coJustRun { tokenPriceRepository.refresh(any()) }

        // Cached DB balance present for SOL.
        coEvery { balanceRepository.getCachedTokenBalances(any(), any()) } returns
            listOf(wrapped(amount = CACHED, coin = sol))

        // Network balance is gated so it cannot win the race against the cached emit.
        val networkGate = CompletableDeferred<Unit>()
        every { balanceRepository.getTokenBalanceAndPrice(SOL_ADDRESS, sol) } returns
            gatedBalance(networkGate, amount = NETWORK, coin = sol)

        val emissions = mutableListOf<List<Address>>()
        val job = launch { repository.loadAddresses(VAULT_ID).collect(emissions::add) }

        advanceUntilIdle()

        // With the network call still blocked, the only thing the UI has seen is the cached row —
        // proving the early DB snapshot is always emitted first.
        assertTrue(emissions.isNotEmpty(), "expected a cached emission before the network returned")
        assertEquals(CACHED, emissions.first().solValue())

        networkGate.complete(Unit)
        advanceUntilIdle()

        assertEquals(NETWORK, emissions.last().solValue())
        job.cancel()
    }

    @Test
    fun `streams each chain as it resolves without waiting for the slowest chain`() = runTest {
        val eth = Coins.Ethereum.ETH.copy(address = ETH_ADDRESS)
        val sol = Coins.Solana.SOL.copy(address = SOL_ADDRESS)
        stubVault(eth, sol)
        coJustRun { tokenPriceRepository.refresh(any()) }

        coEvery { balanceRepository.getCachedTokenBalances(any(), any()) } returns
            listOf(wrapped(amount = CACHED, coin = eth), wrapped(amount = CACHED, coin = sol))

        // ETH resolves immediately; SOL is gated (simulating a slow chain).
        every { balanceRepository.getTokenBalanceAndPrice(ETH_ADDRESS, eth) } returns
            flowOf(balance(amount = ETH_NETWORK, coin = eth))
        val solanaGate = CompletableDeferred<Unit>()
        every { balanceRepository.getTokenBalanceAndPrice(SOL_ADDRESS, sol) } returns
            gatedBalance(solanaGate, amount = SOL_NETWORK, coin = sol)

        val emissions = mutableListOf<List<Address>>()
        val job = launch { repository.loadAddresses(VAULT_ID).collect(emissions::add) }

        advanceUntilIdle()

        // ETH's balance must surface while SOL is still pending — the whole point of per-chain
        // streaming. Before the fix, no emission carried ETH's network value until SOL also
        // finished.
        val streamedEth = emissions.firstOrNull { it.value(Chain.Ethereum) == ETH_NETWORK }
        assertNotNull(streamedEth, "ETH balance should stream before the slow SOL chain resolves")
        assertEquals(
            CACHED,
            streamedEth.value(Chain.Solana),
            "SOL should still show its cached value while its network call is in flight",
        )

        solanaGate.complete(Unit)
        advanceUntilIdle()

        val finalEmission = emissions.last()
        assertEquals(ETH_NETWORK, finalEmission.value(Chain.Ethereum))
        assertEquals(SOL_NETWORK, finalEmission.value(Chain.Solana))
        job.cancel()
    }

    private fun stubVault(vararg coins: Coin) {
        val vault = Vault(id = VAULT_ID, name = "Test Vault", coins = coins.toList())
        every { vaultRepository.getAsFlow(VAULT_ID) } returns flowOf(vault)
    }

    private fun wrapped(amount: Long, coin: Coin) =
        TokenBalanceWrapped(
            tokenBalance = tokenBalance(amount, coin),
            address = coin.address,
            coinId = coin.id,
        )

    private fun balance(amount: Long, coin: Coin) =
        TokenBalanceAndPrice(
            tokenBalance = tokenBalance(amount, coin),
            price = FiatValue(BigDecimal.ONE, USD),
        )

    private fun gatedBalance(
        gate: CompletableDeferred<Unit>,
        amount: Long,
        coin: Coin,
    ): Flow<TokenBalanceAndPrice> = flow {
        gate.await()
        emit(balance(amount, coin))
    }

    private fun tokenBalance(amount: Long, coin: Coin) =
        TokenBalance(
            tokenValue = TokenValue(BigInteger.valueOf(amount), coin.ticker, coin.decimal),
            fiatValue = FiatValue(BigDecimal.valueOf(amount), USD),
        )

    private fun List<Address>.value(chain: Chain): Long? =
        firstOrNull { it.chain == chain }?.accounts?.firstOrNull()?.tokenValue?.value?.toLong()

    private fun List<Address>.solValue(): Long? = value(Chain.Solana)

    private companion object {
        const val VAULT_ID = "vault-1"
        const val USD = "USD"
        const val ETH_ADDRESS = "0xeth"
        const val SOL_ADDRESS = "sol-addr"
        const val CACHED = 5L
        const val NETWORK = 10L
        const val ETH_NETWORK = 100L
        const val SOL_NETWORK = 200L
    }
}
