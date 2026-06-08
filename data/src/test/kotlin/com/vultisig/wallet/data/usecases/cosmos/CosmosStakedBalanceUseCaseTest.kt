package com.vultisig.wallet.data.usecases.cosmos

import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosDelegation
import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosDelegatorReward
import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosDelegatorRewards
import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosStakingCoin
import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosStakingService
import com.vultisig.wallet.data.models.Chain
import io.mockk.coEvery
import io.mockk.mockk
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class CosmosStakedBalanceUseCaseTest {

    private lateinit var service: CosmosStakingService
    private lateinit var useCase: CosmosStakedBalanceUseCase

    private val address = "terra1example"

    @BeforeEach
    fun setUp() {
        service = mockk()
        useCase = CosmosStakedBalanceUseCaseImpl(service)
    }

    @Test
    fun `sums delegated balances plus floored bond-denom rewards`() = runTest {
        coEvery { service.fetchDelegations(Chain.TerraClassic, address) } returns
            listOf(
                delegation(amount = "70000000000", denom = "uluna"),
                delegation(amount = "4729000000", denom = "uluna"),
            )
        coEvery { service.fetchDelegatorRewards(Chain.TerraClassic, address) } returns
            rewards(
                "val1" to listOf(CosmosStakingCoin(denom = "uluna", amount = "123456.789")),
                // legacy stability-tax denom must be ignored
                "val2" to listOf(CosmosStakingCoin(denom = "uusd", amount = "999999999")),
            )

        val result = useCase(Chain.TerraClassic, address)

        // 70000000000 + 4729000000 + floor(123456.789)
        assertEquals(BigInteger("74729123456"), result)
    }

    @Test
    fun `returns zero for a non-staking chain without hitting the network`() = runTest {
        assertEquals(BigInteger.ZERO, useCase(Chain.Ethereum, address))
    }

    @Test
    fun `degrades to delegated-only when the rewards read fails`() = runTest {
        coEvery { service.fetchDelegations(Chain.Terra, address) } returns
            listOf(delegation(amount = "5000000", denom = "uluna"))
        coEvery { service.fetchDelegatorRewards(Chain.Terra, address) } throws
            RuntimeException("LCD 503")

        assertEquals(BigInteger("5000000"), useCase(Chain.Terra, address))
    }

    @Test
    fun `degrades to the last-known snapshot when the delegations read fails`() = runTest {
        coEvery { service.fetchDelegations(Chain.Terra, address) } returns
            listOf(delegation(amount = "8000000", denom = "uluna"))
        coEvery { service.fetchDelegatorRewards(Chain.Terra, address) } returns
            CosmosDelegatorRewards(rewards = emptyList(), total = emptyList())

        // Prime the snapshot with a successful live read.
        assertEquals(BigInteger("8000000"), useCase(Chain.Terra, address))

        // A later delegations failure keeps the last-known value instead of zeroing it.
        coEvery { service.fetchDelegations(Chain.Terra, address) } throws
            RuntimeException("LCD 503")
        assertEquals(BigInteger("8000000"), useCase(Chain.Terra, address))
    }

    @Test
    fun `cached returns zero before any live read then the last live total`() = runTest {
        assertEquals(BigInteger.ZERO, useCase.cached(Chain.TerraClassic, address))

        coEvery { service.fetchDelegations(Chain.TerraClassic, address) } returns
            listOf(delegation(amount = "12345", denom = "uluna"))
        coEvery { service.fetchDelegatorRewards(Chain.TerraClassic, address) } returns
            CosmosDelegatorRewards(rewards = emptyList(), total = emptyList())
        useCase(Chain.TerraClassic, address)

        assertEquals(BigInteger("12345"), useCase.cached(Chain.TerraClassic, address))
    }

    private fun delegation(amount: String, denom: String) =
        CosmosDelegation(
            validatorAddress = "valoper",
            balance = CosmosStakingCoin(denom = denom, amount = amount),
            shares = amount,
        )

    private fun rewards(vararg entries: Pair<String, List<CosmosStakingCoin>>) =
        CosmosDelegatorRewards(
            rewards =
                entries.map { (validator, coins) ->
                    CosmosDelegatorReward(validatorAddress = validator, reward = coins)
                },
            total = emptyList(),
        )
}
