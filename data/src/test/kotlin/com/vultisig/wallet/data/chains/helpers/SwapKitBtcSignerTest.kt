package com.vultisig.wallet.data.chains.helpers

import com.vultisig.wallet.data.utils.Numeric
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.util.Base64
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import wallet.core.jni.BitcoinScript
import wallet.core.jni.CoinType
import wallet.core.jni.PublicKey
import wallet.core.jni.PublicKeyType

/**
 * Tests for [SwapKitBtcSigner] + [SwapKitPsbtParser].
 *
 * Framing tests are pure JVM (no WalletCore). Decode / sighash / compile tests need the WalletCore
 * JNI (address derivation, HASH160, secp256k1) and skip gracefully when it is unavailable — the
 * same pattern [UtxoHelperTest] uses.
 *
 * The golden decode + sighash vector is the real `v3-real-btc-all-swap` SwapKit fixture, with the
 * BIP-143 sighashes pinned to the values the iOS port asserts — so any byte-level drift in the PSBT
 * decode or sighash transcoding (varint, amount endianness, scriptCode) surfaces here.
 */
class SwapKitBtcSignerTest {

    // A valid compressed secp256k1 pubkey; with an empty chain code PublicKeyHelper returns it
    // as-is (no BIP32 derivation), so it stands in for a vault key in decode/binding tests.
    private val vaultPubKeyHex =
        "025476c2e83188368da1ff3e292e7acafcdb3566bb0ad253f62fc70f07aeee6357"
    private val recipientPubKeyHex =
        "0279be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798"

    @Test
    fun `parseFramingHeader - rejects empty payload`() {
        val e =
            assertThrows(SwapKitPsbtException::class.java) {
                SwapKitPsbtParser.parseFramingHeader(ByteArray(0))
            }
        assertTrue(e.message!!.contains("empty"))
    }

    @Test
    fun `parseFramingHeader - rejects bad magic`() {
        // 'psb' + wrong 4th byte.
        val bad = byteArrayOf(0x70, 0x73, 0x62, 0x00, 0xff.toByte(), 0x00)
        assertThrows(SwapKitPsbtException::class.java) { SwapKitPsbtParser.parseFramingHeader(bad) }
    }

    @Test
    fun `parseFramingHeader - rejects truncated value`() {
        val buf = ByteArrayOutputStream()
        buf.write(MAGIC)
        buf.write(0x01) // keyLen
        buf.write(0x00) // key 0x00
        buf.write(0x05) // valLen = 5
        buf.write(byteArrayOf(0x01, 0x02)) // only 2 bytes present
        assertThrows(SwapKitPsbtException::class.java) {
            SwapKitPsbtParser.parseFramingHeader(buf.toByteArray())
        }
    }

    @Test
    fun `parseFramingHeader - rejects duplicate key in a map`() {
        val buf = ByteArrayOutputStream()
        buf.write(MAGIC)
        // two records with the same key 0x00
        buf.write(byteArrayOf(0x01, 0x00, 0x01, 0xAA.toByte()))
        buf.write(byteArrayOf(0x01, 0x00, 0x01, 0xBB.toByte()))
        buf.write(0x00)
        val e =
            assertThrows(SwapKitPsbtException::class.java) {
                SwapKitPsbtParser.parseFramingHeader(buf.toByteArray())
            }
        assertTrue(e.message!!.contains("duplicate"))
    }

    @Test
    fun `parseFramingHeader - rejects PSBT missing the unsigned-tx global`() {
        val buf = ByteArrayOutputStream()
        buf.write(MAGIC)
        buf.write(0x00) // empty global map (immediate terminator) -> no key 0x00
        val e =
            assertThrows(SwapKitPsbtException::class.java) {
                SwapKitPsbtParser.parseFramingHeader(buf.toByteArray())
            }
        assertTrue(e.message!!.contains("PSBT_GLOBAL_UNSIGNED_TX"))
    }

    @Test
    fun `decode + sighashes - golden v3-real-btc-all-swap vector`() {
        // Pure path: decode(..., null, null) skips the vault-address (JNI) is_change marking, which
        // the BIP-143 sighash math ignores anyway — so this authoritative vector runs headlessly.
        val signer = SwapKitBtcSigner("", "")
        val psbt = Base64.getDecoder().decode(GOLDEN_BTC_PSBT_B64)
        val signBitcoin = signer.decode(psbt, vaultAddress = null, vaultLockScriptHex = null)

        assertEquals(2, signBitcoin.version.toInt())
        assertEquals(0, signBitcoin.locktime.toInt())

        val inputs = signBitcoin.inputs.filterNotNull()
        assertEquals(4, inputs.size)
        inputs.forEach { input ->
            assertTrue(input.isOurs, "every SwapKit BTC input is the user's")
            assertEquals("p2wpkh", input.scriptType)
            assertEquals(0xFFFFFFFFL, input.sequence!!.toLong())
        }

        val outputs = signBitcoin.outputs.filterNotNull()
        assertEquals(1, outputs.size)
        assertEquals(12_466L, outputs[0].amount)
        assertEquals("00147baaaca44c91115ae35dce3410f7395522f1c1aa", outputs[0].scriptPubKey)

        // computeOurSighashes is pure (no JNI) for P2WPKH; pin the BIP-143 sighashes against the
        // values the iOS SwapKitBTCSigner port asserts for this same fixture.
        val hashes =
            UtxoHelper(CoinType.BITCOIN, "", "")
                .computeOurSighashes(signBitcoin)
                .map { Numeric.toHexStringNoPrefix(it) }
                .sorted()
        assertEquals(
            listOf(
                "3725e1553bb43700c74d97edd361ed8538416b106e7f968e43023ac3f2e1e404",
                "447a8a57d19fafa308c3ed817c76e4455b581c77d1b7eef03d85440100ba6b78",
                "73935a24a8dd1df3fb5b6018d7f6d5ad95b3774ea55cbda87f62b6b28ee0f8ba",
                "9a32077b87e4a99bf6942350eb88db33145a28cddb736fb3d2b736c89d7f92c7",
            ),
            hashes,
        )
    }

    @Test
    fun `getPreSignedImageHash - happy path binds deposit to targetAddress and change to vault`() {
        try {
            val vaultAddress = addressFor(vaultPubKeyHex)
            val recipient = addressFor(recipientPubKeyHex)
            val signer = SwapKitBtcSigner(vaultPubKeyHex, "")
            val psbt =
                encodePsbt(
                    inputs =
                        listOf(
                            TestIn(
                                prevTxIdDisplay =
                                    "0000000000000000000000000000000000000000000000000000000000000001",
                                vout = 0,
                                sequence = 0xFFFFFFFFL,
                                amount = 100_000,
                                scriptHex = scriptHexFor(vaultAddress),
                            )
                        ),
                    outputs =
                        listOf(
                            TestOut(amount = 60_000, scriptHex = scriptHexFor(recipient)),
                            TestOut(amount = 39_500, scriptHex = scriptHexFor(vaultAddress)),
                        ),
                )

            val hashes =
                signer.getPreSignedImageHash(
                    psbtBytes = psbt,
                    targetAddress = recipient,
                    fromAmount = BigInteger.valueOf(60_000L),
                )
            assertEquals(1, hashes.size)
        } catch (e: Throwable) {
            skipIfJniUnavailable(e)
        }
    }

    @Test
    fun `getPreSignedImageHash - rejects when deposit amount diverges from fromAmount`() {
        try {
            val vaultAddress = addressFor(vaultPubKeyHex)
            val recipient = addressFor(recipientPubKeyHex)
            val signer = SwapKitBtcSigner(vaultPubKeyHex, "")
            val psbt =
                encodePsbt(
                    inputs =
                        listOf(
                            TestIn(
                                prevTxIdDisplay =
                                    "0000000000000000000000000000000000000000000000000000000000000001",
                                vout = 0,
                                sequence = 0xFFFFFFFFL,
                                amount = 100_000,
                                scriptHex = scriptHexFor(vaultAddress),
                            )
                        ),
                    outputs =
                        listOf(
                            TestOut(amount = 50_000, scriptHex = scriptHexFor(recipient)),
                            TestOut(amount = 49_500, scriptHex = scriptHexFor(vaultAddress)),
                        ),
                )
            // Quote says 60_000 but the PSBT deposits 50_000 — must refuse.
            assertThrows(IllegalArgumentException::class.java) {
                signer.getPreSignedImageHash(
                    psbtBytes = psbt,
                    targetAddress = recipient,
                    fromAmount = BigInteger.valueOf(60_000L),
                )
            }
        } catch (e: Throwable) {
            skipIfJniUnavailable(e)
        }
    }

    private data class TestIn(
        val prevTxIdDisplay: String,
        val vout: Long,
        val sequence: Long,
        val amount: Long,
        val scriptHex: String,
    )

    private data class TestOut(val amount: Long, val scriptHex: String)

    /** Minimal BIP-174 PSBT encoder: magic + global unsigned-tx + per-input WITNESS_UTXO maps. */
    private fun encodePsbt(inputs: List<TestIn>, outputs: List<TestOut>): ByteArray {
        val unsigned = ByteArrayOutputStream()
        unsigned.write(le32(2)) // version
        unsigned.write(varInt(inputs.size.toLong()))
        inputs.forEach { input ->
            unsigned.write(Numeric.hexStringToByteArray(input.prevTxIdDisplay).reversedArray())
            unsigned.write(le32(input.vout))
            unsigned.write(0x00) // empty scriptSig
            unsigned.write(le32(input.sequence))
        }
        unsigned.write(varInt(outputs.size.toLong()))
        outputs.forEach { output ->
            unsigned.write(le64(output.amount))
            val script = Numeric.hexStringToByteArray(output.scriptHex)
            unsigned.write(varInt(script.size.toLong()))
            unsigned.write(script)
        }
        unsigned.write(le32(0)) // locktime

        val psbt = ByteArrayOutputStream()
        psbt.write(MAGIC)
        // global map: key 0x00 -> unsigned tx
        val unsignedBytes = unsigned.toByteArray()
        psbt.write(byteArrayOf(0x01, 0x00)) // keyLen=1, key=0x00
        psbt.write(varInt(unsignedBytes.size.toLong()))
        psbt.write(unsignedBytes)
        psbt.write(0x00) // global map terminator
        // per-input maps: WITNESS_UTXO (key 0x01) = amount(8 LE) + varint(scriptLen) + script
        inputs.forEach { input ->
            val witnessUtxo = ByteArrayOutputStream()
            witnessUtxo.write(le64(input.amount))
            val script = Numeric.hexStringToByteArray(input.scriptHex)
            witnessUtxo.write(varInt(script.size.toLong()))
            witnessUtxo.write(script)
            val wu = witnessUtxo.toByteArray()
            psbt.write(byteArrayOf(0x01, 0x01)) // keyLen=1, key=0x01
            psbt.write(varInt(wu.size.toLong()))
            psbt.write(wu)
            psbt.write(0x00) // input map terminator
        }
        // per-output maps: empty
        outputs.forEach { psbt.write(0x00) }
        return psbt.toByteArray()
    }

    private fun le32(v: Long): ByteArray =
        byteArrayOf(v.toByte(), (v ushr 8).toByte(), (v ushr 16).toByte(), (v ushr 24).toByte())

    private fun le64(v: Long): ByteArray = ByteArray(8) { i -> (v ushr (i * 8)).toByte() }

    private fun varInt(v: Long): ByteArray =
        when {
            v < 0xFDL -> byteArrayOf(v.toByte())
            v <= 0xFFFFL -> byteArrayOf(0xFDu.toByte(), v.toByte(), (v ushr 8).toByte())
            else -> byteArrayOf(0xFEu.toByte()) + le32(v)
        }

    private fun addressFor(pubKeyHex: String): String =
        CoinType.BITCOIN.deriveAddressFromPublicKey(
            PublicKey(Numeric.hexStringToByteArray(pubKeyHex), PublicKeyType.SECP256K1)
        )

    private fun scriptHexFor(address: String): String =
        Numeric.toHexStringNoPrefix(
            BitcoinScript.lockScriptForAddress(address, CoinType.BITCOIN).data()
        )

    private fun skipIfJniUnavailable(e: Throwable) {
        if (
            e is UnsatisfiedLinkError ||
                e is ExceptionInInitializerError ||
                e is NoClassDefFoundError
        ) {
            assumeTrue(false, "WalletCore JNI not available: ${e.message}")
        } else throw e
    }

    private companion object {
        private val MAGIC = byteArrayOf(0x70, 0x73, 0x62, 0x74, 0xff.toByte())

        // Real SwapKit `/v3/swap` PSBT for a NEAR-routed BTC->USDC swap (v3-real-btc-all-swap
        // fixture): 4 P2WPKH inputs, 1 output (12,466 sats to the deposit address).
        private const val GOLDEN_BTC_PSBT_B64 =
            "cHNidP8BAM0CAAAABOVo0TThbPQnZPebtyukLPWJ8zTi8piPdXsze7QTpHekAQAAAAD/////NUFhS3D0" +
                "dVeh3+xkFHoyu6G5vdtD2A3CtoeCcReEMz8AAAAAAP////81QWFLcPR1V6Hf7GQUejK7obm920PYDcK2" +
                "h4JxF4QzPwEAAAAA/////+xBpwbhlGXBc6uhU3v4W6GemcLpliKrby7YspyY1eriAAAAAAD/////AbIw" +
                "AAAAAAAAFgAUe6qspEyREVrjXc40EPc5VSLxwaoAAAAAAAEBH6EhAAAAAAAAFgAU3LjAij6CzvLoWWvp" +
                "sWQSP91VrhsAAQEfeQcAAAAAAAAWABTcuMCKPoLO8uhZa+mxZBI/3VWuGwABAR+HBQAAAAAAABYAFNy4" +
                "wIo+gs7y6Flr6bFkEj/dVa4bAAEBH+gDAAAAAAAAFgAU3LjAij6CzvLoWWvpsWQSP91VrhsAAA=="
    }
}
