package com.vultisig.wallet.data.api.models.cosmos

import kotlin.test.assertEquals
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

internal class CosmosTxStatusJsonTest {

    private val json = Json { ignoreUnknownKeys = true }

    // Trimmed real response from `GET /cosmos/tx/v1beta1/txs/{hash}` on a Terra LCD. The LCD
    // serializes gas/raw_log in snake_case; this guards the [TxResponse] @SerialName values so the
    // Terra done-screen fee (gas_used × price) can't silently fall back to null again.
    @Test
    fun `parses snake_case tx_response gas fields`() {
        val body =
            """
            {
              "tx": {},
              "tx_response": {
                "height": "21918262",
                "txhash": "5CE8A7DCA376DB992405C0B26C7E4623F0E512CE8E2DD957F447B6E6C63D3093",
                "code": 0,
                "raw_log": "",
                "gas_wanted": "300000",
                "gas_used": "77779",
                "timestamp": "2026-07-15T02:30:00Z"
              }
            }
            """
                .trimIndent()

        val parsed = json.decodeFromString<CosmosTxStatusJson>(body)
        val resp = parsed.txResponse!!

        assertEquals("77779", resp.gasUsed)
        assertEquals("300000", resp.gasWanted)
        assertEquals(0, resp.code)
        assertEquals("21918262", resp.height)
    }
}
