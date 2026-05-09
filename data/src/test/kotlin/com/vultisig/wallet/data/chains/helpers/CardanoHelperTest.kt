package com.vultisig.wallet.data.chains.helpers

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.models.payload.UtxoInfo
import java.math.BigInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

/**
 * Regression tests for [CardanoHelper] CIP-20 metadata attachment (issue #4439).
 *
 * The fix attaches user memos to Cardano sends as on-chain CIP-20 auxiliary metadata. These tests
 * cover the pure CBOR encoding helpers (no JNI) and the public pre-image hashing path (JNI, skipped
 * when WalletCore native library is unavailable).
 */
class CardanoHelperTest {

    private val cardanoAddress =
        "addr1q9rhcuy7w8d8tcwfk2zenmgnemc8z2dwhmnnnpwh3scyfk5lvxnz0sqv8tjs8w8zwq2c3kfnvyrzndls60lhsqudnxsq6lykr"

    private val cardanoCoin =
        Coin(
            chain = Chain.Cardano,
            ticker = "ADA",
            logo = "",
            address = cardanoAddress,
            decimal = 6,
            hexPublicKey = "",
            priceProviderID = "",
            contractAddress = "",
            isNativeToken = true,
        )

    private fun cardanoPayload(memo: String?): KeysignPayload =
        KeysignPayload(
            coin = cardanoCoin,
            toAddress = cardanoAddress,
            toAmount = BigInteger.valueOf(1_500_000L),
            blockChainSpecific =
                BlockChainSpecific.Cardano(
                    byteFee = 44L,
                    sendMaxAmount = false,
                    ttl = 100_000_000uL,
                ),
            utxos =
                listOf(
                    UtxoInfo(
                        hash = "4a5e1e4baab89f3a32518a88c31bc87f618f76673e2cc77ab2127b7afdeda33b",
                        amount = 10_000_000L,
                        index = 0u,
                    )
                ),
            memo = memo,
            vaultPublicKeyECDSA = "",
            vaultLocalPartyID = "",
            libType = SigningLibType.GG20,
            wasmExecuteContractPayload = null,
        )

    @Test
    fun `auxDataExtraFee returns 0 when memo is null`() {
        assertEquals(0L, CardanoHelper.auxDataExtraFee(null, byteFee = 44L))
    }

    @Test
    fun `auxDataExtraFee returns 0 when memo is empty`() {
        assertEquals(0L, CardanoHelper.auxDataExtraFee("", byteFee = 44L))
    }

    @Test
    fun `auxDataExtraFee bumps fee when memo is non-empty`() {
        val byteFee = 44L
        val fee = CardanoHelper.auxDataExtraFee("hello", byteFee)
        assertTrue(fee > 0L, "Expected positive fee bump for memo, got $fee")
        // Body grows by 35 bytes; aux tail replaces a 1-byte null. Both scale with byteFee.
        assertEquals(0L, fee % byteFee, "Fee bump must be a multiple of byteFee")
    }

    @Test
    fun `encodeCip20AuxData starts with CBOR tag 259 alonzo metadata wrapper`() {
        val auxData = CardanoHelper.encodeCip20AuxData("hi")
        assertEquals(0xD9.toByte(), auxData[0])
        assertEquals(0x01.toByte(), auxData[1])
        assertEquals(0x03.toByte(), auxData[2])
    }

    @Test
    fun `encodeCip20AuxData embeds memo bytes verbatim in CBOR output`() {
        val memo = "vultisig-cip20-test"
        val auxData = CardanoHelper.encodeCip20AuxData(memo)
        val memoBytes = memo.toByteArray(Charsets.UTF_8)
        assertTrue(
            indexOf(auxData, memoBytes) >= 0,
            "Encoded aux data must contain memo bytes — guards against silent memo drop",
        )
    }

    @Test
    fun `encodeCip20AuxData splits long memos at 64-byte CIP-20 chunk boundary`() {
        val shortMemo = "x".repeat(64)
        val longMemo = "x".repeat(80)
        val shortAux = CardanoHelper.encodeCip20AuxData(shortMemo)
        val longAux = CardanoHelper.encodeCip20AuxData(longMemo)
        // Long memo must produce more bytes than short one due to extra chunk header(s).
        assertTrue(
            longAux.size > shortAux.size,
            "Memos > 64 bytes must produce additional CIP-20 chunks",
        )
    }

    @Test
    fun `appendAuxDataHashToBody increments map size and appends key 7 entry`() {
        // body = map(1) { 0 => 1 } encoded as 0xA1 0x00 0x01
        val body = byteArrayOf(0xA1.toByte(), 0x00, 0x01)
        val hash = ByteArray(32) { 0xAB.toByte() }
        val out = CardanoHelper.appendAuxDataHashToBody(body, hash)
        assertEquals(0xA2.toByte(), out[0], "Map size must grow from 1 to 2")
        assertEquals(0x00.toByte(), out[1], "Original key 0 must be preserved")
        assertEquals(0x01.toByte(), out[2], "Original value must be preserved")
        assertEquals(0x07.toByte(), out[3], "Appended key must be 7 (auxiliary_data_hash)")
        assertEquals(0x58.toByte(), out[4], "Appended value must be CBOR bytes(1-byte length)")
        assertEquals(32, out[5].toInt() and 0xFF, "Appended bytes length must be 32")
        assertTrue(
            out.copyOfRange(6, 6 + 32).all { it == 0xAB.toByte() },
            "Appended hash bytes must be preserved",
        )
    }

    @Test
    fun `appendAuxDataHashToBody rejects non-32-byte hash`() {
        val body = byteArrayOf(0xA1.toByte(), 0x00, 0x01)
        try {
            CardanoHelper.appendAuxDataHashToBody(body, ByteArray(31))
            error("Expected IllegalArgumentException for non-32-byte hash")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun `encodeVKeyWitnessSet emits map with vkey and signature entries`() {
        val pub = ByteArray(32) { 0x11 }
        val sig = ByteArray(64) { 0x22 }
        val ws = CardanoHelper.encodeVKeyWitnessSet(pub, sig)
        assertEquals(0xA1.toByte(), ws[0], "map(1)")
        assertEquals(0x00.toByte(), ws[1], "key 0 (vkeywitnesses)")
        assertEquals(0x81.toByte(), ws[2], "array(1)")
        assertEquals(0x82.toByte(), ws[3], "array(2) — [vkey, signature]")
        assertEquals(0x58.toByte(), ws[4], "vkey: bytes(32) header")
        assertEquals(32, ws[5].toInt() and 0xFF)
        assertEquals(0x58.toByte(), ws[6 + 32], "signature: bytes(64) header")
        assertEquals(64, ws[7 + 32].toInt() and 0xFF)
    }

    @Test
    fun `encodeSignedTx wraps body witnesses and aux as CBOR array of 4`() {
        val body = byteArrayOf(0x01)
        val ws = byteArrayOf(0x02)
        val aux = byteArrayOf(0x03)
        val signed = CardanoHelper.encodeSignedTx(body, ws, aux)
        assertEquals(0x84.toByte(), signed[0], "array(4)")
        assertEquals(0x01.toByte(), signed[1], "body")
        assertEquals(0x02.toByte(), signed[2], "witness set")
        assertEquals(0xF5.toByte(), signed[3], "is_valid = true")
        assertEquals(0x03.toByte(), signed[4], "auxiliary data")
    }

    // JNI-dependent regression test — exercises the full pre-sign path.
    @Test
    fun `getPreSignedImageHash differs when memo is attached`() {
        val noMemo = cardanoPayload(memo = null)
        val withMemo = cardanoPayload(memo = "vultisig regression test memo")
        try {
            val hashNoMemo = CardanoHelper.getPreSignedImageHash(noMemo).single()
            val hashWithMemo = CardanoHelper.getPreSignedImageHash(withMemo).single()
            assertNotEquals(
                hashNoMemo,
                hashWithMemo,
                "Pre-image hash must change when memo is attached — otherwise CIP-20 metadata isn't being committed to the body",
            )
        } catch (e: Throwable) {
            skipIfJniUnavailable(e)
        }
    }

    private fun indexOf(haystack: ByteArray, needle: ByteArray): Int {
        if (needle.isEmpty() || needle.size > haystack.size) return -1
        outer@ for (i in 0..haystack.size - needle.size) {
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) continue@outer
            }
            return i
        }
        return -1
    }

    private fun skipIfJniUnavailable(e: Throwable) {
        if (
            e is UnsatisfiedLinkError ||
                e is ExceptionInInitializerError ||
                e is NoClassDefFoundError
        ) {
            assumeTrue(false, "WalletCore JNI not available: ${e.message}")
        } else throw e
    }
}
