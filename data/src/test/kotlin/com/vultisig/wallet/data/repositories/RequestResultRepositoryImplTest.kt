package com.vultisig.wallet.data.repositories

import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class RequestResultRepositoryImplTest {

    private val repository = RequestResultRepositoryImpl()

    @Test
    fun `request resolves with response that fired before subscription`() = runTest {
        repository.respond("alpha", "value")

        assertEquals("value", repository.request<String>("alpha"))
    }

    @Test
    fun `request resolves with response that fires after subscription`() = runTest {
        val pending = async { repository.request<String>("alpha") }
        runCurrent()

        repository.respond("alpha", "value")

        assertEquals("value", pending.await())
    }

    @Test
    fun `request ignores responses with a different id`() = runTest {
        repository.respond("other", "stale")
        val pending = async { repository.request<String>("target") }
        runCurrent()

        repository.respond("target", "fresh")

        assertEquals("fresh", pending.await())
    }

    @Test
    fun `concurrent requests with distinct ids resolve independently`() = runTest {
        val first = async { repository.request<String>("a") }
        val second = async { repository.request<Int>("b") }
        runCurrent()

        repository.respond("b", 100)
        repository.respond("a", "alpha")

        assertEquals("alpha", first.await())
        assertEquals(100, second.await())
    }

    @Test
    fun `request returns null when response payload is null`() = runTest {
        repository.respond("alpha", null)

        assertNull(repository.request<String>("alpha"))
    }
}
