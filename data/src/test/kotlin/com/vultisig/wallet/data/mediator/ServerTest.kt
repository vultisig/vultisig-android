package com.vultisig.wallet.data.mediator

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class ServerTest {
    @Test
    fun `local authentication is bound to the ceremony and authenticated fields`() {
        val key = "ab".repeat(32)
        val auth = assertNotNull(LocalMediatorAuth.create("session-1", key))
        val other = assertNotNull(LocalMediatorAuth.create("session-2", key))
        val body = "first".encodeToByteArray()
        val digest = LocalMediatorAuth.digest(body)
        val nonce = auth.newNonce()
        val tag = auth.requestTag("POST", "/session-1", null, 1000, nonce, digest)

        assertTrue(auth.matches(tag, auth.requestTag("POST", "/session-1", null, 1000, nonce, digest)))
        assertNotEquals(tag, other.requestTag("POST", "/session-1", null, 1000, nonce, digest))
        assertNotEquals(tag, auth.requestTag("POST", "/session-1", "other", 1000, nonce, digest))
        assertNotEquals(tag, auth.requestTag("GET", "/session-1", null, 1000, nonce, digest))
        assertNotEquals(
            tag,
            auth.requestTag("POST", "/session-1", null, 1000, nonce, LocalMediatorAuth.digest(byteArrayOf())),
        )
        val response = auth.responseTag(tag, 200, body)
        assertFalse(auth.matches(response, auth.responseTag(tag, 201, body)))
        assertFalse(auth.matches(response, auth.responseTag(tag, 200, byteArrayOf())))
    }

    @Test
    fun `replay window rejects reused and expired request nonces`() {
        val auth = assertNotNull(LocalMediatorAuth.create("session-1", "ab".repeat(32)))
        val nonce = auth.newNonce()
        val replay = MediatorReplayWindow()
        assertTrue(replay.accept(1000L, nonce, 1000L))
        assertFalse(replay.accept(1000L, nonce, 1000L))
        assertFalse(replay.accept(1000L, auth.newNonce(), 1_000_000L))
        assertFalse(replay.accept(1_000_000L, auth.newNonce(), 1000L))
    }

    @Test
    fun `authenticated origins must be numeric LAN addresses on the mediator port`() {
        val auth = assertNotNull(LocalMediatorAuth.create("session-1", "ab".repeat(32)))
        val origin = "http://127.0.0.1:18080"
        try {
            assertTrue(LocalMediatorCredentials.bind(origin, auth))
            assertEquals(auth, LocalMediatorCredentials.get(origin)?.auth)
            assertFalse(LocalMediatorCredentials.bind("http://example.org:18080", auth))
            assertFalse(LocalMediatorCredentials.bind("http://192.168.1.1:18081", auth))
            assertFalse(LocalMediatorCredentials.bind("http://192.168.1.1:18080/path", auth))
        } finally {
            LocalMediatorCredentials.clear(origin, auth.sessionId)
        }
        assertNull(LocalMediatorCredentials.get(origin))
    }

    @Test
    fun `cache quotas apply to complete writes and clearing releases the budget`() {
        val cache = MediatorCache(maxBytes = 512, maxEntries = 2)
        val first = MediatorCacheKey("message", "one", "alice", "ecdsa")
        val second = MediatorCacheKey("message", "two", "alice", "ecdsa")
        val third = MediatorCacheKey("message", "three", "bob", null)

        assertTrue(cache.add(listOf(first), "one"))
        assertFalse(cache.add(listOf(second, third), "two"))
        assertNull(cache[second])
        assertNull(cache[third])
        assertFalse(cache.add(listOf(first), "large".repeat(100)))
        assertEquals("one", cache[first])
        assertTrue(cache.add(listOf(second), "two"))

        assertEquals(listOf("one", "two"), cache.matching { it.participant == "alice" })
        cache.remove(first)
        assertTrue(cache.add(listOf(third), "three"))
        cache.clear()
        assertNull(cache[second])
        assertTrue(cache.add(listOf(first), "again"))
    }
}
