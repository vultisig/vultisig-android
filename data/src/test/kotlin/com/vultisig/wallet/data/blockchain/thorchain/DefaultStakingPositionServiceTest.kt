package com.vultisig.wallet.data.blockchain.thorchain

import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.api.models.cosmos.CosmosBalance
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.repositories.StakingDetailsRepository
import io.mockk.coEvery
import io.mockk.mockk
import java.math.BigInteger
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class DefaultStakingPositionServiceTest {

    private val thorChainApi: ThorChainApi = mockk()
    private val stakingDetailsRepository: StakingDetailsRepository = mockk(relaxed = true)

    private val service = DefaultStakingPositionService(thorChainApi, stakingDetailsRepository)

    @Test
    fun `reads the ybRUNE receipt balance as its own position`() = runTest {
        // ybRUNE is no longer a wallet token, so this read is the only thing that surfaces a
        // bonded bRUNE position anywhere in the app.
        givenBalances(Coins.ThorChain.ybRUNE.contractAddress to "523400000000")

        val positions = service.getStakingDetailsFromNetwork(ADDRESS)

        assertEquals(
            BigInteger("523400000000"),
            positions.forCoin(Coins.ThorChain.ybRUNE.contractAddress),
        )
    }

    @Test
    fun `reads each supported receipt independently`() = runTest {
        givenBalances(
            Coins.ThorChain.sTCY.contractAddress to "100000000",
            Coins.ThorChain.ybRUNE.contractAddress to "250000000",
        )

        val positions = service.getStakingDetailsFromNetwork(ADDRESS)

        assertEquals(
            BigInteger("100000000"),
            positions.forCoin(Coins.ThorChain.sTCY.contractAddress),
        )
        assertEquals(
            BigInteger("250000000"),
            positions.forCoin(Coins.ThorChain.ybRUNE.contractAddress),
        )
        // A denom the wallet holds nothing of still reports, as zero, so a card can settle.
        assertEquals(BigInteger.ZERO, positions.forCoin(Coins.ThorChain.yRUNE.contractAddress))
    }

    @Test
    fun `the bRUNE bond token is not read as a position`() = runTest {
        // Liquid bRUNE is a spendable wallet balance; only the receipt it mints is a position.
        // Counting it here would show unbonded bRUNE as staked, and double it in the DeFi total.
        givenBalances(Coins.ThorChain.bRUNE.contractAddress to "900000000")

        val positions = service.getStakingDetailsFromNetwork(ADDRESS)

        assertEquals(
            emptyList<String>(),
            positions
                .map { it.coin.contractAddress }
                .filter { it == Coins.ThorChain.bRUNE.contractAddress },
        )
    }

    @Test
    fun `getReceiptBalance reads one receipt off the same denom the position is built from`() =
        runTest {
            // The unbond form's ceiling and the clamp that sizes the execute both come through
            // here, so all three can only agree while they match on the one contract address.
            givenBalances(
                "rune" to "2000000000",
                Coins.ThorChain.ybRUNE.contractAddress to "523400000000",
            )

            assertEquals(
                BigInteger("523400000000"),
                service.getReceiptBalance(ADDRESS, Coins.ThorChain.ybRUNE),
            )
        }

    @Test
    fun `getReceiptBalance reads a denom the bank omits as a genuine zero`() = runTest {
        // The bank drops empty balances, so an absent denom is a position of nothing rather than
        // a missing answer.
        givenBalances("rune" to "2000000000")

        assertEquals(BigInteger.ZERO, service.getReceiptBalance(ADDRESS, Coins.ThorChain.ybRUNE))
    }

    @Test
    fun `getReceiptBalance never mistakes the bond token for its receipt`() = runTest {
        // bRUNE and ybRUNE are separate denoms; redeeming against the liquid balance would attach
        // funds the bond contract does not accept.
        givenBalances(Coins.ThorChain.bRUNE.contractAddress to "900000000")

        assertEquals(BigInteger.ZERO, service.getReceiptBalance(ADDRESS, Coins.ThorChain.ybRUNE))
    }

    private fun givenBalances(vararg denoms: Pair<String, String>) {
        coEvery { thorChainApi.getBalance(ADDRESS) } returns
            denoms.map { (denom, amount) -> CosmosBalance(denom = denom, amount = amount) }
    }

    private fun List<com.vultisig.wallet.data.blockchain.model.StakingDetails>.forCoin(
        contractAddress: String
    ): BigInteger = single { it.coin.contractAddress == contractAddress }.stakeAmount

    private companion object {
        const val ADDRESS = "thor1mtqtupwgjwn397w3dx9fqmqgzrjcal5yxz8q7v"
    }
}
