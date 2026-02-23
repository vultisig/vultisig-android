package com.vultisig.wallet.data.utils

import com.vultisig.wallet.data.testutils.MockHttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException

/**
 * Tests for [safeLaunch] — the ViewModel-level safety net.
 *
 * [safeLaunch] catches all exceptions except [CancellationException],
 * preventing crashes from unhandled errors in coroutines.
 *
 * Test groups:
 * 1. Catches application-level exceptions (the crash vectors HttpCallValidator can't handle)
 * 2. CancellationException is never swallowed (preserves coroutine lifecycle)
 * 3. Normal operation (happy path, job completion)
 * 4. Integration with Ktor (proves the two-layer architecture works end-to-end)
 */
class SafeLaunchTest {

    @Serializable
    data class TypedResponse(@SerialName("id") val id: Int)

    // ================================================================
    // GROUP 1: Catches application-level exceptions
    //
    // These are the crash vectors that HttpCallValidator cannot handle:
    // deserialization errors, business logic errors, unhandled NetworkException.
    // ================================================================

    @Test
    fun catches_ioException() = runBlocking {
        var caught: Throwable? = null

        val job = safeLaunch(onError = { caught = it }) {
            throw IOException("Connection reset")
        }
        job.join()

        assertNotNull(caught, "IOException must be caught")
        assertTrue(caught is IOException)
    }

    @Test
    fun catches_runtimeException_fromErrorCalls() = runBlocking {
        // Simulates: error("fail to broadcast transaction: ...")
        // which is used in BlockChairApi, CardanoApi, etc.
        var caught: Throwable? = null

        val job = safeLaunch(onError = { caught = it }) {
            error("fail to broadcast transaction: invalid hex")
        }
        job.join()

        assertNotNull(caught, "RuntimeException from error() must be caught")
        assertTrue(caught is IllegalStateException)
        assertEquals("fail to broadcast transaction: invalid hex", caught!!.message)
    }

    @Test
    fun catches_networkException() = runBlocking {
        // Simulates: bodyOrThrow() throwing NetworkException on non-2xx response
        var caught: Throwable? = null

        val job = safeLaunch(onError = { caught = it }) {
            throw NetworkException(
                httpStatusCode = 500,
                message = "Internal server error",
            )
        }
        job.join()

        assertNotNull(caught, "NetworkException must be caught")
        assertTrue(caught is NetworkException)
        assertEquals(500, (caught as NetworkException).httpStatusCode)
    }

    @Test
    fun catches_serializationException() = runBlocking {
        // Simulates: body<T>() failing because server returned unexpected JSON
        var caught: Throwable? = null

        val job = safeLaunch(onError = { caught = it }) {
            throw kotlinx.serialization.SerializationException("Missing field: context")
        }
        job.join()

        assertNotNull(caught, "SerializationException must be caught")
        assertTrue(caught is kotlinx.serialization.SerializationException)
    }

    @Test
    fun catches_nullPointerException() = runBlocking {
        // Simulates: accessing null response data without null check
        var caught: Throwable? = null

        val job = safeLaunch(onError = { caught = it }) {
            val list: List<String>? = null
            @Suppress("ALWAYS_NULL")
            list!!.size
        }
        job.join()

        assertNotNull(caught, "NullPointerException must be caught")
        assertTrue(caught is NullPointerException)
    }

    // ================================================================
    // GROUP 2: CancellationException is never swallowed
    //
    // CancellationException must always propagate — swallowing it
    // would break coroutine cancellation (ViewModel.onCleared, job.cancel).
    // ================================================================

    @Test
    fun cancellationException_isNotCaught_whenJobIsCancelled() = runBlocking {
        var onErrorCalled = false

        val job = safeLaunch(onError = { onErrorCalled = true }) {
            delay(Long.MAX_VALUE) // suspend until cancelled
        }

        job.cancel()
        job.join()

        assertFalse(
            onErrorCalled,
            "CancellationException must NOT trigger onError — " +
                    "it must propagate to cancel the coroutine",
        )
        assertTrue(job.isCancelled, "Job must be cancelled")
    }

    @Test
    fun cancellationException_thrownDirectly_isNotCaught() = runBlocking {
        var onErrorCalled = false

        val job = safeLaunch(onError = { onErrorCalled = true }) {
            throw CancellationException("scope cleared")
        }
        job.join()

        assertFalse(
            onErrorCalled,
            "Directly thrown CancellationException must NOT trigger onError",
        )
        assertTrue(job.isCancelled, "Job must be cancelled")
    }

    // ================================================================
    // GROUP 3: Normal operation
    // ================================================================

    @Test
    fun normalCompletion_doesNotTriggerOnError() = runBlocking {
        var onErrorCalled = false
        var blockCompleted = false

        val job = safeLaunch(onError = { onErrorCalled = true }) {
            blockCompleted = true
        }
        job.join()

        assertTrue(blockCompleted, "Block must complete")
        assertFalse(onErrorCalled, "onError must not be called on success")
        assertTrue(job.isCompleted, "Job must complete normally")
        assertFalse(job.isCancelled, "Job must not be cancelled")
    }

    @Test
    fun onError_receivesExactException() = runBlocking {
        val original = IllegalArgumentException("bad input")
        var caught: Throwable? = null

        val job = safeLaunch(onError = { caught = it }) {
            throw original
        }
        job.join()

        assertTrue(
            caught === original,
            "onError must receive the exact same exception instance",
        )
    }

    @Test
    fun jobCompletes_afterError() = runBlocking {
        val job = safeLaunch(onError = { /* swallow */ }) {
            error("crash")
        }
        job.join()

        assertTrue(job.isCompleted, "Job must complete (not hang) after error")
        assertFalse(job.isCancelled, "Job must not be cancelled when error is caught")
    }

    @Test
    fun multipleIndependentSafeLaunches_oneFailure_doesNotAffectOthers() = runBlocking {
        var firstResult: String? = null
        var secondCaught: Throwable? = null
        var thirdResult: String? = null

        val job1 = safeLaunch { firstResult = "success" }
        val job2 = safeLaunch(onError = { secondCaught = it }) {
            error("second fails")
        }
        val job3 = safeLaunch { thirdResult = "also success" }

        job1.join()
        job2.join()
        job3.join()

        assertEquals("success", firstResult)
        assertNotNull(secondCaught, "second must have caught error")
        assertEquals("also success", thirdResult)
    }

    // ================================================================
    // GROUP 4: Integration with Ktor — two-layer architecture
    //
    // Proves that HttpCallValidator (layer 1) and safeLaunch (layer 2)
    // work together to handle ALL crash vectors.
    // ================================================================

    @Test
    fun layer1_httpCallValidator_catchesNetworkError_safeLaunchReceivesNetworkException() =
        runBlocking {
            val client = MockHttpClient.throwingIOException(
                IOException("Unable to resolve host")
            )
            var caught: Throwable? = null

            val job = safeLaunch(onError = { caught = it }) {
                val response = client.get("https://api.vultisig.com/test")
                response.body<TypedResponse>()
            }
            job.join()

            // Layer 1 (HttpCallValidator) converts IOException → NetworkException
            // Layer 2 (safeLaunch) catches it — no crash
            assertNotNull(caught, "safeLaunch must catch the NetworkException")
            assertTrue(
                caught is NetworkException,
                "Must be NetworkException from HttpCallValidator, got ${caught!!::class.simpleName}",
            )
            assertEquals(0, (caught as NetworkException).httpStatusCode)

            client.close()
        }

    @Test
    fun layer2_safeLaunch_catchesDeserializationError_thatEscapesHttpCallValidator() =
        runBlocking {
            // Server returns 200 with JSON that doesn't match TypedResponse
            val client = MockHttpClient.respondingWith(
                HttpStatusCode.OK,
                """{"unexpected_field": "value"}""",
            )
            var caught: Throwable? = null

            val job = safeLaunch(onError = { caught = it }) {
                val response = client.get("https://api.vultisig.com/data")
                response.body<TypedResponse>() // missing "id" field → deserialization error
            }
            job.join()

            // HttpCallValidator does NOT catch deserialization errors (correct).
            // safeLaunch catches them — no crash.
            assertNotNull(caught, "safeLaunch must catch deserialization error")
            assertFalse(
                caught is NetworkException,
                "Deserialization error must NOT be NetworkException — " +
                        "it's an application-level bug, not a transport error. " +
                        "Got: ${caught!!::class.simpleName}",
            )

            client.close()
        }

    @Test
    fun layer2_safeLaunch_catchesBodyOrThrowNetworkException_fromNon2xxResponse() =
        runBlocking {
            val client = MockHttpClient.respondingWith(
                HttpStatusCode.InternalServerError,
                """{"message": "database connection failed"}""",
            )
            var caught: Throwable? = null

            val job = safeLaunch(onError = { caught = it }) {
                val response = client.get("https://api.vultisig.com/solana/")
                response.bodyOrThrow<String>() // throws NetworkException(500, ...)
            }
            job.join()

            assertNotNull(caught, "safeLaunch must catch NetworkException from bodyOrThrow")
            assertTrue(caught is NetworkException)
            assertEquals(500, (caught as NetworkException).httpStatusCode)

            client.close()
        }

    @Test
    fun layer2_safeLaunch_catchesBusinessLogicError_fromStatusCodeCheck() =
        runBlocking {
            val client = MockHttpClient.respondingWith(
                HttpStatusCode.BadRequest,
                """{"error": "invalid transaction hex"}""",
            )
            var caught: Throwable? = null

            val job = safeLaunch(onError = { caught = it }) {
                // Simulates BlockChairApi.broadcastTransaction pattern:
                // if (response.status != HttpStatusCode.OK) { error("fail to broadcast") }
                val response = client.get("https://api.vultisig.com/blockchair/push")
                if (response.status != HttpStatusCode.OK) {
                    val errorBody = response.bodyAsText()
                    error("fail to broadcast transaction: $errorBody")
                }
            }
            job.join()

            assertNotNull(caught, "safeLaunch must catch the error() call")
            assertTrue(caught is IllegalStateException)
            assertTrue(caught!!.message!!.contains("fail to broadcast"))

            client.close()
        }

    @Test
    fun withoutSafeLaunch_deserializationError_wouldCrash() = runBlocking {
        // Documents what happens WITHOUT safeLaunch — the exception escapes.
        // In a ViewModel without try-catch, this crashes the app.
        val client = MockHttpClient.respondingWith(
            HttpStatusCode.OK,
            """not json""",
        )

        var exceptionEscaped = false
        try {
            val response = client.get("https://api.vultisig.com/data")
            response.body<TypedResponse>()
        } catch (_: Exception) {
            exceptionEscaped = true
        }

        assertTrue(
            exceptionEscaped,
            "Without safeLaunch, deserialization errors escape to the caller. " +
                    "In a ViewModel without try-catch, this crashes the app.",
        )

        client.close()
    }

    @Test
    fun onError_canUpdateUiState_withErrorMessage() = runBlocking {
        // Simulates a ViewModel using safeLaunch with custom error handling
        var uiErrorMessage: String? = null

        val job = safeLaunch(
            onError = { e ->
                uiErrorMessage = when (e) {
                    is NetworkException ->
                        if (e.httpStatusCode == 0) "No internet connection"
                        else "Server error: ${e.message}"

                    else -> "Unexpected error: ${e.message}"
                }
            }
        ) {
            throw NetworkException(httpStatusCode = 0, message = "No internet connection")
        }
        job.join()

        assertEquals("No internet connection", uiErrorMessage)
    }

    @Test
    fun safeLaunch_defaultHandler_doesNotCrash() = runBlocking {
        // Uses the default onError (Timber.e). Verifies it doesn't throw.
        // In tests, Timber is a no-op unless a Tree is planted.
        val job = safeLaunch {
            error("this would crash without safeLaunch")
        }
        job.join()

        assertTrue(job.isCompleted, "Job must complete even with default handler")
    }
}
