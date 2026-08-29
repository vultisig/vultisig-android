package com.vultisig.wallet.data.networkutils

import com.vultisig.wallet.data.testutils.MockHttpClient
import com.vultisig.wallet.data.utils.NetworkErrorKind
import com.vultisig.wallet.data.utils.NetworkException
import com.vultisig.wallet.data.utils.bodyOrThrow
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Test

/**
 * Contract tests for network error handling.
 *
 * Verifies that the `HttpCallValidator` approach (`IOException` → `NetworkException`) satisfies all
 * requirements, and does NOT swallow errors it shouldn't.
 *
 * Contract requirements:
 * 1. Network failures must be distinguishable from real server errors.
 * 2. Network failures must not break response deserialization.
 * 3. `bodyOrThrow()` must report network failures as client-side errors (code 0).
 * 4. Callers using status-code checks must not confuse network failures with server rejections.
 * 5. Existing `catch(Exception)` patterns must still catch network errors.
 * 6. All `IOException` subtypes (SSL, timeout, DNS, connection) become a transport-level
 *    `NetworkException` (httpStatusCode=0), classified by subtype into a `NetworkErrorKind`.
 * 7. Non-network errors (deserialization, business logic) are NOT swallowed.
 */
class NetworkStateInterceptorContractTest {

    // -- Models mimicking real codebase types (Blockchair.kt) --

    @Serializable
    data class BlockChairDashboardResponse(
        @SerialName("data") val data: Map<String, TransactionData>? = null,
        @SerialName("context") val context: ContextData,
    )

    @Serializable data class TransactionData(val transaction: TransactionInfo? = null)

    @Serializable data class TransactionInfo(@SerialName("block_id") val blockId: Int? = null)

    @Serializable data class ContextData(@SerialName("state") val state: Int)

    @Serializable data class SimpleResponse(@SerialName("value") val value: String)

    // ================================================================
    // GROUP 1: All IOException subtypes → NetworkException(httpStatusCode=0)
    //
    // Every transport-level failure must produce a NetworkException with
    // httpStatusCode=0 regardless of the specific IOException subclass.
    // ================================================================

    @Test
    fun ioException_becomesTransportNetworkException() = runTest {
        assertTransportExceptionBecomesNetworkException(
            IOException("Connection reset"),
            expectedKind = NetworkErrorKind.Transport,
        )
    }

    @Test
    fun sslHandshakeException_becomesTransportNetworkException() = runTest {
        assertTransportExceptionBecomesNetworkException(
            SSLHandshakeException("Handshake failed"),
            expectedKind = NetworkErrorKind.Transport,
        )
    }

    @Test
    fun socketTimeoutException_becomesTimeoutNetworkException() = runTest {
        assertTransportExceptionBecomesNetworkException(
            SocketTimeoutException("Read timed out"),
            expectedKind = NetworkErrorKind.Timeout,
        )
    }

    @Test
    fun connectException_becomesTransportNetworkException() = runTest {
        assertTransportExceptionBecomesNetworkException(
            ConnectException("Connection refused"),
            expectedKind = NetworkErrorKind.Transport,
        )
    }

    @Test
    fun unknownHostException_becomesNoConnectivityNetworkException() = runTest {
        assertTransportExceptionBecomesNetworkException(
            UnknownHostException("Unable to resolve host"),
            expectedKind = NetworkErrorKind.NoConnectivity,
        )
    }

    private suspend fun assertTransportExceptionBecomesNetworkException(
        ioException: IOException,
        expectedKind: NetworkErrorKind,
    ) {
        val client = MockHttpClient.throwingIOException(ioException)
        try {
            client.get("https://api.vultisig.com/test")
            fail("Expected NetworkException but request succeeded")
        } catch (e: NetworkException) {
            assertEquals(
                expected = 0,
                actual = e.httpStatusCode,
                message = "httpStatusCode must be 0 for client-side transport errors",
            )
            assertEquals(
                expected = expectedKind,
                actual = e.kind,
                message = "transport failure must be classified by subtype",
            )
            assertTrue(
                actual = e.cause is IOException,
                message =
                    "cause must be the original IOException (${ioException::class.simpleName})",
            )
            assertEquals(ioException.message, e.cause?.message)
        }
        client.close()
    }

    // ================================================================
    // GROUP 2: Network failure vs server error — must be distinguishable
    // ================================================================

    @Test
    fun networkFailure_isClearlyDistinguished_fromRealServer503() = runTest {
        val networkClient =
            MockHttpClient.throwingIOException(IOException("Unable to resolve host"))
        val serverClient =
            MockHttpClient.respondingWith(
                HttpStatusCode.ServiceUnavailable,
                """{"error": "Service temporarily unavailable"}""",
            )

        // Network failure → exception (no response)
        val networkException =
            try {
                networkClient.get("https://api.vultisig.com/test")
                null
            } catch (e: NetworkException) {
                e
            }

        // Server 503 → normal response
        val serverResponse = serverClient.get("https://api.vultisig.com/test")

        val caughtNetworkException =
            assertNotNull(networkException, "Network failure must throw an exception")
        assertEquals(0, caughtNetworkException.httpStatusCode)
        assertEquals(HttpStatusCode.ServiceUnavailable, serverResponse.status)

        networkClient.close()
        serverClient.close()
    }

    @Test
    fun networkFailure_doesNotBreakDeserialization() = runTest {
        val client = MockHttpClient.throwingIOException(IOException("Unable to resolve host"))

        // Exception is thrown at client.get() — body<T>() is never called.
        try {
            client.get("https://api.vultisig.com/blockchair/litecoin/dashboards/transaction/abc")
            fail("Expected NetworkException")
        } catch (e: NetworkException) {
            assertEquals(0, e.httpStatusCode)
            assertEquals(NetworkErrorKind.Transport, e.kind)
        }

        client.close()
    }

    @Test
    fun networkFailure_bodyOrThrow_isNeverReached() = runTest {
        val client = MockHttpClient.throwingIOException(IOException("Unable to resolve host"))

        try {
            val response = client.get("https://api.vultisig.com/solana/")
            response.bodyOrThrow<String>()
            fail("Expected NetworkException before reaching bodyOrThrow")
        } catch (e: NetworkException) {
            assertEquals(0, e.httpStatusCode)
        }

        client.close()
    }

    @Test
    fun networkException_isCaughtByExistingCatchExceptionBlocks() = runTest {
        val client = MockHttpClient.throwingIOException(IOException("Unable to resolve host"))

        // Simulates the 46+ catch(Exception) blocks across the codebase.
        val result: String? =
            try {
                client.get("https://test.com").bodyAsText()
            } catch (_: Exception) {
                null
            }

        assertEquals(null, result)
        client.close()
    }

    // ================================================================
    // GROUP 3: Server responses pass through correctly
    //
    // HttpCallValidator must NOT intercept real HTTP responses.
    // Only transport-level IOExceptions are caught.
    // ================================================================

    @Test
    fun server200_withValidJson_deserializesCorrectly() = runTest {
        val client = MockHttpClient.respondingWith(HttpStatusCode.OK, """{"value": "hello"}""")

        val response = client.get("https://api.vultisig.com/test")
        val body = response.body<SimpleResponse>()

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("hello", body.value)

        client.close()
    }

    @Test
    fun server4xx_responseIsReturnedNormally() = runTest {
        val client =
            MockHttpClient.respondingWith(
                HttpStatusCode.BadRequest,
                """{"error": "Invalid address"}""",
            )

        val response = client.get("https://api.vultisig.com/test")

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("""{"error": "Invalid address"}""", response.bodyAsText())

        client.close()
    }

    @Test
    fun server5xx_responseIsReturnedNormally() = runTest {
        val client =
            MockHttpClient.respondingWith(
                HttpStatusCode.InternalServerError,
                """{"error": "Internal server error"}""",
            )

        val response = client.get("https://api.vultisig.com/test")

        assertEquals(HttpStatusCode.InternalServerError, response.status)

        client.close()
    }

    @Test
    fun bodyOrThrow_onNon2xx_throwsNetworkExceptionWithActualStatusCode() = runTest {
        val client =
            MockHttpClient.respondingWith(
                HttpStatusCode.BadRequest,
                """{"message": "Invalid address format"}""",
            )

        try {
            val response = client.get("https://api.vultisig.com/blockchair/push")
            response.bodyOrThrow<String>()
            fail("Expected NetworkException from bodyOrThrow on 400")
        } catch (e: NetworkException) {
            // bodyOrThrow wraps non-2xx with the ACTUAL server status code,
            // NOT 0 (which is reserved for transport errors).
            assertEquals(400, e.httpStatusCode)
        }

        client.close()
    }

    // ================================================================
    // GROUP 4: Deserialization errors escape HttpCallValidator
    //
    // These tests prove that HttpCallValidator does NOT swallow
    // application-level errors. Deserialization failures must propagate
    // to the caller — this is the crash vector that safeLaunch addresses.
    // ================================================================

    @Test
    fun server200_withInvalidJson_throwsDeserializationError_notNetworkException() = runTest {
        val client = MockHttpClient.respondingWith(HttpStatusCode.OK, "this is not json at all")

        try {
            val response = client.get("https://api.vultisig.com/test")
            response.body<SimpleResponse>()
            fail("Expected a deserialization exception")
        } catch (e: NetworkException) {
            fail(
                "Deserialization errors must NOT become NetworkException. " +
                    "Got NetworkException(${e.httpStatusCode}): ${e.message}"
            )
        } catch (_: Exception) {
            // CORRECT: Deserialization error escapes as a non-NetworkException.
            // This is the crash vector that safeLaunch protects against.
        }

        client.close()
    }

    @Test
    fun server200_withWrongJsonShape_throwsDeserializationError() = runTest {
        // Simulates what happens when a server returns 200 but with unexpected JSON.
        // The synthetic body from the old interceptor caused exactly this problem.
        val client =
            MockHttpClient.respondingWith(
                HttpStatusCode.OK,
                """{"error": "Network failure: Unable to resolve host"}""",
            )

        try {
            val response =
                client.get(
                    "https://api.vultisig.com/blockchair/litecoin/dashboards/transaction/abc"
                )
            response.body<BlockChairDashboardResponse>()
            fail("Expected a deserialization exception for mismatched JSON shape")
        } catch (e: NetworkException) {
            fail(
                "JSON shape mismatch must NOT become NetworkException. " +
                    "Got NetworkException(${e.httpStatusCode}): ${e.message}"
            )
        } catch (_: Exception) {
            // CORRECT: missing required field "context" → deserialization error.
            // This proves the old interceptor's synthetic 503 body would crash here.
        }

        client.close()
    }
}
