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
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
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

    private fun givenBalances(
        bonded: BigInteger,
        autoCompound: BigInteger,
        rewards: BigInteger = BigInteger.ZERO,
    ) {
        coEvery { thorChainApi.getRujiStakeBalance(ADDRESS) } returns
            RujiStakeBalances(
                stakeAmount = bonded,
                stakeTicker = "RUJI",
                autoCompoundAmount = autoCompound,
                autoCompoundShares = autoCompound,
                rewardsAmount = rewards,
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

    private companion object {
        const val ADDRESS = "thor1abc"
        const val VAULT_ID = "vault-1"
    }
}
