package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.utils.ThorChainSwapQuoteResponseJsonSerializer
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.mockk.mockk
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

/**
 * The `{"status": {}}` smart query is the only price source for the index receipts (sTCY, ybRUNE,
 * the NAMI indexes), so the host answering it decides whether those tokens have a price at all.
 *
 * It used to be pinned to ibs.team alone — a node the app consulted for nothing else. When that
 * node fell behind the chain head it answered every query with `invalid height: context did not
 * contain latest block height`, and yRUNE/yTCY rendered $0.00 while sTCY silently dropped to bare
 * TCY parity with no NAV premium.
 */
internal class ThorChainApiContractStatusHostTest {

    private val jsonFormat = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val statusBody =
        """{"data":{"liquid_bond_size":"200","liquid_bond_shares":"100","nav_per_share":"1.5"}}"""

    private val requestedHosts = mutableListOf<String>()
    private val requestedPaths = mutableListOf<String>()

    /** Routes by host so a test can fail one and serve the other. */
    private fun api(gatewayStatus: HttpStatusCode, ibsStatus: HttpStatusCode): ThorChainApi {
        val client =
            HttpClient(
                MockEngine { request ->
                    val host = request.url.host
                    requestedHosts += host
                    requestedPaths += request.url.encodedPath
                    val status = if (host.contains("ibs.team")) ibsStatus else gatewayStatus
                    val body =
                        if (status == HttpStatusCode.OK) statusBody
                        else """{"code":2,"message":"invalid height"}"""
                    respond(
                        content = body,
                        status = status,
                        headers =
                            headersOf(
                                HttpHeaders.ContentType,
                                ContentType.Application.Json.toString(),
                            ),
                    )
                }
            ) {
                install(ContentNegotiation) { json(jsonFormat, ContentType.Any) }
            }
        return ThorChainApiImpl(
            httpClient = client,
            thorChainSwapQuoteResponseJsonSerializer =
                mockk<ThorChainSwapQuoteResponseJsonSerializer>(),
            json = jsonFormat,
        )
    }

    @Test
    fun `the shared gateway answers the status query and ibs team is left alone`() = runTest {
        val response =
            api(HttpStatusCode.OK, HttpStatusCode.InternalServerError)
                .getThorchainTokenPriceByContract("thor1contract")

        assertEquals("200", response.data.liquidBondSize)
        assertEquals(listOf(GATEWAY_HOST), requestedHosts)
    }

    @Test
    fun `a stale gateway falls back to ibs team rather than leaving the receipt priceless`() =
        runTest {
            val response =
                api(HttpStatusCode.InternalServerError, HttpStatusCode.OK)
                    .getThorchainTokenPriceByContract("thor1contract")

            // The NAV still resolves, so sTCY keeps its premium instead of dropping to parity.
            assertEquals("200", response.data.liquidBondSize)
            assertEquals("100", response.data.liquidBondShares)
            // Ordered: the gateway is tried first and ibs.team only as the fallback.
            assertEquals(listOf(GATEWAY_HOST, IBS_HOST), requestedHosts)
        }

    /**
     * THORChain denoms routinely carry slashes (`x/staking-x/brune`), and the contract reaching
     * here is derived from one. Interpolated raw, such a value would split into extra path segments
     * and retarget the request.
     */
    @Test
    fun `a contract containing a slash stays inside its own path segment`() = runTest {
        api(HttpStatusCode.OK, HttpStatusCode.InternalServerError)
            .getThorchainTokenPriceByContract("thor1abc/../../evil")

        val path = requestedPaths.single()
        assertTrue(
            path.contains("thor1abc%2F..%2F..%2Fevil"),
            "expected the slashes encoded within one segment, got $path",
        )
        assertTrue(
            path.startsWith("/chain/thorchain_api/cosmwasm/wasm/v1/contract/"),
            "expected the query to stay on the contract route, got $path",
        )
    }

    private companion object {
        const val GATEWAY_HOST = "gateway.liquify.com"
        const val IBS_HOST = "thorchain.ibs.team"
    }
}
