package com.vultisig.wallet.data.blockchain.cosmos.qbtc

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class QbtcNetworkCheckTest {

    private val jsonHeaders =
        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    private var requestedUrl: String? = null

    private fun check(
        status: HttpStatusCode = HttpStatusCode.OK,
        body: String = "",
        responseDelayMillis: Long = 0,
    ): QbtcNetworkCheck =
        QbtcNetworkCheckImpl(
            HttpClient(
                MockEngine { request ->
                    requestedUrl = request.url.toString()
                    if (responseDelayMillis > 0) delay(responseDelayMillis)
                    respond(content = body, status = status, headers = jsonHeaders)
                }
            ) {
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            },
            timeout = 500.milliseconds,
        )

    // runTest's virtual clock would fire the timeout as soon as the test thread idles, while the
    // mock engine answers on a real thread, so the check runs on real time here.
    private suspend fun QbtcNetworkCheck.outdated(): Boolean =
        withContext(Dispatchers.Default) { isAppOutdated() }

    private fun nodeInfo(network: String) =
        """{"default_node_info":{"network":"$network","moniker":"qbtc"},"application_version":{}}"""

    @Test
    fun `matching network is not outdated`() = runTest {
        assertFalse(check(body = nodeInfo(QbtcNetworkConfig.CHAIN_ID)).outdated())
        assertEquals(
            "${QbtcNetworkConfig.REST_URL}/cosmos/base/tendermint/v1beta1/node_info",
            requestedUrl,
        )
    }

    @Test
    fun `another network is outdated`() = runTest {
        assertTrue(check(body = nodeInfo("qbtc-mainnet-1")).outdated())
    }

    @Test
    fun `server error does not block`() = runTest {
        assertFalse(check(status = HttpStatusCode.BadGateway, body = "oops").outdated())
    }

    @Test
    fun `unreadable response does not block`() = runTest {
        assertFalse(check(body = """{"unexpected":true}""").outdated())
    }

    @Test
    fun `slow node does not block`() = runTest {
        assertFalse(
            check(
                    body = nodeInfo("qbtc-mainnet-1"),
                    responseDelayMillis = 5.seconds.inWholeMilliseconds,
                )
                .outdated()
        )
    }
}
