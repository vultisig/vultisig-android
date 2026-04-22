package com.vultisig.wallet.data.mediator

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class ServerTest {

    @Test
    fun `filterMessagesByPrefix returns empty when cache is empty`() {
        assertTrue(filterMessagesByPrefix(emptyMap(), "sid-bob-").isEmpty())
    }

    @Test
    fun `filterMessagesByPrefix returns empty when no key matches`() {
        val cache = mapOf<String, Any>("sid-carol-h1" to message("h1"))

        assertTrue(filterMessagesByPrefix(cache, "sid-bob-").isEmpty())
    }

    @Test
    fun `filterMessagesByPrefix returns every matching message`() {
        val m1 = message("h1")
        val m2 = message("h2")
        val cache =
            mapOf<String, Any>(
                "sid-bob-h1" to m1,
                "sid-bob-h2" to m2,
                "sid-carol-h3" to message("h3"),
            )

        assertEquals(setOf(m1, m2), filterMessagesByPrefix(cache, "sid-bob-").toSet())
    }

    @Test
    fun `filterMessagesByPrefix skips non-Message entries that share the prefix`() {
        val m = message("h1")
        val cache =
            mapOf(
                "sid-bob-h1" to m,
                "sid-bob-session" to Session("sid", mutableListOf("bob")),
                "sid-bob-raw" to "arbitrary body",
            )

        assertEquals(listOf(m), filterMessagesByPrefix(cache, "sid-bob-"))
    }

    private fun message(hash: String): Message =
        Message(
            sessionID = "sid",
            from = "alice",
            to = listOf("bob"),
            body = "body-$hash",
            hash = hash,
            sequenceNo = 0,
        )
}
