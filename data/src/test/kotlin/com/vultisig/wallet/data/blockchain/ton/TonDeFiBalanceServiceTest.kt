@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.data.blockchain.ton

import com.vultisig.wallet.data.api.chains.ton.TonAccountStakingInfoJson
import com.vultisig.wallet.data.api.chains.ton.TonStakingApi
import com.vultisig.wallet.data.api.chains.ton.TonStakingPoolInfoJson
import com.vultisig.wallet.data.blockchain.model.StakingDetails
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.repositories.StakingDetailsRepository
import com.vultisig.wallet.data.utils.NetworkException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

internal class TonDeFiBalanceServiceTest {

    private val api: TonStakingApi = mockk(relaxed = true)
    private val repo: StakingDetailsRepository = mockk(relaxed = true)
    private val service = TonDeFiBalanceService(api, repo)

    private val address = "EQself"
    private val vaultId = "vault-1"

    @Test
    fun `staked balance is the largest pool's amount plus pending deposit`() = runTest {
        coEvery { api.getNominatorPools(address) } returns
            listOf(
                position(pool = "0:small", amount = 10, pendingDeposit = 0),
                position(pool = "0:big", amount = 50_000_000_000, pendingDeposit = 800_000_000),
            )
        coEvery { api.getStakingPool("0:big") } returns TonStakingPoolInfoJson(apy = 5.0)
        coEvery { repo.getStakingDetailsByCoindId(vaultId, Coins.Ton.TON.id) } returns null

        val result = service.getRemoteDeFiBalance(address, vaultId)

        val balance = result.single().balances.single()
        assertEquals(Coins.Ton.TON, balance.coin)
        assertEquals(BigInteger.valueOf(50_800_000_000), balance.amount)
        coVerify { repo.saveStakingDetails(vaultId, match { it.stakeAmount == balance.amount }) }
    }

    @Test
    fun `APY lookup failure still surfaces the staked position`() = runTest {
        coEvery { api.getNominatorPools(address) } returns
            listOf(position(pool = "0:big", amount = 50_000_000_000, pendingDeposit = 0))
        coEvery { api.getStakingPool(any()) } throws NetworkException(500, "boom")

        val result = service.getRemoteDeFiBalance(address, vaultId)

        assertEquals(BigInteger.valueOf(50_000_000_000), result.single().balances.single().amount)
    }

    @Test
    fun `network failure falls back to the cached stake instead of erasing it`() = runTest {
        coEvery { api.getNominatorPools(address) } throws NetworkException(503, "down")
        coEvery { repo.getStakingDetailsByCoindId(vaultId, Coins.Ton.TON.id) } returns
            stakingDetails(BigInteger.valueOf(42_000_000_000))

        val result = service.getRemoteDeFiBalance(address, vaultId)

        assertEquals(BigInteger.valueOf(42_000_000_000), result.single().balances.single().amount)
    }

    @Test
    fun `no positions yields an empty balance`() = runTest {
        coEvery { api.getNominatorPools(address) } returns emptyList()

        assertEquals(emptyList(), service.getRemoteDeFiBalance(address, vaultId))
    }

    private fun position(pool: String, amount: Long, pendingDeposit: Long) =
        TonAccountStakingInfoJson(pool = pool, amount = amount, pendingDeposit = pendingDeposit)

    private fun stakingDetails(amount: BigInteger) =
        StakingDetails(
            id = "ton",
            coin = Coins.Ton.TON,
            stakeAmount = amount,
            apr = null,
            estimatedRewards = null,
            nextPayoutDate = null,
            rewards = null,
            rewardsCoin = null,
        )
}
