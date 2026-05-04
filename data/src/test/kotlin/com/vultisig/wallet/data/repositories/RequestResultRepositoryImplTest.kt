@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.data.repositories

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
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

    @Test
    fun `second request for same id does not see previous exchange's response`() = runTest {
        repository.respond("alpha", "first")
        assertEquals("first", repository.request<String>("alpha"))

        val pending = async { repository.request<String>("alpha") }
        runCurrent()
        assertTrue(
            pending.isActive,
            "second request must suspend instead of replaying the previous exchange's value",
        )

        repository.respond("alpha", "second")

        assertEquals("second", pending.await())
    }

    @Test
    fun `concurrent waiters on same id all resolve with the same response`() = runTest {
        val first = async { repository.request<String>("alpha") }
        val second = async { repository.request<String>("alpha") }
        runCurrent()

        repository.respond("alpha", "value")

        assertEquals("value", first.await())
        assertEquals("value", second.await())
    }

    @Test
    fun `cancelled request leaves slot clean for the next request`() = runTest {
        val first = async { repository.request<String>("alpha") }
        runCurrent()
        first.cancelAndJoin()

        val second = async { repository.request<String>("alpha") }
        runCurrent()
        assertTrue(
            second.isActive,
            "cancelling the first request must not poison the slot for the next one",
        )

        repository.respond("alpha", "value")

        assertEquals("value", second.await())
    }

    @Test
    fun `cancelling one of two concurrent waiters does not block the other`() = runTest {
        val first = async { repository.request<String>("alpha") }
        val second = async { repository.request<String>("alpha") }
        runCurrent()
        first.cancelAndJoin()

        repository.respond("alpha", "value")

        assertEquals("value", second.await())
    }

    @Test
    fun `latest orphan response wins when multiple respond before request`() = runTest {
        repository.respond("alpha", "first")
        repository.respond("alpha", "second")

        assertEquals("second", repository.request<String>("alpha"))
    }
}
