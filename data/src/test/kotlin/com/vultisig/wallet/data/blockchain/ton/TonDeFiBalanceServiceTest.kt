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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class TonDeFiBalanceServiceTest {

    private val api: TonStakingApi = mockk(relaxed = true)
    private val liquid: TonLiquidStakingService = mockk()
    private val repo: StakingDetailsRepository = mockk(relaxed = true)
    private val service = TonDeFiBalanceService(api, liquid, repo)

    private val address = "EQself"
    private val vaultId = "vault-1"

    @BeforeEach
    fun setUp() {
        // Default: no Tonstakers position. Individual tests override.
        coEvery { liquid.getPosition(address) } returns
            TonLiquidPosition(tsTonBalance = BigInteger.ZERO, jettonWalletAddress = null)
    }

    @Test
    fun `a Tonstakers position is its own balance alongside the nominator stake`() = runTest {
        coEvery { api.getNominatorPools(address) } returns
            listOf(position(pool = "0:big", amount = 50_000_000_000, pendingDeposit = 0))
        coEvery { api.getStakingPool("0:big") } returns TonStakingPoolInfoJson(apy = 5.0)
        coEvery { liquid.getPosition(address) } returns
            TonLiquidPosition(
                tsTonBalance = BigInteger.valueOf(4_300_000_000),
                jettonWalletAddress = "EQjettonWallet",
            )

        val balances = service.getRemoteDeFiBalance(address, vaultId).single().balances

        assertEquals(2, balances.size)
        assertEquals(
            BigInteger.valueOf(50_000_000_000),
            balances.first { it.coin == Coins.Ton.TON }.amount,
        )
        // Reported in tsTON, so the row prices it against the tsTON rate rather than TON's.
        assertEquals(
            BigInteger.valueOf(4_300_000_000),
            balances.first { it.coin == Coins.Ton.TSTON }.amount,
        )
    }

    @Test
    fun `a liquid-only holder still gets a position`() = runTest {
        coEvery { api.getNominatorPools(address) } returns emptyList()
        coEvery { liquid.getPosition(address) } returns
            TonLiquidPosition(
                tsTonBalance = BigInteger.valueOf(4_300_000_000),
                jettonWalletAddress = "EQjettonWallet",
            )

        val balance = service.getRemoteDeFiBalance(address, vaultId).single().balances.single()

        assertEquals(Coins.Ton.TSTON, balance.coin)
        assertEquals(BigInteger.valueOf(4_300_000_000), balance.amount)
    }

    @Test
    fun `a failed Tonstakers read does not cost the nominator position its row`() = runTest {
        coEvery { api.getNominatorPools(address) } returns
            listOf(position(pool = "0:big", amount = 50_000_000_000, pendingDeposit = 0))
        coEvery { api.getStakingPool("0:big") } returns TonStakingPoolInfoJson(apy = 5.0)
        coEvery { liquid.getPosition(address) } throws RuntimeException("tonapi down")

        val balance = service.getRemoteDeFiBalance(address, vaultId).single().balances.single()

        assertEquals(Coins.Ton.TON, balance.coin)
        assertEquals(BigInteger.valueOf(50_000_000_000), balance.amount)
    }

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
        // tonapi `apy` is a percentage (5.0 = 5%); it must persist as a fraction (0.05) since the
        // DeFi screen's formatPercentage multiplies by 100.
        coVerify {
            repo.saveStakingDetails(
                vaultId,
                match { it.stakeAmount == balance.amount && it.apr == 0.05 },
            )
        }
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
    fun `a failed nominator read keeps the Tonstakers position it already had`() = runTest {
        coEvery { api.getNominatorPools(address) } throws NetworkException(503, "down")
        coEvery { repo.getStakingDetailsByCoindId(vaultId, Coins.Ton.TON.id) } returns
            stakingDetails(BigInteger.valueOf(42_000_000_000))
        coEvery { liquid.getPosition(address) } returns
            TonLiquidPosition(
                tsTonBalance = BigInteger.valueOf(4_300_000_000),
                jettonWalletAddress = "EQjettonWallet",
            )

        val balances = service.getRemoteDeFiBalance(address, vaultId).single().balances

        assertEquals(2, balances.size)
        assertEquals(
            BigInteger.valueOf(42_000_000_000),
            balances.first { it.coin == Coins.Ton.TON }.amount,
        )
        assertEquals(
            BigInteger.valueOf(4_300_000_000),
            balances.first { it.coin == Coins.Ton.TSTON }.amount,
        )
    }

    @Test
    fun `liquid-staking position is excluded and persisted as zero`() = runTest {
        coEvery { api.getNominatorPools(address) } returns
            listOf(position(pool = "0:tonstakers", amount = 50_000_000_000, pendingDeposit = 0))
        coEvery { api.getStakingPool("0:tonstakers") } returns
            TonStakingPoolInfoJson(
                apy = 5.0,
                implementation = TonNominatorPool.IMPLEMENTATION_LIQUID_TF,
            )
        coEvery { repo.getStakingDetailsByCoindId(vaultId, Coins.Ton.TON.id) } returns null

        val result = service.getRemoteDeFiBalance(address, vaultId)

        assertEquals(emptyList(), result)
        coVerify { repo.saveStakingDetails(vaultId, match { it.stakeAmount == BigInteger.ZERO }) }
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
