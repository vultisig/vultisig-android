package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.api.ThorChainApi
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class ThorMimirRepositoryTest {

    private lateinit var api: ThorChainApi
    private lateinit var repository: ThorMimirRepositoryImpl

    @BeforeEach
    fun setUp() {
        api = mockk()
        repository = ThorMimirRepositoryImpl(api)
    }

    @Test
    fun `clean state returns false for all checks`() = runTest {
        coEvery { api.getMimir() } returns emptyMap()

        assertFalse(repository.isLpPaused("ETH.USDT-0xdac17f958d2ee523a2206206994597c13d831ec7"))
        assertFalse(repository.isLpHalted("ETH"))
    }

    @Test
    fun `global PAUSELP blocks all pools`() = runTest {
        coEvery { api.getMimir() } returns mapOf("PAUSELP" to 1L)

        assertTrue(repository.isLpPaused("BTC.BTC"))
        assertTrue(repository.isLpPaused("ETH.USDT-0xdac"))
    }

    @Test
    fun `per-pool deposit pause key matches the full asset identifier including contract`() =
        runTest {
            coEvery { api.getMimir() } returns
                mapOf("PAUSELPDEPOSIT-ETH-USDT-0XDAC17F958D2EE523A2206206994597C13D831EC7" to 1L)

            assertTrue(repository.isLpPaused("ETH.USDT-0xdac17f958d2ee523a2206206994597c13d831ec7"))
            // Same ticker, different contract — must not match.
            assertFalse(
                repository.isLpPaused("ETH.USDT-0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48")
            )
            assertFalse(repository.isLpPaused("BTC.BTC"))
        }

    @Test
    fun `per-pool deposit pause key matches native pools without contract suffix`() = runTest {
        coEvery { api.getMimir() } returns mapOf("PAUSELPDEPOSIT-BTC-BTC" to 1L)

        assertTrue(repository.isLpPaused("BTC.BTC"))
        assertFalse(repository.isLpPaused("ETH.ETH"))
    }

    @Test
    fun `per-pool deposit pause key matches case-insensitively`() = runTest {
        coEvery { api.getMimir() } returns
            mapOf("pauselpdeposit-eth-usdt-0xdac17f958d2ee523a2206206994597c13d831ec7" to 1L)

        assertTrue(repository.isLpPaused("ETH.USDT-0xdac17f958d2ee523a2206206994597c13d831ec7"))
    }

    @Test
    fun `zero value is treated as off`() = runTest {
        coEvery { api.getMimir() } returns
            mapOf(
                "PAUSELP" to 0L,
                "PAUSELPDEPOSIT-ETH-USDT-0XDAC17F958D2EE523A2206206994597C13D831EC7" to 0L,
            )

        assertFalse(repository.isLpPaused("ETH.USDT-0xdac17f958d2ee523a2206206994597c13d831ec7"))
    }

    @Test
    fun `chain halt key blocks LP on that chain`() = runTest {
        coEvery { api.getMimir() } returns mapOf("HALTETHLP" to 1L)

        assertTrue(repository.isLpHalted("ETH"))
        assertFalse(repository.isLpHalted("BTC"))
    }

    @Test
    fun `HALTxxxCHAIN also halts LP`() = runTest {
        coEvery { api.getMimir() } returns mapOf("HALTBTCCHAIN" to 1L)

        assertTrue(repository.isLpHalted("BTC"))
    }

    @Test
    fun `chain prefix lookup is case-insensitive`() = runTest {
        coEvery { api.getMimir() } returns mapOf("HALTETHLP" to 1L)

        assertTrue(repository.isLpHalted("eth"))
    }

    @Test
    fun `multiple checks within TTL hit the network only once`() = runTest {
        coEvery { api.getMimir() } returns mapOf("PAUSELP" to 1L)

        repository.isLpPaused("ETH.USDT-0xdac")
        repository.isLpHalted("ETH")
        repository.isLpPaused("BTC.BTC")

        coVerify(exactly = 1) { api.getMimir() }
    }
}
