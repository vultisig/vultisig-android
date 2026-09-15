package com.vultisig.wallet.data.blockchain.cosmos.staking

import com.vultisig.wallet.data.testutils.MockHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TestTimeSource
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/** Mirrors iOS `KeybaseAvatarServiceTests.swift`. */
class KeybaseAvatarServiceTests {

    private val avatarJson =
        """
        {"status":{"code":0},"them":[{"pictures":{"primary":{"url":"https://keybase.io/avatar.jpg"}}}]}
        """
            .trimIndent()

    private val noAvatarJson = """{"status":{"code":0},"them":null}"""

    @Test
    fun `resolves the primary picture url`() = runTest {
        val service =
            KeybaseAvatarServiceImpl(
                MockHttpClient.respondingWith(HttpStatusCode.OK, avatarJson),
                TestTimeSource(),
            )
        assertEquals("https://keybase.io/avatar.jpg", service.avatarUrl("1234567890ABCDEF"))
    }

    @Test
    fun `returns null when identity is empty`() = runTest {
        val service =
            KeybaseAvatarServiceImpl(
                MockHttpClient.respondingWith(HttpStatusCode.OK, avatarJson),
                TestTimeSource(),
            )
        assertNull(service.avatarUrl("   "))
    }

    @Test
    fun `returns null when them is null`() = runTest {
        val service =
            KeybaseAvatarServiceImpl(
                MockHttpClient.respondingWith(HttpStatusCode.OK, noAvatarJson),
                TestTimeSource(),
            )
        assertNull(service.avatarUrl("1234567890ABCDEF"))
    }

    @Test
    fun `caches the result within the TTL`() = runTest {
        val calls = AtomicInteger(0)
        val jsonHeaders =
            headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
        // The service reads via bodyAsText() + manual JSON — no ContentNegotiation needed.
        val client =
            io.ktor.client.HttpClient(
                MockEngine {
                    calls.incrementAndGet()
                    respond(content = avatarJson, status = HttpStatusCode.OK, headers = jsonHeaders)
                }
            )
        val timeSource = TestTimeSource()
        val service = KeybaseAvatarServiceImpl(client, timeSource)
        service.avatarUrl("ID1")
        timeSource += 30.minutes // < 1h TTL
        service.avatarUrl("ID1")
        assertEquals(1, calls.get())
    }

    @Test
    fun `negative result is cached so a missing avatar does not refetch`() = runTest {
        val calls = AtomicInteger(0)
        val jsonHeaders =
            headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
        val client =
            io.ktor.client.HttpClient(
                MockEngine {
                    calls.incrementAndGet()
                    respond(
                        content = noAvatarJson,
                        status = HttpStatusCode.OK,
                        headers = jsonHeaders,
                    )
                }
            )
        val service = KeybaseAvatarServiceImpl(client, TestTimeSource())
        assertNull(service.avatarUrl("ID1"))
        assertNull(service.avatarUrl("ID1"))
        assertEquals(1, calls.get())
    }
}
