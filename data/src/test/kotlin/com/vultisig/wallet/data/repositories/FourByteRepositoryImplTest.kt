@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.api.FourByteApi
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.fail
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

internal class FourByteRepositoryImplTest {

    private val fourByteApi: FourByteApi = mockk()
    private val repository = FourByteRepositoryImpl(fourByteApi, Json)

    @Test
    fun `decodeFunction resolves common selector offline without hitting the API`() = runTest {
        // ERC-20 approve(address,uint256) — selector 0x095ea7b3
        val memo = "0x095ea7b300000000000000000000000000000000000000000000000000000000deadbeef"

        val signature = repository.decodeFunction(memo)

        assertEquals("approve(address,uint256)", signature)
        coVerify(exactly = 0) { fourByteApi.decodeFunction(any()) }
    }

    @Test
    fun `decodeFunction falls back to API for unknown selectors`() = runTest {
        // 0xdeadbeef is not in the static table.
        val memo = "0xdeadbeef00000000"
        coEvery { fourByteApi.decodeFunction("deadbeef") } returns "someUnknownFunc()"

        val signature = repository.decodeFunction(memo)

        assertEquals("someUnknownFunc()", signature)
        coVerify(exactly = 1) { fourByteApi.decodeFunction("deadbeef") }
    }

    @Test
    fun `decodeFunction returns null for memo shorter than 4 bytes`() = runTest {
        assertNull(repository.decodeFunction("0x12"))
        coVerify(exactly = 0) { fourByteApi.decodeFunction(any()) }
    }

    @Test
    fun `decodeFunction returns null when 0x prefix leaves fewer than 8 hex chars`() = runTest {
        // `0xabcdef` is 8 chars total but only 6 after the 0x prefix is stripped.
        assertNull(repository.decodeFunction("0xabcdef"))
        coVerify(exactly = 0) { fourByteApi.decodeFunction(any()) }
    }

    @Test
    fun `decodeFunction returns null for non-hex selector without hitting the API`() = runTest {
        // `0xZZZZZZZZ` has 8 chars after the prefix but is not valid hex.
        assertNull(repository.decodeFunction("0xZZZZZZZZ"))
        coVerify(exactly = 0) { fourByteApi.decodeFunction(any()) }
    }

    @Test
    fun `decodeFunction returns null when API throws a non-cancellation exception`() = runTest {
        val memo = "0xdeadbeef"
        coEvery { fourByteApi.decodeFunction("deadbeef") } throws RuntimeException("boom")

        assertNull(repository.decodeFunction(memo))
        coVerify(exactly = 1) { fourByteApi.decodeFunction("deadbeef") }
    }

    @Test
    fun `decodeFunction rethrows CancellationException from the API`() = runTest {
        val memo = "0xdeadbeef"
        coEvery { fourByteApi.decodeFunction("deadbeef") } throws CancellationException("cancelled")

        try {
            repository.decodeFunction(memo)
            fail("expected CancellationException to propagate")
        } catch (_: CancellationException) {
            // expected
        }
        coVerify(exactly = 1) { fourByteApi.decodeFunction("deadbeef") }
    }
}
