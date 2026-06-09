package com.vultisig.wallet.data.blockchain.cosmos.staking

import com.vultisig.wallet.data.blockchain.model.StakingDetails
import com.vultisig.wallet.data.blockchain.model.StakingDetails.Companion.generateId
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.repositories.StakingDetailsRepository
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.mockk
import io.mockk.slot
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

internal class CosmosStakingDeFiBalanceServiceTest {

    private val stakingService = mockk<CosmosStakingService>()
    private val stakingDetailsRepository = mockk<StakingDetailsRepository>(relaxed = true)

    private val service = CosmosStakingDeFiBalanceService(stakingService, stakingDetailsRepository)

    @Test
    fun `staked total is delegations plus bond-denom rewards, excluding other denoms`() = runTest {
        coEvery { stakingService.fetchDelegations(Chain.TerraClassic, ADDRESS) } returns
            listOf(delegation("100"), delegation("50"))
        coEvery { stakingService.fetchDelegatorRewards(Chain.TerraClassic, ADDRESS) } returns
            rewards(
                // bond denom (uluna), fractional cosmos.Dec floored to whole base units -> 7
                coin("uluna", "7.987654321"),
                // legacy stability-tax denom must NOT inflate the native staked total
                coin("uusd", "1000"),
            )
        coEvery { stakingDetailsRepository.getStakingDetailsByCoindId(VAULT_ID, any()) } returns
            null

        val result = service.getRemoteDeFiBalance(Chain.TerraClassic, ADDRESS, VAULT_ID)

        // 100 + 50 delegated + 7 floored bond-denom rewards = 157
        val balance = result.single().balances.single()
        assertEquals(BigInteger.valueOf(157), balance.amount)
        assertEquals(Coins.TerraClassic.LUNC.id, balance.coin.id)
    }

    @Test
    fun `failed rewards read still reports the delegated total`() = runTest {
        coEvery { stakingService.fetchDelegations(Chain.TerraClassic, ADDRESS) } returns
            listOf(delegation("100"))
        coEvery { stakingService.fetchDelegatorRewards(Chain.TerraClassic, ADDRESS) } throws
            RuntimeException("LCD down")
        coEvery { stakingDetailsRepository.getStakingDetailsByCoindId(VAULT_ID, any()) } returns
            null

        val result = service.getRemoteDeFiBalance(Chain.TerraClassic, ADDRESS, VAULT_ID)

        assertEquals(BigInteger.valueOf(100), result.single().balances.single().amount)
    }

    @Test
    fun `the persisted staked total includes rewards so the cached emit matches the live emit`() =
        runTest {
            coEvery { stakingService.fetchDelegations(Chain.TerraClassic, ADDRESS) } returns
                listOf(delegation("100"))
            coEvery { stakingService.fetchDelegatorRewards(Chain.TerraClassic, ADDRESS) } returns
                rewards(coin("uluna", "5"))
            coEvery { stakingDetailsRepository.getStakingDetailsByCoindId(VAULT_ID, any()) } returns
                null
            val saved = slot<com.vultisig.wallet.data.blockchain.model.StakingDetails>()
            coJustRun { stakingDetailsRepository.saveStakingDetails(VAULT_ID, capture(saved)) }

            service.getRemoteDeFiBalance(Chain.TerraClassic, ADDRESS, VAULT_ID)

            assertEquals(BigInteger.valueOf(105), saved.captured.stakeAmount)
        }

    @Test
    fun `persistStakedTotal writes the supplied total for the chain native coin`() = runTest {
        // The staking-positions screen hands its freshly-fetched delegated + rewards total here so
        // the cached DeFi read reflects it on next open (#4815).
        coEvery { stakingDetailsRepository.getStakingDetailsByCoindId(VAULT_ID, any()) } returns
            null
        val saved = slot<com.vultisig.wallet.data.blockchain.model.StakingDetails>()
        coJustRun { stakingDetailsRepository.saveStakingDetails(VAULT_ID, capture(saved)) }

        service.persistStakedTotal(Chain.TerraClassic, VAULT_ID, BigInteger.valueOf(4_250_000))

        assertEquals(BigInteger.valueOf(4_250_000), saved.captured.stakeAmount)
        assertEquals(Coins.TerraClassic.LUNC.id, saved.captured.coin.id)
    }

    @Test
    fun `persistStakedTotal updates the cache when a full unbond drops the total to zero`() =
        runTest {
            // Full unbond: the positions VM persists a zero total. The cache already holds the old
            // non-zero stake, so the differing-amount branch must fire an update (not a save) to
            // clear the stale DeFi balance (#4815).
            coEvery { stakingDetailsRepository.getStakingDetailsByCoindId(VAULT_ID, any()) } returns
                StakingDetails(
                    id = Coins.TerraClassic.LUNC.generateId(),
                    coin = Coins.TerraClassic.LUNC,
                    stakeAmount = BigInteger.valueOf(4_250_000),
                    apr = null,
                    estimatedRewards = null,
                    nextPayoutDate = null,
                    rewards = null,
                    rewardsCoin = null,
                )
            val updated = slot<StakingDetails>()
            coJustRun { stakingDetailsRepository.updateStakingDetails(VAULT_ID, capture(updated)) }

            service.persistStakedTotal(Chain.TerraClassic, VAULT_ID, BigInteger.ZERO)

            assertEquals(BigInteger.ZERO, updated.captured.stakeAmount)
            io.mockk.coVerify(exactly = 0) {
                stakingDetailsRepository.saveStakingDetails(any(), any())
            }
        }

    @Test
    fun `persistStakedTotal is a no-op when the cached total already matches`() = runTest {
        // No write when nothing changed — avoids churning the cache on every silent resume reload.
        coEvery { stakingDetailsRepository.getStakingDetailsByCoindId(VAULT_ID, any()) } returns
            StakingDetails(
                id = Coins.TerraClassic.LUNC.generateId(),
                coin = Coins.TerraClassic.LUNC,
                stakeAmount = BigInteger.valueOf(4_250_000),
                apr = null,
                estimatedRewards = null,
                nextPayoutDate = null,
                rewards = null,
                rewardsCoin = null,
            )

        service.persistStakedTotal(Chain.TerraClassic, VAULT_ID, BigInteger.valueOf(4_250_000))

        io.mockk.coVerify(exactly = 0) {
            stakingDetailsRepository.saveStakingDetails(any(), any())
            stakingDetailsRepository.updateStakingDetails(any(), any())
        }
    }

    @Test
    fun `persistStakedTotal is a no-op for a non-staking chain`() = runTest {
        // Bitcoin has no cosmos native coin; the call must not attempt a write.
        service.persistStakedTotal(Chain.Bitcoin, VAULT_ID, BigInteger.valueOf(100))

        io.mockk.coVerify(exactly = 0) { stakingDetailsRepository.saveStakingDetails(any(), any()) }
    }

    @Test
    fun `failed delegations read blanks the balance rather than throwing`() = runTest {
        coEvery { stakingService.fetchDelegations(Chain.TerraClassic, ADDRESS) } throws
            RuntimeException("LCD down")

        val result = service.getRemoteDeFiBalance(Chain.TerraClassic, ADDRESS, VAULT_ID)

        assertTrue(result.isEmpty())
    }

    private fun delegation(amount: String) =
        CosmosDelegation(
            validatorAddress = "terravaloper1",
            balance = CosmosStakingCoin(denom = "uluna", amount = amount),
            shares = amount,
        )

    private fun coin(denom: String, amount: String) =
        CosmosStakingCoin(denom = denom, amount = amount)

    private fun rewards(vararg coins: CosmosStakingCoin) =
        CosmosDelegatorRewards(
            rewards =
                listOf(
                    CosmosDelegatorReward(
                        validatorAddress = "terravaloper1",
                        reward = coins.toList(),
                    )
                ),
            total = coins.toList(),
        )

    private companion object {
        const val ADDRESS = "terra1abc"
        const val VAULT_ID = "vault-1"
    }
}
