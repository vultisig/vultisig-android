package com.vultisig.wallet.data.chains.helpers

import com.vultisig.wallet.data.utils.Numeric
import java.io.ByteArrayOutputStream
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import wallet.core.jni.CoinType
import wallet.core.jni.proto.Bitcoin
import wallet.core.jni.proto.Common.SigningError

/**
 * Tests for [SwapKitLegacyP2PKHSigner] (DOGE / BCH / DASH legacy-P2PKH PSBT signing).
 *
 * The PSBT parsing + structural validation (P2PKH shape, output count, fee sign, prev-UTXO
 * presence) is pure JVM and runs headlessly — those rejections fire before any WalletCore call. The
 * presigning happy path needs the WalletCore JNI (frozen plan → `TransactionCompiler`, address
 * derivation) and skips gracefully when it is unavailable, the same pattern [SwapKitBtcSignerTest]
 * uses.
 */
class SwapKitLegacyP2PKHSignerTest {

    private fun signer(coin: CoinType = CoinType.DOGECOIN) = SwapKitLegacyP2PKHSigner("", "", coin)

    @Test
    fun `rejects empty payload`() {
        val e =
            assertThrows(SwapKitLegacyP2PKHSignerException::class.java) {
                signer().buildSigningInputData(ByteArray(0), "")
            }
        assertTrue(e.message!!.contains("empty"))
    }

    @Test
    fun `rejects a non-P2PKH input scriptPubKey`() {
        // A P2WPKH input (segwit) is not a legacy P2PKH — must be refused before any signing.
        val psbt =
            encodeLegacyPsbt(
                inputs =
                    listOf(
                        LegacyIn(
                            prevTxIdDisplay = TXID_ONE,
                            vout = 0,
                            sequence = 0xFFFFFFFFL,
                            amount = 100_000,
                            prevScriptHex = "0014" + "11".repeat(20), // P2WPKH, not P2PKH
                        )
                    ),
                outputs = listOf(LegacyOut(amount = 99_000, scriptHex = p2pkh("33".repeat(20)))),
            )
        val e =
            assertThrows(SwapKitLegacyP2PKHSignerException::class.java) {
                signer().buildSigningInputData(psbt, "")
            }
        assertTrue(e.message!!.contains("input #0"))
        assertTrue(e.message!!.contains("not P2PKH"))
    }

    @Test
    fun `rejects a non-P2PKH output`() {
        val psbt =
            encodeLegacyPsbt(
                inputs =
                    listOf(
                        LegacyIn(
                            prevTxIdDisplay = TXID_ONE,
                            vout = 0,
                            sequence = 0xFFFFFFFFL,
                            amount = 100_000,
                            prevScriptHex = p2pkh("11".repeat(20)),
                        )
                    ),
                // OP_RETURN-style output — not P2PKH, can't be re-emitted through the address API.
                outputs = listOf(LegacyOut(amount = 99_000, scriptHex = "6a04deadbeef")),
            )
        val e =
            assertThrows(SwapKitLegacyP2PKHSignerException::class.java) {
                signer().buildSigningInputData(psbt, "")
            }
        assertTrue(e.message!!.contains("output #0"))
        assertTrue(e.message!!.contains("not P2PKH"))
    }

    @Test
    fun `rejects more than two outputs`() {
        val psbt =
            encodeLegacyPsbt(
                inputs =
                    listOf(
                        LegacyIn(
                            prevTxIdDisplay = TXID_ONE,
                            vout = 0,
                            sequence = 0xFFFFFFFFL,
                            amount = 100_000,
                            prevScriptHex = p2pkh("11".repeat(20)),
                        )
                    ),
                outputs =
                    listOf(
                        LegacyOut(amount = 30_000, scriptHex = p2pkh("22".repeat(20))),
                        LegacyOut(amount = 30_000, scriptHex = p2pkh("33".repeat(20))),
                        LegacyOut(amount = 30_000, scriptHex = p2pkh("44".repeat(20))),
                    ),
            )
        val e =
            assertThrows(SwapKitLegacyP2PKHSignerException::class.java) {
                signer().buildSigningInputData(psbt, "")
            }
        assertTrue(e.message!!.contains("3 outputs"))
    }

    @Test
    fun `rejects negative fee`() {
        val psbt =
            encodeLegacyPsbt(
                inputs =
                    listOf(
                        LegacyIn(
                            prevTxIdDisplay = TXID_ONE,
                            vout = 0,
                            sequence = 0xFFFFFFFFL,
                            amount = 50_000,
                            prevScriptHex = p2pkh("11".repeat(20)),
                        )
                    ),
                // Outputs exceed inputs → fee would be negative.
                outputs = listOf(LegacyOut(amount = 60_000, scriptHex = p2pkh("22".repeat(20)))),
            )
        val e =
            assertThrows(SwapKitLegacyP2PKHSignerException::class.java) {
                signer().buildSigningInputData(psbt, "")
            }
        assertTrue(e.message!!.contains("negative fee"))
    }

    @Test
    fun `rejects a missing prev-tx UTXO record`() {
        // Input map present but carries neither NON_WITNESS_UTXO (0x00) nor WITNESS_UTXO (0x01).
        val psbt =
            encodeLegacyPsbt(
                inputs =
                    listOf(
                        LegacyIn(
                            prevTxIdDisplay = TXID_ONE,
                            vout = 0,
                            sequence = 0xFFFFFFFFL,
                            amount = 100_000,
                            prevScriptHex = p2pkh("11".repeat(20)),
                            omitPrevUtxo = true,
                        )
                    ),
                outputs = listOf(LegacyOut(amount = 99_000, scriptHex = p2pkh("33".repeat(20)))),
            )
        val e =
            assertThrows(SwapKitLegacyP2PKHSignerException::class.java) {
                signer().buildSigningInputData(psbt, "")
            }
        assertTrue(e.message!!.contains("missing a prev-tx UTXO record"))
    }

    @Test
    fun `rejects an unsigned-tx input carrying a non-empty scriptSig`() {
        val psbt =
            encodeLegacyPsbt(
                inputs =
                    listOf(
                        LegacyIn(
                            prevTxIdDisplay = TXID_ONE,
                            vout = 0,
                            sequence = 0xFFFFFFFFL,
                            amount = 100_000,
                            prevScriptHex = p2pkh("11".repeat(20)),
                            unsignedScriptSigHex = "ab",
                        )
                    ),
                outputs = listOf(LegacyOut(amount = 99_000, scriptHex = p2pkh("33".repeat(20)))),
            )
        val e =
            assertThrows(SwapKitLegacyP2PKHSignerException::class.java) {
                signer().buildSigningInputData(psbt, "")
            }
        assertTrue(e.message!!.contains("scriptSig must be empty"))
    }

    @Test
    fun `rejects an unsigned-tx body with trailing bytes`() {
        val psbt =
            encodeLegacyPsbt(
                inputs =
                    listOf(
                        LegacyIn(
                            prevTxIdDisplay = TXID_ONE,
                            vout = 0,
                            sequence = 0xFFFFFFFFL,
                            amount = 100_000,
                            prevScriptHex = p2pkh("11".repeat(20)),
                        )
                    ),
                outputs = listOf(LegacyOut(amount = 99_000, scriptHex = p2pkh("33".repeat(20)))),
                unsignedTxTrailerHex = "ff",
            )
        val e =
            assertThrows(SwapKitLegacyP2PKHSignerException::class.java) {
                signer().buildSigningInputData(psbt, "")
            }
        assertTrue(e.message!!.contains("trailing bytes"))
    }

    @Test
    fun `getPreSignedImageHash - DOGE happy path returns one sighash per input (WITNESS_UTXO)`() {
        try {
            val psbt =
                encodeLegacyPsbt(
                    inputs =
                        listOf(
                            LegacyIn(
                                prevTxIdDisplay = TXID_ONE,
                                vout = 0,
                                sequence = 0xFFFFFFFFL,
                                amount = 100_000,
                                prevScriptHex = p2pkh("11".repeat(20)),
                            )
                        ),
                    outputs =
                        listOf(
                            LegacyOut(amount = 60_000, scriptHex = p2pkh("22".repeat(20))),
                            LegacyOut(amount = 39_000, scriptHex = p2pkh("11".repeat(20))),
                        ),
                )
            val hashes = signer(CoinType.DOGECOIN).getPreSignedImageHash(psbt, "")
            assertEquals(1, hashes.size)
            assertEquals(64, hashes[0].length)
        } catch (e: Throwable) {
            skipIfJniUnavailable(e)
        }
    }

    @Test
    fun `getPreSignedImageHash - DASH happy path with two inputs (NON_WITNESS_UTXO)`() {
        try {
            val prevScript = p2pkh("11".repeat(20))
            val psbt =
                encodeLegacyPsbt(
                    inputs =
                        listOf(
                            LegacyIn(
                                prevTxIdDisplay = TXID_ONE,
                                vout = 0,
                                sequence = 0xFFFFFFFFL,
                                amount = 70_000,
                                prevScriptHex = prevScript,
                                useNonWitnessUtxo = true,
                            ),
                            LegacyIn(
                                prevTxIdDisplay = TXID_TWO,
                                vout = 1,
                                sequence = 0xFFFFFFFFL,
                                amount = 40_000,
                                prevScriptHex = prevScript,
                                useNonWitnessUtxo = true,
                            ),
                        ),
                    outputs =
                        listOf(
                            LegacyOut(amount = 100_000, scriptHex = p2pkh("22".repeat(20))),
                            LegacyOut(amount = 9_000, scriptHex = prevScript),
                        ),
                )
            val hashes = signer(CoinType.DASH).getPreSignedImageHash(psbt, "")
            // One sighash per signable input.
            assertEquals(2, hashes.size)
            // Sorted, distinct, 32-byte hex digests.
            assertEquals(hashes.sorted(), hashes)
            assertEquals(2, hashes.toSet().size)
        } catch (e: Throwable) {
            skipIfJniUnavailable(e)
        }
    }

    @Test
    fun `buildSigningInputData - frozen plan reproduces the PSBT inputs, outputs, lockTime (DOGE)`() {
        try {
            // The frozen Bitcoin.SigningInput is what WalletCore compiles into the broadcast tx, so
            // pinning every field it carries pins the reconstruction the PR leans on. Building it
            // needs the WalletCore JNI (address derivation, script building); parsing the proto
            // back
            // is pure, and the expectations are derived from the PSBT — no precomputed golden
            // needed.
            val depositHash = "22".repeat(20)
            val changeHash = "11".repeat(20)
            val psbt =
                encodeLegacyPsbt(
                    inputs =
                        listOf(
                            LegacyIn(
                                prevTxIdDisplay = TXID_ONE,
                                vout = 3,
                                sequence = 0xFFFFFFFEL,
                                amount = 100_000,
                                prevScriptHex = p2pkh(changeHash),
                            )
                        ),
                    outputs =
                        listOf(
                            LegacyOut(amount = 60_000, scriptHex = p2pkh(depositHash)),
                            LegacyOut(amount = 39_000, scriptHex = p2pkh(changeHash)),
                        ),
                    lockTime = 770_000,
                )

            val input =
                Bitcoin.SigningInput.parseFrom(
                    signer(CoinType.DOGECOIN).buildSigningInputData(psbt, "")
                )

            // lockTime threaded through (the review fix) so the rebuilt tx_id matches the PSBT.
            assertEquals(770_000, input.lockTime)

            // Frozen plan: amounts/fee derived verbatim from the PSBT, never replanned.
            assertEquals(SigningError.OK, input.plan.error)
            assertEquals(100_000L, input.plan.availableAmount)
            assertEquals(60_000L, input.plan.amount)
            assertEquals(39_000L, input.plan.change)
            assertEquals(1_000L, input.plan.fee)

            // The single UTXO is re-emitted verbatim: outpoint (LE hash + index + sequence),
            // amount,
            // and the exact prevout scriptPubKey.
            assertEquals(1, input.utxoCount)
            val utxo = input.getUtxo(0)
            assertArrayEquals(
                Numeric.hexStringToByteArray(TXID_ONE).reversedArray(),
                utxo.outPoint.hash.toByteArray(),
            )
            assertEquals(3, utxo.outPoint.index)
            assertEquals(0xFFFFFFFE.toInt(), utxo.outPoint.sequence)
            assertEquals(100_000L, utxo.amount)
            assertEquals(p2pkh(changeHash), Numeric.toHexStringNoPrefix(utxo.script.toByteArray()))

            // toAddress/changeAddress are derived from the PSBT's own output hash160s (DOGE `D…`),
            // so WalletCore re-emits the exact output scripts the PSBT shipped.
            assertTrue(input.toAddress.startsWith("D"), "deposit ${input.toAddress}")
            assertTrue(input.changeAddress.startsWith("D"), "change ${input.changeAddress}")
        } catch (e: Throwable) {
            skipIfJniUnavailable(e)
        }
    }

    private data class LegacyIn(
        val prevTxIdDisplay: String,
        val vout: Long,
        val sequence: Long,
        val amount: Long,
        val prevScriptHex: String,
        val useNonWitnessUtxo: Boolean = false,
        val omitPrevUtxo: Boolean = false,
        val unsignedScriptSigHex: String? = null,
    )

    private data class LegacyOut(val amount: Long, val scriptHex: String)

    /**
     * P2PKH scriptPubKey for a 20-byte hash160 hex: `OP_DUP OP_HASH160 PUSH20 <hash> EQ CHECKSIG`.
     */
    private fun p2pkh(hash20Hex: String): String = "76a914$hash20Hex" + "88ac"

    /**
     * Minimal BIP-174 PSBT encoder for a **legacy** (non-segwit) tx: magic + global unsigned-tx +
     * per-input WITNESS_UTXO or NON_WITNESS_UTXO maps. The unsigned-tx body has no marker/flag.
     */
    private fun encodeLegacyPsbt(
        inputs: List<LegacyIn>,
        outputs: List<LegacyOut>,
        lockTime: Long = 0,
        unsignedTxTrailerHex: String? = null,
    ): ByteArray {
        val unsigned = ByteArrayOutputStream()
        unsigned.write(le32(1)) // version
        unsigned.write(varInt(inputs.size.toLong()))
        inputs.forEach { input ->
            unsigned.write(Numeric.hexStringToByteArray(input.prevTxIdDisplay).reversedArray())
            unsigned.write(le32(input.vout))
            val scriptSig =
                input.unsignedScriptSigHex?.let { Numeric.hexStringToByteArray(it) } ?: ByteArray(0)
            unsigned.write(varInt(scriptSig.size.toLong()))
            unsigned.write(scriptSig)
            unsigned.write(le32(input.sequence))
        }
        unsigned.write(varInt(outputs.size.toLong()))
        outputs.forEach { output ->
            unsigned.write(le64(output.amount))
            val script = Numeric.hexStringToByteArray(output.scriptHex)
            unsigned.write(varInt(script.size.toLong()))
            unsigned.write(script)
        }
        unsigned.write(le32(lockTime))
        unsignedTxTrailerHex?.let { unsigned.write(Numeric.hexStringToByteArray(it)) }

        val psbt = ByteArrayOutputStream()
        psbt.write(MAGIC)
        val unsignedBytes = unsigned.toByteArray()
        psbt.write(byteArrayOf(0x01, 0x00)) // global key 0x00
        psbt.write(varInt(unsignedBytes.size.toLong()))
        psbt.write(unsignedBytes)
        psbt.write(0x00) // global map terminator

        inputs.forEach { input ->
            if (!input.omitPrevUtxo) {
                if (input.useNonWitnessUtxo) {
                    // NON_WITNESS_UTXO (key 0x00): a full prev-tx whose output[vout] is the UTXO.
                    val prevTx = encodePrevTx(input.vout, input.amount, input.prevScriptHex)
                    psbt.write(byteArrayOf(0x01, 0x00))
                    psbt.write(varInt(prevTx.size.toLong()))
                    psbt.write(prevTx)
                } else {
                    // WITNESS_UTXO (key 0x01): amount(8 LE) + varint(scriptLen) + scriptPubKey.
                    val witnessUtxo = ByteArrayOutputStream()
                    witnessUtxo.write(le64(input.amount))
                    val script = Numeric.hexStringToByteArray(input.prevScriptHex)
                    witnessUtxo.write(varInt(script.size.toLong()))
                    witnessUtxo.write(script)
                    val wu = witnessUtxo.toByteArray()
                    psbt.write(byteArrayOf(0x01, 0x01))
                    psbt.write(varInt(wu.size.toLong()))
                    psbt.write(wu)
                }
            }
            psbt.write(0x00) // input map terminator
        }
        outputs.forEach { psbt.write(0x00) } // empty per-output maps
        return psbt.toByteArray()
    }

    /**
     * A minimal legacy prev-transaction whose output [vout] carries [amount]/[scriptHex]; padding
     * outputs before [vout] are tiny P2PKH so the parser walks past them to the target.
     */
    private fun encodePrevTx(vout: Long, amount: Long, scriptHex: String): ByteArray {
        val tx = ByteArrayOutputStream()
        tx.write(le32(1)) // version
        tx.write(varInt(1)) // one input
        tx.write(ByteArray(32)) // prev txid (zeros)
        tx.write(le32(0)) // prev vout
        tx.write(varInt(0)) // empty scriptSig
        tx.write(le32(0xFFFFFFFFL)) // sequence
        val outCount = vout + 1
        tx.write(varInt(outCount))
        for (i in 0 until outCount) {
            if (i == vout) {
                tx.write(le64(amount))
                val script = Numeric.hexStringToByteArray(scriptHex)
                tx.write(varInt(script.size.toLong()))
                tx.write(script)
            } else {
                tx.write(le64(1_000))
                val pad = Numeric.hexStringToByteArray(p2pkh("00".repeat(20)))
                tx.write(varInt(pad.size.toLong()))
                tx.write(pad)
            }
        }
        tx.write(le32(0)) // locktime
        return tx.toByteArray()
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
        private const val TXID_ONE =
            "0000000000000000000000000000000000000000000000000000000000000001"
        private const val TXID_TWO =
            "0000000000000000000000000000000000000000000000000000000000000002"
    }
}
