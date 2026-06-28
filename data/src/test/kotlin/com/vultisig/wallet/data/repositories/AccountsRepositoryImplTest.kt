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
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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

        // ETH (EVM) resolves immediately via the batched Multicall3 path; SOL is gated (slow
        // chain).
        coEvery { balanceRepository.getEvmTokenBalancesAndPrices(ETH_ADDRESS, any()) } returns
            mapOf(eth.id to balance(amount = ETH_NETWORK, coin = eth))
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
        // Exactly two emissions while SOL is gated — the cached snapshot and ETH's streamed update.
        // A batched implementation would only have emitted the cached snapshot at this point, so
        // the count, not just the presence of ETH_NETWORK, pins down the streaming behaviour.
        assertEquals(
            2,
            emissions.size,
            "expected cached + ETH-streamed emissions before SOL resolves",
        )

        solanaGate.complete(Unit)
        advanceUntilIdle()

        val finalEmission = emissions.last()
        assertEquals(ETH_NETWORK, finalEmission.value(Chain.Ethereum))
        assertEquals(SOL_NETWORK, finalEmission.value(Chain.Solana))
        job.cancel()
    }

    @Test
    fun `loadAddressBalances marks completion only after every chain resolves`() = runTest {
        val eth = Coins.Ethereum.ETH.copy(address = ETH_ADDRESS)
        val sol = Coins.Solana.SOL.copy(address = SOL_ADDRESS)
        stubVault(eth, sol)
        coJustRun { tokenPriceRepository.refresh(any()) }
        coEvery { balanceRepository.getCachedTokenBalances(any(), any()) } returns
            listOf(wrapped(amount = CACHED, coin = eth), wrapped(amount = CACHED, coin = sol))

        coEvery { balanceRepository.getEvmTokenBalancesAndPrices(ETH_ADDRESS, any()) } returns
            mapOf(eth.id to balance(amount = ETH_NETWORK, coin = eth))
        val solanaGate = CompletableDeferred<Unit>()
        every { balanceRepository.getTokenBalanceAndPrice(SOL_ADDRESS, sol) } returns
            gatedBalance(solanaGate, amount = SOL_NETWORK, coin = sol)

        val emissions = mutableListOf<AddressBalancesUpdate>()
        val job = launch { repository.loadAddressBalances(VAULT_ID).collect(emissions::add) }

        advanceUntilIdle()
        // While SOL is still in flight the load is not done, so nothing is marked complete.
        assertTrue(
            emissions.none { it.isComplete },
            "load must not report completion while a chain is still pending",
        )

        solanaGate.complete(Unit)
        advanceUntilIdle()

        // Exactly one terminal emission, it is the last, and it carries every network balance.
        assertEquals(1, emissions.count { it.isComplete })
        val terminal = emissions.last()
        assertTrue(terminal.isComplete)
        assertEquals(ETH_NETWORK, terminal.addresses.value(Chain.Ethereum))
        assertEquals(SOL_NETWORK, terminal.addresses.value(Chain.Solana))
        job.cancel()
    }

    @Test
    fun `loadAddresses drops the redundant terminal completion emission`() = runTest {
        val sol = Coins.Solana.SOL.copy(address = SOL_ADDRESS)
        stubVault(sol)
        coJustRun { tokenPriceRepository.refresh(any()) }
        coEvery { balanceRepository.getCachedTokenBalances(any(), any()) } returns
            listOf(wrapped(amount = CACHED, coin = sol))
        every { balanceRepository.getTokenBalanceAndPrice(SOL_ADDRESS, sol) } returns
            flowOf(balance(amount = NETWORK, coin = sol))

        val listEmissions = mutableListOf<List<Address>>()
        val balanceEmissions = mutableListOf<AddressBalancesUpdate>()
        val listJob = launch { repository.loadAddresses(VAULT_ID).collect(listEmissions::add) }
        val balanceJob = launch {
            repository.loadAddressBalances(VAULT_ID).collect(balanceEmissions::add)
        }

        advanceUntilIdle()

        // loadAddressBalances ends with a terminal completion emission that just repeats the last
        // snapshot; loadAddresses filters it out, so it emits exactly one fewer time while still
        // surfacing the final network balance.
        assertTrue(balanceEmissions.any { it.isComplete })
        assertEquals(balanceEmissions.size - 1, listEmissions.size)
        assertEquals(NETWORK, listEmissions.last().solValue())
        listJob.cancel()
        balanceJob.cancel()
    }

    @Test
    fun `EVM chain fetches all token balances through a single batched multicall`() = runTest {
        val eth = Coins.Ethereum.ETH.copy(address = ETH_ADDRESS)
        val aave = Coins.Ethereum.AAVE.copy(address = ETH_ADDRESS)
        stubVault(eth, aave)
        coJustRun { tokenPriceRepository.refresh(any()) }
        coEvery { balanceRepository.getCachedTokenBalances(any(), any()) } returns
            listOf(wrapped(amount = CACHED, coin = eth), wrapped(amount = CACHED, coin = aave))

        // One batched call resolves every EVM token on the chain.
        coEvery {
            balanceRepository.getEvmTokenBalancesAndPrices(ETH_ADDRESS, listOf(eth, aave))
        } returns
            mapOf(
                eth.id to balance(amount = ETH_NETWORK, coin = eth),
                aave.id to balance(amount = NETWORK, coin = aave),
            )

        val emissions = mutableListOf<List<Address>>()
        val job = launch { repository.loadAddresses(VAULT_ID).collect(emissions::add) }
        advanceUntilIdle()

        val ethAccounts =
            emissions.last().firstOrNull { it.chain == Chain.Ethereum }?.accounts.orEmpty()
        assertEquals(
            ETH_NETWORK,
            ethAccounts.firstOrNull { it.token.id == eth.id }?.tokenValue?.value?.toLong(),
        )
        assertEquals(
            NETWORK,
            ethAccounts.firstOrNull { it.token.id == aave.id }?.tokenValue?.value?.toLong(),
        )

        // Exactly one batched RPC for the chain, and the per-token path is never used for EVM.
        coVerify(exactly = 1) {
            balanceRepository.getEvmTokenBalancesAndPrices(ETH_ADDRESS, listOf(eth, aave))
        }
        verify(exactly = 0) { balanceRepository.getTokenBalanceAndPrice(ETH_ADDRESS, any()) }
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
