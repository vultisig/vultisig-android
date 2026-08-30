package com.vultisig.wallet.data.utils

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType.Application
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class RpcExtensionExtractErrorTest {

    @Test
    fun returnsErrorValueWhenJsonContainsErrorKey() = runTest {
        val response = mockResponse("""{ "error": "Invalid token" }""")

        val result = extractError(response, "error")

        assertEquals("Invalid token", result)
    }

    @Test
    fun returnsErrorValueFromTopLevelJsonArray() = runTest {
        val body =
            """
        [
          { "code": 401 },
          { "error": "Authentication failed" }
        ]
    """
                .trimIndent()

        val response = mockResponse(body)

        val result = extractError(response, "error")

        assertEquals("Authentication failed", result)
    }

    @Test
    fun returnsFullResponseBodyWhenJsonDoesNotContainErrorKey() = runTest {
        val body = """{ "message": "Something went wrong" }"""
        val response = mockResponse(body)

        val result = extractError(response, "error")

        assertEquals(body, result)
    }

    @Test
    fun returnsRawBodyWhenResponseIsNotJson() = runTest {
        val body = "Internal Server Error"
        val response = mockResponse(body)

        val result = extractError(response, "error")

        assertEquals(body, result)
    }

    @Test
    fun returnsRawBodyWhenJsonIsMalformed() = runTest {
        val body = """{ "error": "Invalid token" """
        val response = mockResponse(body)

        val result = extractError(response, "error")

        assertEquals(body, result)
    }

    @Test
    fun returnsEmptyStringWhenErrorKeyExistsButIsEmpty() = runTest {
        val response = mockResponse("""{ "error": "" }""")

        val result = extractError(response, "error")

        assertEquals("", result)
    }

    @Test
    fun returnsErrorValueFromNestedJsonObject() = runTest {
        val body =
            """
        {
          "status": "error",
          "data": {
            "error": "Invalid token"
          }
        }
    """
                .trimIndent()

        val response = mockResponse(body)

        val result = extractError(response, "error")

        assertEquals("Invalid token", result)
    }

    @Test
    fun returnsErrorValueFromDeeplyNestedJson() = runTest {
        val body =
            """
        {
          "meta": {
            "request": {
              "failure": {
                "error": "User not authorized"
              }
            }
          }
        }
    """
                .trimIndent()

        val response = mockResponse(body)

        val result = extractError(response, "error")

        assertEquals("User not authorized", result)
    }

    @Test
    fun prefersTopLevelErrorOverNestedError() = runTest {
        val body =
            """
        {
          "error": "Top level error",
          "data": {
            "error": "Nested error"
          }
        }
    """
                .trimIndent()

        val response = mockResponse(body)

        val result = extractError(response, "error")

        assertEquals("Top level error", result)
    }

    @Test
    fun returnsErrorValueFromJsonArray() = runTest {
        val body =
            """
        {
          "errors": [
            { "code": 401 },
            { "error": "Token expired" }
          ]
        }
    """
                .trimIndent()

        val response = mockResponse(body)

        val result = extractError(response, "error")

        assertEquals("Token expired", result)
    }

    @Test
    fun returnsFirstMatchingErrorWhenMultipleExist() = runTest {
        val body =
            """
        {
          "data": {
            "error": "First error",
            "details": {
              "error": "Second error"
            }
          }
        }
    """
                .trimIndent()

        val response = mockResponse(body)

        val result = extractError(response, "error")

        assertEquals("First error", result)
    }

    @Test
    fun returnsFullBodyWhenErrorKeyNotFoundRecursively() = runTest {
        val body =
            """
        {
          "message": "Something failed",
          "code": 500
        }
    """
                .trimIndent()

        val response = mockResponse(body)

        val result = extractError(response, "error")

        assertEquals(body, result)
    }

    @Test
    fun findsErrorInMixedJsonStructures() = runTest {
        val body =
            """
        {
          "data": [
            {
              "items": [
                { "error": "Deep array error" }
              ]
            }
          ]
        }
    """
                .trimIndent()

        val response = mockResponse(body)

        val result = extractError(response, "error")

        assertEquals("Deep array error", result)
    }

    private suspend fun mockResponse(
        body: String,
        status: HttpStatusCode = HttpStatusCode.BadRequest,
    ): HttpResponse {
        val engine = MockEngine {
            respond(
                content = body,
                status = status,
                headers = headersOf(HttpHeaders.ContentType, Application.Json.toString()),
            )
        }

        return HttpClient(engine).use { client -> client.get("https://test.com") }
    }
}
