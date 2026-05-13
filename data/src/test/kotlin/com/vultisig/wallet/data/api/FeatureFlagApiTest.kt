package com.vultisig.wallet.data.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

class FeatureFlagApiTest {

    @Test
    fun `both flags parsed when present`() = runTest {
        val api = apiReturning("""{"encrypt-gcm": true, "tss-batch": true}""")

        val flags = api.getFeatureFlags()

        assertEquals(true, flags.isEncryptGcmEnabled)
        assertEquals(true, flags.isTssBatchEnabled)
    }

    @Test
    fun `tss-batch flag parsed from real server response format`() = runTest {
        val api = apiReturning("""{"encrypt-gcm": false, "tss-batch": false}""")

        val flags = api.getFeatureFlags()

        assertEquals(false, flags.isEncryptGcmEnabled)
        assertEquals(false, flags.isTssBatchEnabled)
    }

    @Test
    fun `tss-batch true enables batch keygen`() = runTest {
        val api = apiReturning("""{"encrypt-gcm": false, "tss-batch": true}""")

        val flags = api.getFeatureFlags()

        assertEquals(true, flags.isTssBatchEnabled)
    }

    @Test
    fun `unknown keys in server response are ignored`() = runTest {
        val api = apiReturning("""{"encrypt-gcm": true, "tss-batch": true, "future-flag": true}""")

        val flags = api.getFeatureFlags()

        assertEquals(true, flags.isEncryptGcmEnabled)
        assertEquals(true, flags.isTssBatchEnabled)
    }

    @Test
    fun `tss-batch defaults to false when missing from payload`() = runTest {
        val api = apiReturning("""{"encrypt-gcm": true}""")

        val flags = api.getFeatureFlags()

        assertEquals(false, flags.isTssBatchEnabled)
    }

    private fun apiReturning(json: String): FeatureFlagApi {
        val client =
            HttpClient(
                MockEngine {
                    respond(
                        content = json,
                        status = HttpStatusCode.OK,
                        headers =
                            headersOf(
                                HttpHeaders.ContentType,
                                ContentType.Application.Json.toString(),
                            ),
                    )
                }
            ) {
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            }
        return FeatureFlagApiImpl(client)
    }
}
