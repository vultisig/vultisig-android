package com.vultisig.wallet.data.blockchain.solana

import com.vultisig.wallet.data.blockchain.model.DeFiBalance
import com.vultisig.wallet.data.blockchain.solana.kamino.KaminoDeFiBalanceService
import com.vultisig.wallet.data.blockchain.solana.staking.SolanaStakingDeFiBalanceService
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import io.mockk.coEvery
import io.mockk.mockk
import java.math.BigInteger
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** Covers the chain total being the sum of Solana's two independent DeFi surfaces. */
internal class SolanaDeFiBalanceServiceTest {

    private lateinit var staking: SolanaStakingDeFiBalanceService
    private lateinit var kamino: KaminoDeFiBalanceService

    @BeforeEach
    fun setUp() {
        staking = mockk(relaxed = true)
        kamino = mockk(relaxed = true)
        coEvery { staking.getRemoteDeFiBalance(any(), any()) } returns emptyList()
        coEvery { kamino.getRemoteDeFiBalance(any(), any()) } returns emptyList()
        coEvery { staking.getCacheDeFiBalance(any(), any()) } returns emptyList()
        coEvery { kamino.getCacheDeFiBalance(any(), any()) } returns emptyList()
    }

    @Test
    fun `staked SOL and a SOL Earn position are added together, not one dropped`() = runTest {
        coEvery { staking.getRemoteDeFiBalance(ADDRESS, VAULT_ID) } returns
            balances(Coins.Solana.SOL to BigInteger("1000000000"))
        coEvery { kamino.getRemoteDeFiBalance(ADDRESS, VAULT_ID) } returns
            balances(Coins.Solana.SOL to BigInteger("500000000"))

        val balance = service().getRemoteDeFiBalance(ADDRESS, VAULT_ID).single().balances.single()

        // The pipeline resolves one balance per coin, so leaving these as two entries would report
        // whichever it matched first as the whole position.
        assertEquals(BigInteger("1500000000"), balance.amount)
        assertEquals(2, balance.positionCount)
    }

    @Test
    fun `a grouped Kamino token keeps its original number of positions`() = runTest {
        coEvery { kamino.getRemoteDeFiBalance(ADDRESS, VAULT_ID) } returns
            balances(Coins.Solana.USDC to BigInteger("250000000")).withPositionCount(2)

        val balance = service().getRemoteDeFiBalance(ADDRESS, VAULT_ID).single().balances.single()

        assertEquals(BigInteger("250000000"), balance.amount)
        assertEquals(2, balance.positionCount)
    }

    @Test
    fun `positions in different tokens each keep their own balance`() = runTest {
        coEvery { staking.getRemoteDeFiBalance(ADDRESS, VAULT_ID) } returns
            balances(Coins.Solana.SOL to BigInteger("1000000000"))
        coEvery { kamino.getRemoteDeFiBalance(ADDRESS, VAULT_ID) } returns
            balances(Coins.Solana.USDC to BigInteger("250000000"))

        val balances = service().getRemoteDeFiBalance(ADDRESS, VAULT_ID).single().balances

        assertEquals(2, balances.size)
        assertEquals(
            BigInteger("1000000000"),
            balances.first { it.coin.id == Coins.Solana.SOL.id }.amount,
        )
        assertEquals(
            BigInteger("250000000"),
            balances.first { it.coin.id == Coins.Solana.USDC.id }.amount,
        )
    }

    @Test
    fun `a vault with only native staking is reported exactly as before`() = runTest {
        coEvery { staking.getRemoteDeFiBalance(ADDRESS, VAULT_ID) } returns
            balances(Coins.Solana.SOL to BigInteger("1000000000"))

        val balance = service().getRemoteDeFiBalance(ADDRESS, VAULT_ID).single().balances.single()

        assertEquals(Coins.Solana.SOL.id, balance.coin.id)
        assertEquals(BigInteger("1000000000"), balance.amount)
    }

    @Test
    fun `a vault holding neither reports nothing at all`() = runTest {
        assertTrue(service().getRemoteDeFiBalance(ADDRESS, VAULT_ID).isEmpty())
    }

    @Test
    fun `the cached read sums both sides too`() = runTest {
        coEvery { staking.getCacheDeFiBalance(ADDRESS, VAULT_ID) } returns
            balances(Coins.Solana.SOL to BigInteger("1000000000"))
        coEvery { kamino.getCacheDeFiBalance(ADDRESS, VAULT_ID) } returns
            balances(Coins.Solana.SOL to BigInteger("500000000"))

        val balance = service().getCacheDeFiBalance(ADDRESS, VAULT_ID).single().balances.single()

        assertEquals(BigInteger("1500000000"), balance.amount)
    }

    private fun service() =
        SolanaDeFiBalanceService(stakingBalanceService = staking, kaminoBalanceService = kamino)

    private fun balances(vararg entries: Pair<Coin, BigInteger>) =
        listOf(
            DeFiBalance(
                chain = Chain.Solana,
                balances = entries.map { (coin, amount) -> DeFiBalance.Balance(coin, amount) },
            )
        )

    private fun List<DeFiBalance>.withPositionCount(positionCount: Int) = map { defiBalance ->
        defiBalance.copy(
            balances =
                defiBalance.balances.map { balance -> balance.copy(positionCount = positionCount) }
        )
    }

    private companion object {
        const val VAULT_ID = "vault-id"
        const val ADDRESS = "9ceRgz579BcfWogs3RE11FKNQaWW7Lmtnev3MXspxUjF"
    }
}
