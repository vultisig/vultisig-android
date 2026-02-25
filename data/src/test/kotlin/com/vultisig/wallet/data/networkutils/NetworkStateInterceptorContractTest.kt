package com.vultisig.wallet.data.networkutils

import com.vultisig.wallet.data.testutils.MockHttpClient
import com.vultisig.wallet.data.utils.NetworkException
import com.vultisig.wallet.data.utils.bodyOrThrow
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException

/**
 * Contract tests for network error handling.
 *
 * Verifies that the `HttpCallValidator` approach (`IOException` → `NetworkException`)
 * satisfies all requirements, and does NOT swallow errors it shouldn't.
 *
 * Contract requirements:
 * 1. Network failures must be distinguishable from real server errors.
 * 2. Network failures must not break response deserialization.
 * 3. `bodyOrThrow()` must report network failures as client-side errors (code 0).
 * 4. Callers using status-code checks must not confuse network failures with server rejections.
 * 5. Existing `catch(Exception)` patterns must still catch network errors.
 * 6. All `IOException` subtypes (SSL, timeout, DNS, connection) are handled uniformly.
 * 7. Non-network errors (deserialization, business logic) are NOT swallowed.
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

    @Serializable
    data class SimpleResponse(@SerialName("value") val value: String)

    // ================================================================
    // GROUP 1: All IOException subtypes → NetworkException(httpStatusCode=0)
    //
    // Every transport-level failure must produce a NetworkException with
    // httpStatusCode=0 regardless of the specific IOException subclass.
    // ================================================================

    @Test
    fun ioException_becomesNetworkExceptionWithCode0() = runBlocking {
        assertTransportExceptionBecomesNetworkException(
            IOException("Connection reset")
        )
    }

    @Test
    fun sslHandshakeException_becomesNetworkExceptionWithCode0() = runBlocking {
        assertTransportExceptionBecomesNetworkException(
            SSLHandshakeException("Handshake failed")
        )
    }

    @Test
    fun socketTimeoutException_becomesNetworkExceptionWithCode0() = runBlocking {
        assertTransportExceptionBecomesNetworkException(
            SocketTimeoutException("Read timed out")
        )
    }

    @Test
    fun connectException_becomesNetworkExceptionWithCode0() = runBlocking {
        assertTransportExceptionBecomesNetworkException(
            ConnectException("Connection refused")
        )
    }

    @Test
    fun unknownHostException_becomesNetworkExceptionWithCode0() = runBlocking {
        assertTransportExceptionBecomesNetworkException(
            UnknownHostException("Unable to resolve host")
        )
    }

    private suspend fun assertTransportExceptionBecomesNetworkException(
        ioException: IOException,
    ) {
        val client = MockHttpClient.throwingIOException(ioException)
        try {
            client.get("https://api.vultisig.com/test")
            fail("Expected NetworkException but request succeeded")
        } catch (e: NetworkException) {
            assertEquals(
                "httpStatusCode must be 0 for client-side transport errors",
                0, e.httpStatusCode,
            )
            assertEquals("No internet connection", e.message)
            assertTrue(
                "cause must be the original IOException (${ioException::class.simpleName})",
                e.cause is IOException,
            )
            assertEquals(ioException.message, e.cause?.message)
        }
        client.close()
    }

    // ================================================================
    // GROUP 2: Network failure vs server error — must be distinguishable
    // ================================================================

    @Test
    fun networkFailure_isClearlyDistinguished_fromRealServer503() = runBlocking {
        val networkClient = MockHttpClient.throwingIOException(
            IOException("Unable to resolve host")
        )
        val serverClient = MockHttpClient.respondingWith(
            HttpStatusCode.ServiceUnavailable,
            """{"error": "Service temporarily unavailable"}""",
        )

        // Network failure → exception (no response)
        val networkException = try {
            networkClient.get("https://api.vultisig.com/test")
            null
        } catch (e: NetworkException) {
            e
        }

        // Server 503 → normal response
        val serverResponse = serverClient.get("https://api.vultisig.com/test")

        assertNotNull("Network failure must throw an exception", networkException)
        assertEquals(0, networkException!!.httpStatusCode)
        assertEquals(HttpStatusCode.ServiceUnavailable, serverResponse.status)

        networkClient.close()
        serverClient.close()
    }

    @Test
    fun networkFailure_doesNotBreakDeserialization() = runBlocking {
        val client = MockHttpClient.throwingIOException(
            IOException("Unable to resolve host")
        )

        // Exception is thrown at client.get() — body<T>() is never called.
        try {
            client.get("https://api.vultisig.com/blockchair/litecoin/dashboards/transaction/abc")
            fail("Expected NetworkException")
        } catch (e: NetworkException) {
            assertEquals(0, e.httpStatusCode)
            assertEquals("No internet connection", e.message)
        }

        client.close()
    }

    @Test
    fun networkFailure_bodyOrThrow_isNeverReached() = runBlocking {
        val client = MockHttpClient.throwingIOException(
            IOException("Unable to resolve host")
        )

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
    fun networkException_isCaughtByExistingCatchExceptionBlocks() = runBlocking {
        val client = MockHttpClient.throwingIOException(
            IOException("Unable to resolve host")
        )

        // Simulates the 46+ catch(Exception) blocks across the codebase.
        val result: String? = try {
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
    fun server200_withValidJson_deserializesCorrectly() = runBlocking {
        val client = MockHttpClient.respondingWith(
            HttpStatusCode.OK,
            """{"value": "hello"}""",
        )

        val response = client.get("https://api.vultisig.com/test")
        val body = response.body<SimpleResponse>()

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("hello", body.value)

        client.close()
    }

    @Test
    fun server4xx_responseIsReturnedNormally() = runBlocking {
        val client = MockHttpClient.respondingWith(
            HttpStatusCode.BadRequest,
            """{"error": "Invalid address"}""",
        )

        val response = client.get("https://api.vultisig.com/test")

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("""{"error": "Invalid address"}""", response.bodyAsText())

        client.close()
    }

    @Test
    fun server5xx_responseIsReturnedNormally() = runBlocking {
        val client = MockHttpClient.respondingWith(
            HttpStatusCode.InternalServerError,
            """{"error": "Internal server error"}""",
        )

        val response = client.get("https://api.vultisig.com/test")

        assertEquals(HttpStatusCode.InternalServerError, response.status)

        client.close()
    }

    @Test
    fun bodyOrThrow_onNon2xx_throwsNetworkExceptionWithActualStatusCode() = runBlocking {
        val client = MockHttpClient.respondingWith(
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
    fun server200_withInvalidJson_throwsDeserializationError_notNetworkException() = runBlocking {
        val client = MockHttpClient.respondingWith(
            HttpStatusCode.OK,
            "this is not json at all",
        )

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
    fun server200_withWrongJsonShape_throwsDeserializationError() = runBlocking {
        // Simulates what happens when a server returns 200 but with unexpected JSON.
        // The synthetic body from the old interceptor caused exactly this problem.
        val client = MockHttpClient.respondingWith(
            HttpStatusCode.OK,
            """{"error": "Network failure: Unable to resolve host"}""",
        )

        try {
            val response = client.get("https://api.vultisig.com/blockchair/litecoin/dashboards/transaction/abc")
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
