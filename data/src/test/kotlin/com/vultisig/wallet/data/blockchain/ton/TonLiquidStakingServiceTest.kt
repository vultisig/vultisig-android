package com.vultisig.wallet.data.blockchain.ton

import com.vultisig.wallet.data.api.chains.ton.AddressEntryJson
import com.vultisig.wallet.data.api.chains.ton.JettonWalletJson
import com.vultisig.wallet.data.api.chains.ton.JettonWalletsJson
import com.vultisig.wallet.data.api.chains.ton.TonApi
import com.vultisig.wallet.data.api.chains.ton.TonLiquidPoolDataJson
import com.vultisig.wallet.data.api.chains.ton.TonStakingApi
import com.vultisig.wallet.data.api.chains.ton.TonStakingPoolInfoJson
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * The caching contract. Both reads back a screen that reloads on every resume, so what matters is
 * that a second read inside the window costs nothing, that the gestures which mean "something
 * changed" still reach the chain, and that a failure is never what gets remembered.
 */
internal class TonLiquidStakingServiceTest {

    private val owner = "EQowner"
    private val timeSource = TestTimeSource()
    private val tonApi: TonApi = mockk()
    private val tonStakingApi: TonStakingApi = mockk()

    private val service = TonLiquidStakingService(tonApi, tonStakingApi, { it }, timeSource)

    @Test
    fun `a second position read inside the window does not reach the indexer`() = runTest {
        givenJettonWallet(balance = "4300000000")

        val first = service.getPosition(owner)
        val second = service.getPosition(owner)

        assertEquals(BigInteger.valueOf(4_300_000_000L), first.tsTonBalance)
        assertEquals(first, second)
        coVerify(exactly = 1) { tonApi.getJettonWallet(any(), any()) }
    }

    @Test
    fun `the position is read again once its window passes`() = runTest {
        givenJettonWallet(balance = "4300000000")

        service.getPosition(owner)
        timeSource += 13.seconds
        service.getPosition(owner)

        coVerify(exactly = 2) { tonApi.getJettonWallet(any(), any()) }
    }

    @Test
    fun `a pull to refresh goes past the cache`() = runTest {
        givenJettonWallet(balance = "4300000000")

        service.getPosition(owner)
        service.getPosition(owner, forceRefresh = true)

        coVerify(exactly = 2) { tonApi.getJettonWallet(any(), any()) }
    }

    @Test
    fun `a broadcast drops the position so the next read sees the new balance`() = runTest {
        givenJettonWallet(balance = "4300000000")
        assertEquals(BigInteger.valueOf(4_300_000_000L), service.getPosition(owner).tsTonBalance)

        service.invalidate(owner)
        givenJettonWallet(balance = "8600000000")

        assertEquals(BigInteger.valueOf(8_600_000_000L), service.getPosition(owner).tsTonBalance)
    }

    @Test
    fun `positions are cached per owner`() = runTest {
        givenJettonWallet(balance = "4300000000")

        service.getPosition(owner)
        service.getPosition("EQother")

        coVerify(exactly = 2) { tonApi.getJettonWallet(any(), any()) }
    }

    @Test
    fun `concurrent readers share one request`() = runTest {
        givenJettonWallet(balance = "4300000000")

        val results =
            listOf(
                    async { service.getPosition(owner) },
                    async { service.getPosition(owner) },
                    async { service.getPosition(owner) },
                )
                .awaitAll()

        assertEquals(1, results.distinct().size)
        coVerify(exactly = 1) { tonApi.getJettonWallet(any(), any()) }
    }

    @Test
    fun `the pool state is shared across reads until its own window passes`() = runTest {
        givenPool()

        service.getPoolState()
        service.getPoolState()
        timeSource += 61.seconds
        service.getPoolState()

        coVerify(exactly = 2) { tonStakingApi.getLiquidPoolData(any()) }
    }

    @Test
    fun `a failed pool read is not what gets remembered`() = runTest {
        coEvery { tonStakingApi.getLiquidPoolData(any()) } returns null
        coEvery { tonStakingApi.getStakingPool(any()) } returns null

        assertNull(service.getPoolState())

        givenPool()
        assertNotNull(service.getPoolState())
    }

    @Test
    fun `a pool payload with no rate in it is a failed read`() = runTest {
        // What a tonapi field rename looks like: the call succeeds, every field decodes absent.
        coEvery { tonStakingApi.getLiquidPoolData(any()) } returns TonLiquidPoolDataJson()
        coEvery { tonStakingApi.getStakingPool(any()) } returns null

        assertNull(service.getPoolState())
    }

    @Test
    fun `an unreadable deposit gate on a priced pool stays open`() = runTest {
        // Deliberate: the gate is a governance flag, the contract refuses a closed deposit anyway,
        // and failing closed would turn a decoder drift into a dead stake form.
        coEvery { tonStakingApi.getLiquidPoolData(any()) } returns
            TonLiquidPoolDataJson(
                totalBalance = 116_000_000_000L,
                supply = 100_000_000_000L,
                depositsOpen = null,
            )
        coEvery { tonStakingApi.getStakingPool(any()) } returns null

        assertEquals(true, service.getPoolState()?.isDepositOpen)
    }

    @Test
    fun `the pool state outlives a position window`() = runTest {
        givenJettonWallet(balance = "4300000000")
        givenPool()

        service.getPosition(owner)
        service.getPoolState()
        timeSource += 13.seconds
        service.getPosition(owner)
        service.getPoolState()

        coVerify(exactly = 2) { tonApi.getJettonWallet(any(), any()) }
        coVerify(exactly = 1) { tonStakingApi.getLiquidPoolData(any()) }
    }

    private fun givenJettonWallet(balance: String) {
        coEvery { tonApi.getJettonWallet(any(), any()) } returns
            JettonWalletsJson(
                jettonWallets =
                    listOf(
                        JettonWalletJson(
                            address = "0:jettonwallet",
                            jetton = Tonstakers.TSTON_MASTER_ADDRESS,
                            balance = balance,
                        )
                    ),
                addressBook =
                    mapOf("0:jettonwallet" to AddressEntryJson(userFriendly = "EQjettonwallet")),
            )
    }

    private fun givenPool() {
        coEvery { tonStakingApi.getLiquidPoolData(any()) } returns
            TonLiquidPoolDataJson(totalBalance = 116_000_000_000L, supply = 100_000_000_000L)
        coEvery { tonStakingApi.getStakingPool(any()) } returns
            TonStakingPoolInfoJson(apy = 13.36, minStake = 1_000_000_000L)
    }
}
