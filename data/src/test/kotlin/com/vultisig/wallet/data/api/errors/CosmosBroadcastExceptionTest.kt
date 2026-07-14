package com.vultisig.wallet.data.api.errors

import com.vultisig.wallet.data.api.errors.CosmosBroadcastException.Companion.BROADCAST_FAILURE_MARKER
import com.vultisig.wallet.data.api.errors.CosmosBroadcastException.Companion.SEQUENCE_MISMATCH_MARKER
import com.vultisig.wallet.data.api.errors.CosmosBroadcastException.Companion.UNKNOWN_ADDRESS_MARKER
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pins [CosmosBroadcastException.from] marker selection and [parseCosmosBroadcastResponse]
 * rejection handling for issue #5043: a `codespace=sdk`/`code=9` rejection (fee-payer account
 * doesn't exist on-chain, e.g. a never-funded QBTC vault) must route to [UNKNOWN_ADDRESS_MARKER] so
 * the UI shows a friendly message instead of echoing the node's raw, potentially undecodable
 * `raw_log` address bytes.
 */
class CosmosBroadcastExceptionTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `from tags code=9 codespace=sdk as unknown address`() {
        val error =
            CosmosBroadcastException.from(
                code = 9,
                codespace = "sdk",
                rawLog = "fee payer address: ��� does not exist: unknown address",
                txHash = null,
            )

        assertTrue(error.isUnknownAddress)
        assertFalse(error.isSequenceMismatch)
        assertTrue(error.message?.startsWith(UNKNOWN_ADDRESS_MARKER) == true)
    }

    @Test
    fun `from keeps the raw detail after the marker for the Show exact error disclosure`() {
        val error =
            CosmosBroadcastException.from(
                code = 9,
                codespace = "sdk",
                rawLog = "fee payer address: ��� does not exist: unknown address",
                txHash = null,
            )

        assertEquals(
            "$UNKNOWN_ADDRESS_MARKER: fee payer address: ��� does not exist: unknown address",
            error.message,
        )
    }

    @Test
    fun `from still tags code=32 codespace=sdk as sequence mismatch, not unknown address`() {
        val error =
            CosmosBroadcastException.from(
                code = 32,
                codespace = "sdk",
                rawLog = "account sequence mismatch, expected 5, got 4",
                txHash = null,
            )

        assertTrue(error.isSequenceMismatch)
        assertFalse(error.isUnknownAddress)
        assertTrue(error.message?.startsWith(SEQUENCE_MISMATCH_MARKER) == true)
    }

    @Test
    fun `from falls back to the generic broadcast failure marker for an unrelated code`() {
        val error =
            CosmosBroadcastException.from(
                code = 5,
                codespace = "sdk",
                rawLog = "insufficient funds",
                txHash = null,
            )

        assertFalse(error.isSequenceMismatch)
        assertFalse(error.isUnknownAddress)
        assertTrue(error.message?.startsWith(BROADCAST_FAILURE_MARKER) == true)
        assertFalse(error.message?.startsWith(UNKNOWN_ADDRESS_MARKER) == true)
    }

    @Test
    fun `from does not treat code=9 from a non-sdk codespace as unknown address`() {
        // Only the sdk codespace's registry assigns code 9 to ErrUnknownAddress; a module-specific
        // codespace reusing the same numeric code is an unrelated error.
        val error =
            CosmosBroadcastException.from(
                code = 9,
                codespace = "wasm",
                rawLog = "some wasm-specific error",
                txHash = null,
            )

        assertFalse(error.isUnknownAddress)
    }

    @Test
    fun `parseCosmosBroadcastResponse throws a typed unknown-address exception for a code=9 rejection`() {
        // Mirrors the exact shape reported in issue #5043: HTTP 200 with a rejected tx_response.
        val rawBody =
            """
            {
              "tx_response": {
                "txhash": "25DAFE0000000000000000000000000000000000000000000000000000002C96",
                "code": 9,
                "codespace": "sdk",
                "raw_log": "fee payer address: ��� does not exist: unknown address"
              }
            }
            """
                .trimIndent()

        val error =
            assertThrows(CosmosBroadcastException::class.java) {
                parseCosmosBroadcastResponse(rawBody, "TestTag", json)
            }

        assertEquals(9, error.code)
        assertEquals("sdk", error.codespace)
        assertTrue(error.isUnknownAddress)
        assertTrue(error.message?.startsWith(UNKNOWN_ADDRESS_MARKER) == true)
    }
}
