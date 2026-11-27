package com.vultisig.wallet.data.api.models.thorchain

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

// Provider entries captured from /thorchain/node/thor10tsu3gfzgywyzuuyvmjmlpehdupdcrczemne9y on
// 2026-11-26: THORNode omits `bps` when Bond Yield is unset.
internal class NodeDetailsPayoutBpsTest {

    private val details =
        Json { ignoreUnknownKeys = true }
            .decodeFromString<NodeDetailsResponse>(
                """
                {
                  "node_address": "thor10tsu3gfzgywyzuuyvmjmlpehdupdcrczemne9y",
                  "status": "Active",
                  "current_award": "0",
                  "bond_providers": {
                    "node_operator_fee": "2000",
                    "providers": [
                      {"bond_address": "thor1kuvaypgrdqpwjccdeprvzjkwf59pgy8xyx7pp5", "bond": "4505368474649", "bps": 10000, "payout_target": "knbm"},
                      {"bond_address": "thor1456ja57j5wasf23mhgjpskq8mwhxzxejplh0z5", "bond": "100692753308382"}
                    ]
                  }
                }
                """
            )

    @Test
    fun `reads the share a provider set`() {
        assertEquals(10000, details.payoutBps("thor1kuvaypgrdqpwjccdeprvzjkwf59pgy8xyx7pp5"))
    }

    @Test
    fun `treats an omitted share as zero`() {
        assertEquals(0, details.payoutBps("thor1456ja57j5wasf23mhgjpskq8mwhxzxejplh0z5"))
    }

    @Test
    fun `has no share when the address is not a provider`() {
        assertNull(details.payoutBps("thor1x2whgc2nt665y0kc44uywhynazvp0l8tp0vtu6"))
    }
}
