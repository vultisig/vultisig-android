package com.vultisig.wallet.data.blockchain.cosmos.qbtc.claim

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class QbtcClaimChainServiceTest {

    private val jsonHeaders =
        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    private fun client(handler: (path: String) -> Pair<HttpStatusCode, String>): HttpClient =
        HttpClient(
            MockEngine { request ->
                val (status, body) = handler(request.url.encodedPath)
                respond(content = body, status = status, headers = jsonHeaders)
            }
        ) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

    private fun utxoBody(txid: String, entitled: String) =
        """{"utxo":{"txid":"$txid","entitled_amount":"$entitled"}}"""

    @Test
    fun `isClaimWithProofDisabled parses the param value`() = runTest {
        val disabled =
            QbtcClaimChainServiceImpl(
                    client {
                        HttpStatusCode.OK to
                            """{"param":{"key":"ClaimWithProofDisabled","value":"1"}}"""
                    }
                )
                .isClaimWithProofDisabled()
        assertTrue(disabled)

        val enabled =
            QbtcClaimChainServiceImpl(
                    client {
                        HttpStatusCode.OK to
                            """{"param":{"key":"ClaimWithProofDisabled","value":"0"}}"""
                    }
                )
                .isClaimWithProofDisabled()
        assertFalse(enabled)
    }

    @Test
    fun `filterClaimable rewrites amounts, drops claimed and not-indexed, and fails open`() =
        runTest {
            val claimableTxid = "aa".repeat(32)
            val claimedTxid = "bb".repeat(32)
            val notIndexedTxid = "cc".repeat(32)
            val transientTxid = "dd".repeat(32)
            val negativeTxid = "ee".repeat(32)

            val claimable =
                ClaimableUtxo(txid = claimableTxid, vout = 0, amount = 500_000) // chain says 400k
            val claimed =
                ClaimableUtxo(txid = claimedTxid, vout = 1, amount = 300_000) // entitled 0
            val notIndexed = ClaimableUtxo(txid = notIndexedTxid, vout = 2, amount = 200_000) // 404
            val transient =
                ClaimableUtxo(txid = transientTxid, vout = 3, amount = 100_000) // 500 → keep
            val negative =
                ClaimableUtxo(txid = negativeTxid, vout = 4, amount = 50_000) // entitled -1 → drop

            val service =
                QbtcClaimChainServiceImpl(
                    client { path ->
                        when {
                            path.contains(claimableTxid) ->
                                HttpStatusCode.OK to utxoBody(claimableTxid, "400000")
                            path.contains(claimedTxid) ->
                                HttpStatusCode.OK to utxoBody(claimedTxid, "0")
                            path.contains(notIndexedTxid) -> HttpStatusCode.NotFound to ""
                            path.contains(negativeTxid) ->
                                HttpStatusCode.OK to utxoBody(negativeTxid, "-1")
                            else -> HttpStatusCode.InternalServerError to "boom"
                        }
                    }
                )

            val result =
                service.filterClaimable(listOf(claimable, claimed, notIndexed, transient, negative))

            // Input order preserved; claimed + not-indexed + non-positive dropped; claimable amount
            // rewritten; transient failure keeps the UTXO at its original amount.
            assertEquals(listOf(claimable.copy(amount = 400_000), transient), result)
        }
}
