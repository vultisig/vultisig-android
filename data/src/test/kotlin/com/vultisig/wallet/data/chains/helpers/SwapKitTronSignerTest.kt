package com.vultisig.wallet.data.chains.helpers

import com.vultisig.wallet.data.utils.Numeric
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Tests for [SwapKitTronSigner]'s pure (no-JNI) surface: the signing digest, the single-entry
 * pre-signed hash, and the broadcast-envelope assembly.
 *
 * The golden vector is the real `v3-tron-final-swap-fresh` SwapKit fixture (a TRC-20 USDT→USDC
 * route via NEAR). Tron's `txID` IS `sha256(raw_data_bytes)`, and SwapKit reports both — so pinning
 * the digest to the fixture's `txID` catches any drift in how `raw_data_hex` is decoded and hashed.
 * Signature verification needs the WalletCore JNI (secp256k1) and is covered by the iOS port +
 * integration; these tests stay headless like the framing half of [SwapKitBtcSignerTest].
 */
class SwapKitTronSignerTest {

    private val signer = SwapKitTronSigner(vaultHexPublicKey = "", vaultHexChainCode = "")

    private fun payloadBytes(rawDataHex: String, txId: String = TX_ID): ByteArray =
        """{"visible":true,"txID":"$txId","raw_data_hex":"$rawDataHex"}""".encodeToByteArray()

    @Test
    fun `digest equals sha256 of raw_data_hex and matches txID`() {
        val digestHex = Numeric.toHexStringNoPrefix(signer.digest(payloadBytes(RAW_DATA_HEX)))
        assertEquals(
            TX_ID,
            digestHex,
            "Tron signing digest must equal sha256(raw_data_bytes) == txID",
        )
    }

    @Test
    fun `pre-signed image hash is a single entry equal to the digest`() {
        val hashes = signer.getPreSignedImageHash(payloadBytes(RAW_DATA_HEX))
        assertEquals(1, hashes.size)
        assertEquals(TX_ID, hashes[0])
    }

    @Test
    fun `malformed JSON payload is rejected`() {
        assertThrows(SwapKitTronSignerException::class.java) {
            signer.digest("not json".encodeToByteArray())
        }
    }

    @Test
    fun `missing raw_data_hex is rejected`() {
        assertThrows(SwapKitTronSignerException::class.java) {
            signer.digest("""{"txID":"$TX_ID","visible":true}""".encodeToByteArray())
        }
    }

    @Test
    fun `empty payload is rejected`() {
        assertThrows(SwapKitTronSignerException::class.java) { signer.digest(ByteArray(0)) }
    }

    @Test
    fun `broadcast envelope appends the signature and preserves the original fields`() {
        val signatureHex = "aa".repeat(65)
        val envelope = signer.makeBroadcastEnvelope(payloadBytes(RAW_DATA_HEX), signatureHex)
        val obj = Json.parseToJsonElement(envelope) as JsonObject

        // signature appended as a single-element hex array (TronWeb broadcast shape).
        val sig = requireNotNull(obj["signature"]) { "signature field is missing" }.jsonArray
        assertEquals(1, sig.size)
        assertEquals(signatureHex, sig[0].jsonPrimitive.content)
        // Original canonical fields survive verbatim so the cosigning peer broadcasts the same tx.
        assertEquals(
            TX_ID,
            requireNotNull(obj["txID"]) { "txID field is missing" }.jsonPrimitive.content,
        )
        assertEquals(
            RAW_DATA_HEX,
            requireNotNull(obj["raw_data_hex"]) { "raw_data_hex field is missing" }
                .jsonPrimitive
                .content,
        )
        assertTrue(
            requireNotNull(obj["visible"]) { "visible field is missing" }
                .jsonPrimitive
                .content
                .toBoolean()
        )
    }

    private companion object {
        // Real SwapKit `v3-tron-final-swap-fresh` fixture: TRC-20 USDT transfer via
        // TriggerSmartContract.
        private const val RAW_DATA_HEX =
            "0a028975220898cc46b34632500840b8e093aee4335aae01081f12a9010a31747970652e676f6f676c65" +
                "617069732e636f6d2f70726f746f636f6c2e54726967676572536d617274436f6e747261637412740a" +
                "154170082243784dcdf3042034e7b044d6d342a91360121541a614f803b6fd780986a42c78ec9c7f77e" +
                "6ded13c2244a9059cbb000000000000000000000000cd606df761d75d06a716ef7af816b0a553306f5a" +
                "0000000000000000000000000000000000000000000000000000000002faf08070b0ca81aee43390018" +
                "0ade204"
        private const val TX_ID = "90788bbae2f83d278b5f13a9b39e26a294d9319bf7ea8bb69b8dbf32b1c61133"
    }
}
