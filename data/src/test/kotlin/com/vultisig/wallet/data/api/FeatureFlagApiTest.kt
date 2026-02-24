package com.vultisig.wallet.data.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Test

class FeatureFlagApiTest {
    @Test
    fun `test feature flag`() = runBlocking {
        val mockEngine = MockEngine { request ->
            respond(
                content = """{"encrypt-gcm": true}""",
                status = HttpStatusCode.OK,
                headers = headersOf(
                    HttpHeaders.ContentType,
                    ContentType.Application.Json.toString()
                )
            )
        }
        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        val featureFlagApi = FeatureFlagApiImpl(httpClient)
        val featureFlagJson = featureFlagApi.getFeatureFlag()
        assertEquals(true, featureFlagJson.isEncryptGcmEnabled)
    }
}