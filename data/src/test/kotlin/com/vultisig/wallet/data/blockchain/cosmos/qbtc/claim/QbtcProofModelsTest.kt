package com.vultisig.wallet.data.blockchain.cosmos.qbtc.claim

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class QbtcProofModelsTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `padSigHex left-pads shorter values to the target width`() {
        assertEquals("0".repeat(44) + "abcd", padSigHex("abcd", 24))
        assertEquals("0".repeat(62) + "ff", padSigHex("ff", 32))
        assertEquals("0".repeat(48), padSigHex("", 24))
    }

    @Test
    fun `padSigHex returns at-width values unchanged`() {
        val exact = "ab".repeat(24)
        assertEquals(exact, padSigHex(exact, 24))
    }

    @Test
    fun `padSigHex never truncates an over-width value`() {
        val over = "ab".repeat(32) // 64 chars, wider than the 24-byte target
        assertEquals(over, padSigHex(over, 24))
    }

    @Test
    fun `ClaimProofRequest serializes to the snake_case wire contract`() {
        val request =
            ClaimProofRequest.create(
                rHex = "deadbeef",
                sHex = "cafebabe",
                compressedPubkeyHex =
                    "0279be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
                utxos = listOf(ClaimableUtxo(txid = "aa".repeat(32), vout = 0, amount = 60_000)),
                claimerAddress = "qbtc1abc",
                chainId = QbtcClaimConfig.CHAIN_ID,
            )
        val encoded = json.encodeToString(ClaimProofRequest.serializer(), request)
        val decoded =
            json.parseToJsonElement(encoded).let { it as kotlinx.serialization.json.JsonObject }

        assertEquals(
            setOf(
                "signature_r",
                "signature_s",
                "public_key",
                "utxos",
                "claimer_address",
                "chain_id",
                "broadcast",
            ),
            decoded.keys,
        )
        // r padded to 24 bytes (48 hex), s padded to 32 bytes (64 hex).
        assertEquals("0".repeat(40) + "deadbeef", request.signatureR)
        assertEquals("0".repeat(56) + "cafebabe", request.signatureS)
        assertTrue(request.broadcast)
        assertEquals("qbtc-testnet", request.chainId)
        assertEquals(1, request.utxos.size)
        assertEquals(0, request.utxos.first().vout)
    }

    @Test
    fun `ClaimProofResponse decodes the proof service body`() {
        val body =
            """
            {
              "proof": "ff00",
              "message_hash": "${"bb".repeat(32)}",
              "address_hash": "${"cc".repeat(20)}",
              "qbtc_address_hash": "${"dd".repeat(32)}",
              "pub_key_hash_sha256": "${"ee".repeat(32)}",
              "utxos": [{"txid": "${"aa".repeat(32)}"}],
              "claimer_address": "qbtc1abc",
              "tx_hash": "${"ab".repeat(32)}"
            }
            """
                .trimIndent()
        val response = json.decodeFromString(ClaimProofResponse.serializer(), body)
        assertEquals("ff00", response.proof)
        assertEquals("bb".repeat(32), response.messageHash)
        assertEquals("cc".repeat(20), response.addressHash)
        assertEquals("dd".repeat(32), response.qbtcAddressHash)
        assertEquals("ab".repeat(32), response.txHash)
    }

    @Test
    fun `ClaimProofResponse tolerates a missing tx_hash`() {
        val body =
            """
            {"proof":"ff00","message_hash":"${"bb".repeat(32)}","address_hash":"${"cc".repeat(20)}","qbtc_address_hash":"${"dd".repeat(32)}"}
            """
                .trimIndent()
        val response = json.decodeFromString(ClaimProofResponse.serializer(), body)
        assertNull(response.txHash)
    }

    @Test
    fun `ProofServiceHealth is healthy only when status and setup agree`() {
        assertTrue(ProofServiceHealth(status = "healthy", setupLoaded = true).isHealthy)
        assertFalse(ProofServiceHealth(status = "degraded", setupLoaded = true).isHealthy)
        assertFalse(ProofServiceHealth(status = "healthy", setupLoaded = false).isHealthy)
    }
}
