package com.vultisig.wallet.data.blockchain.thorchain

import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.api.models.thorchain.RujiStakeBalances
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.repositories.StakingDetailsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.math.BigInteger
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

internal class RujiStakingServiceTest {

    private val thorChainApi: ThorChainApi = mockk()
    private val stakingDetailsRepository: StakingDetailsRepository = mockk(relaxed = true)

    private val service = RujiStakingService(thorChainApi, stakingDetailsRepository)

    @Test
    fun `emits the bonded position even when no receipt is held`() = runTest {
        // The "Standard" pool has no receipt token, so this is the default staker's shape — the
        // position must not be suppressed by the auto-compounding side's zero (#5419).
        givenBalances(bonded = BigInteger("7875733"), autoCompound = BigInteger.ZERO)

        val positions = service.getStakingDetailsFromNetwork(ADDRESS)

        assertEquals(BigInteger("7875733"), positions.forCoin(Coins.ThorChain.RUJI.id))
        assertEquals(BigInteger.ZERO, positions.forCoin(Coins.ThorChain.sRUJI.id))
    }

    @Test
    fun `emits the auto-compounding position even when nothing is bonded`() = runTest {
        givenBalances(bonded = BigInteger.ZERO, autoCompound = BigInteger("1406486651509"))

        val positions = service.getStakingDetailsFromNetwork(ADDRESS)

        assertEquals(BigInteger.ZERO, positions.forCoin(Coins.ThorChain.RUJI.id))
        assertEquals(BigInteger("1406486651509"), positions.forCoin(Coins.ThorChain.sRUJI.id))
    }

    @Test
    fun `keeps the two positions independent when both are held`() = runTest {
        givenBalances(
            bonded = BigInteger("1638238990000"),
            autoCompound = BigInteger("1406486651509"),
        )

        val positions = service.getStakingDetailsFromNetwork(ADDRESS)

        assertEquals(2, positions.size)
        assertEquals(BigInteger("1638238990000"), positions.forCoin(Coins.ThorChain.RUJI.id))
        assertEquals(BigInteger("1406486651509"), positions.forCoin(Coins.ThorChain.sRUJI.id))
    }

    @Test
    fun `attributes rewards and revenue to the bonded position only`() = runTest {
        // Auto-compounded revenue is already inside the position's amount; surfacing a claim there
        // would offer to withdraw rewards that do not exist.
        givenBalances(
            bonded = BigInteger("100"),
            autoCompound = BigInteger("200"),
            rewards = BigInteger("500"),
        )

        val positions = service.getStakingDetailsFromNetwork(ADDRESS)

        val bonded = positions.single { it.coin.id == Coins.ThorChain.RUJI.id }
        val compounded = positions.single { it.coin.id == Coins.ThorChain.sRUJI.id }
        assertEquals(BigInteger("500").toBigDecimal(), bonded.rewards)
        assertEquals(null, compounded.rewards)
        assertEquals(null, compounded.rewardsCoin)
    }

    @Test
    fun `caches both positions after a successful fetch`() = runTest {
        givenBalances(bonded = BigInteger("100"), autoCompound = BigInteger("200"))
        coEvery { stakingDetailsRepository.getStakingDetails(VAULT_ID) } returns emptyList()

        service.getStakingDetails(ADDRESS, VAULT_ID).toList()

        coVerify {
            stakingDetailsRepository.saveAllStakingDetails(
                VAULT_ID,
                match { details -> details.size == 2 },
            )
        }
    }

    @Test
    fun `falls back to both cached positions when the network read fails`() = runTest {
        coEvery { thorChainApi.getRujiStakeBalance(ADDRESS) } throws RuntimeException("boom")
        val cached =
            listOf(
                stakingDetails(Coins.ThorChain.RUJI, BigInteger("100")),
                stakingDetails(Coins.ThorChain.sRUJI, BigInteger("200")),
            )
        coEvery { stakingDetailsRepository.getStakingDetails(VAULT_ID) } returns cached

        val emissions = service.getStakingDetails(ADDRESS, VAULT_ID).toList()

        assertEquals(cached, emissions.last())
    }

    @Test
    fun `ignores a pre-upgrade cache holding only the bonded row`() = runTest {
        // A single cached row predates the dual read: its RUJI amount came from the old collapsed
        // logic, so it may be a false zero or a raw sRUJI share count. Emitting it would render a
        // wrong bonded amount and leave the compounding card with nothing to resolve it.
        givenBalances(bonded = BigInteger("100"), autoCompound = BigInteger("200"))
        coEvery { stakingDetailsRepository.getStakingDetails(VAULT_ID) } returns
            listOf(stakingDetails(Coins.ThorChain.RUJI, BigInteger("999")))

        val emissions = service.getStakingDetails(ADDRESS, VAULT_ID).toList()

        assertEquals(1, emissions.size)
        assertEquals(BigInteger("100"), emissions.single().forCoin(Coins.ThorChain.RUJI.id))
        assertEquals(BigInteger("200"), emissions.single().forCoin(Coins.ThorChain.sRUJI.id))
    }

    @Test
    fun `surfaces the failure rather than a legacy half cache when the network read fails`() =
        runTest {
            // Nothing trustworthy to fall back on, so the error must reach the caller: the DeFi tab
            // clears both cards' loading state on it, where a half emission would leave the
            // compounding one spinning forever and drop it from the aggregate balance.
            coEvery { thorChainApi.getRujiStakeBalance(ADDRESS) } throws RuntimeException("boom")
            coEvery { stakingDetailsRepository.getStakingDetails(VAULT_ID) } returns
                listOf(stakingDetails(Coins.ThorChain.RUJI, BigInteger("999")))

            assertThrows(RuntimeException::class.java) {
                runBlocking { service.getStakingDetails(ADDRESS, VAULT_ID).toList() }
            }
            Unit
        }

    @Test
    fun `carries the pool APR onto the bonded position`() = runTest {
        // The rate was fetched but dropped here, so the card rendered without it (#5498).
        givenBalances(
            bonded = BigInteger("7875733"),
            autoCompound = BigInteger.ZERO,
            apr = 0.011623890337,
        )

        val positions = service.getStakingDetailsFromNetwork(ADDRESS)

        assertEquals(0.011623890337, positions.aprForCoin(Coins.ThorChain.RUJI.id))
    }

    @Test
    fun `leaves the auto-compounding position without an APR of its own`() = runTest {
        // Its revenue is reinvested into its own amount rather than published as a separate rate.
        givenBalances(
            bonded = BigInteger.ZERO,
            autoCompound = BigInteger("1406486651509"),
            apr = 0.011623890337,
        )

        val positions = service.getStakingDetailsFromNetwork(ADDRESS)

        assertEquals(null, positions.aprForCoin(Coins.ThorChain.sRUJI.id))
    }

    @Test
    fun `reports no APR when the pool publishes none`() = runTest {
        givenBalances(bonded = BigInteger("7875733"), autoCompound = BigInteger.ZERO, apr = null)

        val positions = service.getStakingDetailsFromNetwork(ADDRESS)

        assertEquals(null, positions.aprForCoin(Coins.ThorChain.RUJI.id))
    }

    private fun givenBalances(
        bonded: BigInteger,
        autoCompound: BigInteger,
        rewards: BigInteger = BigInteger.ZERO,
        apr: Double? = null,
    ) {
        coEvery { thorChainApi.getRujiStakeBalance(ADDRESS) } returns
            RujiStakeBalances(
                stakeAmount = bonded,
                stakeTicker = "RUJI",
                autoCompoundAmount = autoCompound,
                autoCompoundShares = autoCompound,
                rewardsAmount = rewards,
                apr = apr,
            )
    }

    private fun stakingDetails(coin: com.vultisig.wallet.data.models.Coin, amount: BigInteger) =
        com.vultisig.wallet.data.blockchain.model.StakingDetails(
            id = coin.id,
            coin = coin,
            stakeAmount = amount,
            apr = null,
            estimatedRewards = null,
            nextPayoutDate = null,
            rewards = null,
            rewardsCoin = null,
        )

    private fun List<com.vultisig.wallet.data.blockchain.model.StakingDetails>.forCoin(
        coinId: String
    ): BigInteger = single { it.coin.id == coinId }.stakeAmount

    private fun List<com.vultisig.wallet.data.blockchain.model.StakingDetails>.aprForCoin(
        coinId: String
    ): Double? = single { it.coin.id == coinId }.apr

    private companion object {
        const val ADDRESS = "thor1abc"
        const val VAULT_ID = "vault-1"
    }
}
