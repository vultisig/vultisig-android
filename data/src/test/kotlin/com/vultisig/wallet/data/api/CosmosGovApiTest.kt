package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.utils.CosmosThorChainResponseSerializerImpl
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
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

class CosmosGovApiTest {

    private val jsonHeaders =
        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    @Test
    fun `getGovProposals parses the proposals list on 200`() = runTest {
        val api =
            cosmosApi(
                MockEngine {
                    respond(
                        content =
                            """{"proposals":[{"id":"1","title":"Claim UTXO to reserve",""" +
                                """"summary":"Claim more UTXO","final_tally_result":""" +
                                """{"yes_count":"1000","no_count":"0","abstain_count":"0",""" +
                                """"no_with_veto_count":"0"},""" +
                                """"voting_end_time":"2026-06-22T00:18:31.833408824Z"}]}""",
                        status = HttpStatusCode.OK,
                        headers = jsonHeaders,
                    )
                }
            )

        val proposals = api.getGovProposals(3)

        assertEquals(1, proposals.size)
        assertEquals("1", proposals.first().id)
        assertEquals("Claim UTXO to reserve", proposals.first().title)
        assertEquals("1000", proposals.first().finalTallyResult?.yesCount)
    }

    @Test
    fun `getGovProposals sends the requested proposal_status`() = runTest {
        var capturedStatus: String? = null
        val api =
            cosmosApi(
                MockEngine { request ->
                    capturedStatus = request.url.parameters["proposal_status"]
                    respond("""{"proposals":[]}""", HttpStatusCode.OK, jsonHeaders)
                }
            )

        val proposals = api.getGovProposals(2)

        assertEquals("2", capturedStatus)
        assertEquals(0, proposals.size)
    }

    @Test
    fun `getGovVote returns the parsed vote on 200`() = runTest {
        val api =
            cosmosApi(
                MockEngine {
                    respond(
                        content =
                            """{"vote":{"options":[{"option":"VOTE_OPTION_YES","weight":"1.0"}]}}""",
                        status = HttpStatusCode.OK,
                        headers = jsonHeaders,
                    )
                }
            )

        val vote = api.getGovVote("1", "qbtc1abc")

        assertEquals("VOTE_OPTION_YES", vote?.options?.first()?.option)
    }

    @Test
    fun `getGovVote returns null on 404 without throwing`() = runTest {
        val api = cosmosApi(MockEngine { respond(content = "", status = HttpStatusCode.NotFound) })

        assertNull(api.getGovVote("1", "qbtc1abc"))
    }

    @Test
    fun `getGovVote returns null on 5xx without throwing`() = runTest {
        val api =
            cosmosApi(
                MockEngine {
                    respond(content = "boom", status = HttpStatusCode.InternalServerError)
                }
            )

        assertNull(api.getGovVote("1", "qbtc1abc"))
    }

    @Test
    fun `getGovVote percent-encodes the voter address`() = runTest {
        var capturedPath: String? = null
        val api =
            cosmosApi(
                MockEngine { request ->
                    capturedPath = request.url.encodedPath
                    respond("""{"vote":null}""", HttpStatusCode.OK, jsonHeaders)
                }
            )

        api.getGovVote("1", "qbtc/abc")

        assertEquals("/cosmos/gov/v1/proposals/1/votes/qbtc%2Fabc", capturedPath)
    }

    private fun cosmosApi(engine: MockEngine): CosmosApi {
        val json = Json { ignoreUnknownKeys = true }
        return CosmosApiImp(
            HttpClient(engine) { install(ContentNegotiation) { json(json) } },
            "https://example.test",
            json,
            CosmosThorChainResponseSerializerImpl(json),
        )
    }
}
