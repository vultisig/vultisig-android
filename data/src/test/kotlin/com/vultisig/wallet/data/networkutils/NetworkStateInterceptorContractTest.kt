package com.vultisig.wallet.data.networkutils

import com.vultisig.wallet.data.utils.NetworkException
import com.vultisig.wallet.data.utils.bodyOrThrow
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpCallValidator
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * CONTRACT TESTS FOR NETWORK ERROR HANDLING
 *
 * These tests define the CORRECT behavior a network error handling layer must satisfy.
 *
 * Group 1 (interceptor503_*): Uses the interceptor approach (IOException -> synthetic 503).
 *   -> These tests FAIL, proving the approach violates the contract.
 *
 * Group 2 (httpCallValidator_*): Uses HttpCallValidator (IOException -> NetworkException).
 *   -> These tests PASS, proving the alternative satisfies the contract.
 *
 * The contract requirements:
 * 1. Network failures must be distinguishable from real server errors
 * 2. Network failures must not break response deserialization
 * 3. bodyOrThrow() must report network failures as client-side errors (code 0), not server errors
 * 4. Callers using status-code checks must not confuse network failures with broadcast failures
 * 5. Existing try-catch(Exception) patterns must still catch network errors
 */
class NetworkStateInterceptorContractTest {

    // -- Models mimicking real codebase types (Blockchair.kt) --

    @Serializable
    data class BlockChairDashboardResponse(
        @SerialName("data")
        val data: Map<String, TransactionData>? = null,
        @SerialName("context")
        val context: ContextData,
    )

    @Serializable
    data class TransactionData(val transaction: TransactionInfo? = null)

    @Serializable
    data class TransactionInfo(@SerialName("block_id") val blockId: Int? = null)

    @Serializable
    data class ContextData(@SerialName("state") val state: Int)

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    private val jsonHeaders = headersOf(
        HttpHeaders.ContentType, ContentType.Application.Json.toString()
    )

    // The exact body format produced by NetworkStateInterceptor
    private val syntheticBody = """{"error": "Network failure: Unable to resolve host"}"""

    // A real server 503 body
    private val realServer503Body = """{"error": "Service temporarily unavailable"}"""

    /**
     * Simulates what callers see after NetworkStateInterceptor converts IOException to 503.
     */
    private fun buildInterceptorClient(): HttpClient = HttpClient(MockEngine {
        respond(
            content = syntheticBody,
            status = HttpStatusCode.ServiceUnavailable,
            headers = jsonHeaders,
        )
    }) {
        install(ContentNegotiation) { json(json, ContentType.Any) }
    }

    /**
     * Simulates a REAL server returning 503.
     */
    private fun buildRealServer503Client(): HttpClient = HttpClient(MockEngine {
        respond(
            content = realServer503Body,
            status = HttpStatusCode.ServiceUnavailable,
            headers = jsonHeaders,
        )
    }) {
        install(ContentNegotiation) { json(json, ContentType.Any) }
    }

    /**
     * Uses HttpCallValidator to map IOException -> NetworkException(httpStatusCode=0).
     */
    private fun buildHttpCallValidatorClient(): HttpClient = HttpClient(MockEngine {
        throw java.io.IOException("Unable to resolve host")
    }) {
        install(ContentNegotiation) { json(json, ContentType.Any) }
        install(HttpCallValidator) {
            handleResponseExceptionWithRequest { cause, _ ->
                when (cause) {
                    is java.io.IOException -> throw NetworkException(
                        httpStatusCode = 0,
                        message = "No internet connection",
                        cause = cause,
                    )
                }
            }
        }
    }

    // ================================================================
    // GROUP 1: Interceptor approach — these tests FAIL (contract violated)
    //
    // Each test asserts what SHOULD happen when the phone has no internet.
    // The interceptor approach produces a synthetic 503 instead, breaking
    // every assertion.
    // ================================================================

    @Test
    fun interceptor503_networkFailureMustBeDifferentFromServerError() = runBlocking {
        val interceptorClient = buildInterceptorClient()
        val realServerClient = buildRealServer503Client()

        val networkFailureResponse = interceptorClient.get("https://api.vultisig.com/test")
        val serverErrorResponse = realServerClient.get("https://api.vultisig.com/test")

        // CONTRACT: A caller MUST be able to distinguish "phone has no internet"
        // from "server returned 503". These status codes MUST differ.
        assertNotEquals(
            "Network failure status must differ from real server 503, " +
                    "but both returned ${networkFailureResponse.status}",
            serverErrorResponse.status,
            networkFailureResponse.status,
        )

        interceptorClient.close()
        realServerClient.close()
    }

    @Test
    fun interceptor503_networkFailureMustNotBreakDeserialization() = runBlocking {
        val client = buildInterceptorClient()

        val response = client.get("https://api.vultisig.com/blockchair/litecoin/dashboards/transaction/abc")

        // CONTRACT: When there's no internet, calling body<T>() must throw
        // a NetworkException or IOException — NOT a deserialization error.
        // The caller should see "no internet", not "missing field: context".
        try {
            response.body<BlockChairDashboardResponse>()
            fail("Expected an exception when deserializing during network failure")
        } catch (e: NetworkException) {
            // CORRECT: NetworkException means the caller knows it's a network problem
            assertEquals("Should indicate client-side error", 0, e.httpStatusCode)
        } catch (_: java.io.IOException) {
            // CORRECT: IOException is also an acceptable signal for network failure
        } catch (e: Exception) {
            // WRONG: Any other exception (e.g. SerializationException, MissingFieldException)
            // means the network failure was disguised as a data parsing error.
            fail(
                "Network failure must throw NetworkException or IOException, " +
                        "but threw ${e::class.simpleName}: ${e.message}"
            )
        }

        client.close()
    }

    @Test
    fun interceptor503_bodyOrThrowMustReportClientSideError() = runBlocking {
        val client = buildInterceptorClient()

        val response = client.get("https://api.vultisig.com/solana/")

        // CONTRACT: When there's no internet, bodyOrThrow must produce a
        // NetworkException with httpStatusCode=0 (client-side), not 503.
        try {
            response.bodyOrThrow<String>()
            fail("Expected NetworkException from bodyOrThrow")
        } catch (e: NetworkException) {
            assertEquals(
                "Network failure must have httpStatusCode=0 (client-side), not a server code. " +
                        "Got httpStatusCode=${e.httpStatusCode}",
                0,
                e.httpStatusCode,
            )
        }

        client.close()
    }

    @Test
    fun interceptor503_broadcastCallerMustNotSeeTransactionFailure() = runBlocking {
        val client = buildInterceptorClient()

        val response = client.get("https://api.vultisig.com/blockchair/litecoin/push/transaction")

        // CONTRACT: When there's no internet, the broadcast path should NOT
        // see a non-OK status that looks like a server rejection.
        // Simulates BlockChairApi.broadcastTransaction:
        //   if (response.status != HttpStatusCode.OK) {
        //       error("fail to broadcast transaction: $errorBody")
        //   }
        assertTrue(
            "Network failure must not produce a non-OK HTTP response. " +
                    "Got ${response.status} — callers will treat this as a broadcast rejection " +
                    "and show 'fail to broadcast transaction' instead of 'no internet'.",
            response.status.isSuccess(),
        )

        client.close()
    }

    // ================================================================
    // GROUP 2: HttpCallValidator approach — these tests PASS
    // ================================================================

    @Test
    fun httpCallValidator_networkFailureIsClearlyDistinguishedFromServerError() = runBlocking {
        val validatorClient = buildHttpCallValidatorClient()
        val serverClient = buildRealServer503Client()

        // Network failure -> exception (no response at all)
        val networkException = try {
            validatorClient.get("https://api.vultisig.com/test")
            null
        } catch (e: NetworkException) {
            e
        }

        // Server error -> normal response with 503
        val serverResponse = serverClient.get("https://api.vultisig.com/test")

        // CONTRACT SATISFIED: Network failure is an exception, server error is a response.
        // They are fundamentally different types — impossible to confuse.
        assertNotNull("Network failure must throw an exception", networkException)
        assertEquals(0, networkException!!.httpStatusCode)
        assertEquals(HttpStatusCode.ServiceUnavailable, serverResponse.status)

        validatorClient.close()
        serverClient.close()
    }

    @Test
    fun httpCallValidator_networkFailureDoesNotBreakDeserialization() = runBlocking {
        val client = buildHttpCallValidatorClient()

        // CONTRACT SATISFIED: No response is created, so body<T>() is never called.
        // The caller gets a clean NetworkException before any deserialization occurs.
        try {
            client.get("https://api.vultisig.com/blockchair/litecoin/dashboards/transaction/abc")
            fail("Expected NetworkException")
        } catch (e: NetworkException) {
            assertEquals(0, e.httpStatusCode)
            assertEquals("No internet connection", e.message)
            assertTrue(e.cause is java.io.IOException)
        }

        client.close()
    }

    @Test
    fun httpCallValidator_bodyOrThrowIsNeverReached() = runBlocking {
        val client = buildHttpCallValidatorClient()

        // CONTRACT SATISFIED: The exception is thrown BEFORE any response exists,
        // so bodyOrThrow() is never called. The caller catches NetworkException
        // with httpStatusCode=0 in their existing catch(Exception) block.
        try {
            val response = client.get("https://api.vultisig.com/solana/")
            response.bodyOrThrow<String>()
            fail("Expected NetworkException before reaching bodyOrThrow")
        } catch (e: NetworkException) {
            assertEquals(
                "httpStatusCode must be 0 for client-side errors",
                0,
                e.httpStatusCode,
            )
        }

        client.close()
    }

    @Test
    fun httpCallValidator_existingCatchBlocksStillWork() = runBlocking {
        val client = buildHttpCallValidatorClient()

        // CONTRACT SATISFIED: The 46+ catch(e: Exception) blocks across the codebase
        // still work because NetworkException extends RuntimeException.
        val result: String? = try {
            client.get("https://test.com").bodyAsText()
        } catch (_: Exception) {
            null
        }

        assertEquals(null, result)

        client.close()
    }
}
