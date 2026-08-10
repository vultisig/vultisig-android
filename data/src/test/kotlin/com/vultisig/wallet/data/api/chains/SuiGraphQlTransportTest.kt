package com.vultisig.wallet.data.api.chains

import com.vultisig.wallet.data.testutils.MockHttpClient
import com.vultisig.wallet.data.utils.NetworkException
import io.ktor.http.HttpStatusCode
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test

/**
 * Failover behaviour for [SuiGraphQlTransport] (issue #5506).
 *
 * The Sui transport had a single hardcoded host and no fallback, so one bad endpoint took every Sui
 * read and broadcast down with it. Failover is deliberately scoped to transport-level faults: a
 * populated `errors` array is the node's considered answer to a well-formed request, and replaying
 * it against every remaining host only delays the same message.
 */
class SuiGraphQlTransportTest {

    private val endpoints = listOf("https://primary.test/graphql", "https://secondary.test/graphql")

    private val successBody = """{"data":{"epoch":{"referenceGasPrice":"100"}}}"""

    @Test
    fun `falls over to the next endpoint when the first answers 5xx`() = runTest {
        val client =
            MockHttpClient.respondingWithSequence(
                HttpStatusCode.InternalServerError to "upstream down",
                HttpStatusCode.OK to successBody,
            )

        val data = SuiGraphQlTransport(client, endpoints).query(QUERY)

        assertEquals(
            "100",
            data["epoch"]?.jsonObject?.get("referenceGasPrice")?.jsonPrimitive?.content,
        )
    }

    // The 5xx case above already covers recovery; what is pinned here is that an unreachable host
    // is retried at all — every endpoint must be attempted before the failure is reported.
    @Test
    fun `attempts every endpoint when all are unreachable`() = runTest {
        var call = 0
        val failing = MockHttpClient.throwing(IOException("connection reset")) { call++ }

        assertFailsWith<NetworkException> { SuiGraphQlTransport(failing, endpoints).query(QUERY) }

        assertEquals(endpoints.size, call)
    }

    // A 4xx is deterministic — the node has judged these exact bytes, so replaying them against the
    // remaining hosts would only delay the same answer. The second host must never be asked.
    @Test
    fun `does not fail over on a 4xx`() = runTest {
        val client =
            MockHttpClient.respondingWithSequence(
                HttpStatusCode.BadRequest to "malformed document",
                HttpStatusCode.OK to successBody,
            )

        // If the transport retried, the second (successful) response would be returned instead.
        val e =
            assertFailsWith<NetworkException> {
                SuiGraphQlTransport(client, endpoints).query(QUERY)
            }

        assertEquals(HttpStatusCode.BadRequest.value, e.httpStatusCode)
    }

    // Exhausting the list must report the real transport failure, not a generic "no endpoint"
    // message that hides why every host was skipped.
    @Test
    fun `throws the last transport failure when every endpoint fails`() = runTest {
        val client = MockHttpClient.respondingWith(HttpStatusCode.BadGateway, "bad gateway")

        val e =
            assertFailsWith<NetworkException> {
                SuiGraphQlTransport(client, endpoints).query(QUERY)
            }

        assertEquals(HttpStatusCode.BadGateway.value, e.httpStatusCode)
    }

    // A node refusal is deterministic — every other host would answer the same. Retrying it would
    // only delay the message, so the first `errors` array is raised immediately.
    @Test
    fun `does not fail over on a GraphQL error payload`() = runTest {
        val client =
            MockHttpClient.respondingWithSequence(
                HttpStatusCode.OK to
                    """{"data":null,"errors":[{"message":"invalid address","extensions":{"code":"BAD_USER_INPUT"}}]}""",
                HttpStatusCode.OK to successBody,
            )

        // If the transport retried, the second (successful) response would be returned instead.
        val e =
            assertFailsWith<SuiRpcException> { SuiGraphQlTransport(client, endpoints).query(QUERY) }

        assertEquals("invalid address", e.errorMessage)
        assertEquals("BAD_USER_INPUT", e.code)
    }

    @Test
    fun `joins every GraphQL error message`() = runTest {
        val client =
            MockHttpClient.respondingWith(
                HttpStatusCode.OK,
                """{"data":null,"errors":[{"message":"first"},{"message":"second"}]}""",
            )

        val e =
            assertFailsWith<SuiRpcException> { SuiGraphQlTransport(client, endpoints).query(QUERY) }

        assertTrue(e.errorMessage.contains("first"), e.errorMessage)
        assertTrue(e.errorMessage.contains("second"), e.errorMessage)
    }

    // GraphQL answers HTTP 200 even when it refuses, so a body carrying neither is malformed —
    // returning it as an empty result would read as "you hold nothing".
    @Test
    fun `treats a body with neither data nor errors as a failure`() = runTest {
        val client = MockHttpClient.respondingWith(HttpStatusCode.OK, """{}""")

        val e =
            assertFailsWith<SuiRpcException> { SuiGraphQlTransport(client, endpoints).query(QUERY) }

        assertTrue(e.errorMessage.contains("malformed"), e.errorMessage)
    }

    // Cancellation is not a transport fault — swallowing it would keep hammering the remaining
    // hosts after the caller has navigated away.
    @Test
    fun `propagates cancellation instead of failing over`() = runTest {
        val calls = AtomicInteger()
        val client =
            MockHttpClient.throwing(CancellationException("navigated away")) {
                calls.incrementAndGet()
            }

        assertFailsWith<CancellationException> {
            SuiGraphQlTransport(client, endpoints).query(QUERY)
        }
        assertEquals(1, calls.get())
    }

    @Test
    fun `rejects an empty endpoint list`() {
        val client = MockHttpClient.respondingWith(HttpStatusCode.OK, successBody)

        assertFailsWith<IllegalArgumentException> { SuiGraphQlTransport(client, emptyList()) }
    }

    // The shipped configuration must point at a live Sui GraphQL host, not the retired JSON-RPC
    // one — the whole point of #5506.
    @Test
    fun `ships a GraphQL endpoint, not the retired JSON-RPC host`() {
        assertTrue(SUI_GRAPHQL_ENDPOINTS.isNotEmpty())
        assertTrue(SUI_GRAPHQL_ENDPOINTS.all { it.contains("graphql") }, "$SUI_GRAPHQL_ENDPOINTS")
        assertTrue(
            SUI_GRAPHQL_ENDPOINTS.none { it.contains("sui-rpc.publicnode.com") },
            "$SUI_GRAPHQL_ENDPOINTS",
        )
    }

    private companion object {
        const val QUERY = "query { epoch { referenceGasPrice } }"
    }
}
