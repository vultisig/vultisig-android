package com.vultisig.wallet.data.chains.helpers

import com.vultisig.wallet.data.utils.Numeric
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import wallet.core.jni.proto.Bitcoin
import wallet.core.jni.proto.Common.SigningError

/**
 * Tests for [SwapKitZcashSigner] (transparent ZEC Sapling-v4 PSBT signing).
 *
 * The Sapling-v4 body parsing + structural validation (version/group gate, shielded-field
 * rejection, P2PKH shape, output count, fee sign) is pure JVM and runs headlessly — those
 * rejections fire before any WalletCore call. The presigning happy path needs the WalletCore JNI
 * (frozen plan + branch id → `TransactionCompiler`, t1 address derivation) and skips gracefully
 * when it is unavailable.
 */
class SwapKitZcashSignerTest {

    private fun signer() = SwapKitZcashSigner("", "")

    @Test
    fun `rejects empty payload`() {
        val e =
            assertThrows(SwapKitZcashSignerException::class.java) {
                signer().buildSigningInputData(ByteArray(0), "", FROM_AMOUNT)
            }
        assertTrue(e.message!!.contains("empty"))
    }

    @Test
    fun `rejects a non-Sapling-v4 version group (NU5)`() {
        val psbt =
            encodeSaplingPsbt(
                versionGroupId = NU5_VERSION_GROUP_ID,
                inputs = listOf(saplingIn(amount = 100_000)),
                outputs = listOf(SaplingOut(99_000, p2pkh("22".repeat(20)))),
            )
        val e =
            assertThrows(SwapKitZcashSignerException::class.java) {
                signer().buildSigningInputData(psbt, "", FROM_AMOUNT)
            }
        assertTrue(e.message!!.contains("unsupported Zcash version"))
    }

    @Test
    fun `rejects a shielded bundle (non-zero valueBalance)`() {
        val psbt =
            encodeSaplingPsbt(
                inputs = listOf(saplingIn(amount = 100_000)),
                outputs = listOf(SaplingOut(99_000, p2pkh("22".repeat(20)))),
                valueBalance =
                    1, // any non-zero shielded value is unsignable via MPC transparent path
            )
        val e =
            assertThrows(SwapKitZcashSignerException::class.java) {
                signer().buildSigningInputData(psbt, "", FROM_AMOUNT)
            }
        assertTrue(e.message!!.contains("shielded"))
    }

    @Test
    fun `rejects a shielded bundle (non-zero spend count)`() {
        val psbt =
            encodeSaplingPsbt(
                inputs = listOf(saplingIn(amount = 100_000)),
                outputs = listOf(SaplingOut(99_000, p2pkh("22".repeat(20)))),
                nShieldedSpend = 1,
            )
        val e =
            assertThrows(SwapKitZcashSignerException::class.java) {
                signer().buildSigningInputData(psbt, "", FROM_AMOUNT)
            }
        assertTrue(e.message!!.contains("shielded"))
    }

    @Test
    fun `rejects a non-P2PKH input scriptPubKey`() {
        val psbt =
            encodeSaplingPsbt(
                inputs =
                    listOf(saplingIn(amount = 100_000, prevScriptHex = "0014" + "11".repeat(20))),
                outputs = listOf(SaplingOut(99_000, p2pkh("22".repeat(20)))),
            )
        val e =
            assertThrows(SwapKitZcashSignerException::class.java) {
                signer().buildSigningInputData(psbt, "", FROM_AMOUNT)
            }
        assertTrue(e.message!!.contains("input #0"))
        assertTrue(e.message!!.contains("not P2PKH"))
    }

    @Test
    fun `rejects a non-zero expiryHeight`() {
        // WalletCore can't reproduce a non-zero expiryHeight in the rebuilt tx, so a route shipping
        // one is rejected loudly rather than mistracked under a diverging tx_id.
        val psbt =
            encodeSaplingPsbt(
                inputs = listOf(saplingIn(amount = 100_000)),
                outputs = listOf(SaplingOut(99_000, p2pkh("22".repeat(20)))),
                expiryHeight = 1_000_000,
            )
        val e =
            assertThrows(SwapKitZcashSignerException::class.java) {
                signer().buildSigningInputData(psbt, "", FROM_AMOUNT)
            }
        assertTrue(e.message!!.contains("expiryHeight"))
    }

    @Test
    fun `rejects an unsigned-tx input carrying a non-empty scriptSig`() {
        val psbt =
            encodeSaplingPsbt(
                inputs = listOf(saplingIn(amount = 100_000, unsignedScriptSigHex = "ab")),
                outputs = listOf(SaplingOut(99_000, p2pkh("22".repeat(20)))),
            )
        val e =
            assertThrows(SwapKitZcashSignerException::class.java) {
                signer().buildSigningInputData(psbt, "", FROM_AMOUNT)
            }
        assertTrue(e.message!!.contains("scriptSig must be empty"))
    }

    @Test
    fun `rejects a miner fee that exceeds the quoted swap amount`() {
        // inputs 100_000, single 10_000 output → 90_000 fee. A quoted swap amount of only 1_000
        // makes the fee dwarf it, so the ceiling refuses to sign (the balance-burn guard).
        val psbt =
            encodeSaplingPsbt(
                inputs = listOf(saplingIn(amount = 100_000)),
                outputs = listOf(SaplingOut(10_000, p2pkh("22".repeat(20)))),
            )
        val e =
            assertThrows(SwapKitZcashSignerException::class.java) {
                signer().buildSigningInputData(psbt, "", BigInteger.valueOf(1_000))
            }
        assertTrue(e.message!!.contains("exceeds the quoted swap amount"))
    }

    @Test
    fun `rejects an unsigned-tx body with trailing bytes`() {
        val psbt =
            encodeSaplingPsbt(
                inputs = listOf(saplingIn(amount = 100_000)),
                outputs = listOf(SaplingOut(99_000, p2pkh("22".repeat(20)))),
                unsignedTxTrailerHex = "ff",
            )
        val e =
            assertThrows(SwapKitZcashSignerException::class.java) {
                signer().buildSigningInputData(psbt, "", FROM_AMOUNT)
            }
        assertTrue(e.message!!.contains("trailing bytes"))
    }

    @Test
    fun `getPreSignedImageHash - transparent happy path returns one sighash per input`() {
        try {
            val psbt =
                encodeSaplingPsbt(
                    inputs = listOf(saplingIn(amount = 100_000)),
                    outputs =
                        listOf(
                            SaplingOut(60_000, p2pkh("22".repeat(20))),
                            SaplingOut(39_000, p2pkh("11".repeat(20))),
                        ),
                )
            val hashes = signer().getPreSignedImageHash(psbt, "", FROM_AMOUNT)
            assertEquals(1, hashes.size)
            assertEquals(64, hashes[0].length)
        } catch (e: Throwable) {
            skipIfJniUnavailable(e)
        }
    }

    @Test
    fun `buildSigningInputData - frozen plan reproduces inputs, outputs, lockTime, branchId (ZEC)`() {
        try {
            // Building the Sapling Bitcoin.SigningInput needs the WalletCore JNI; parsing the proto
            // back is pure and the expectations come from the PSBT, so this pins the reconstruction
            // (the bytes WalletCore compiles into the broadcast tx) without a precomputed golden.
            val depositHash = "22".repeat(20)
            val changeHash = "11".repeat(20)
            val psbt =
                encodeSaplingPsbt(
                    inputs =
                        listOf(
                            SaplingIn(
                                prevTxIdDisplay = TXID_ONE,
                                vout = 2,
                                sequence = 0xFFFFFFFFL,
                                amount = 100_000,
                                prevScriptHex = p2pkh(changeHash),
                            )
                        ),
                    outputs =
                        listOf(
                            SaplingOut(60_000, p2pkh(depositHash)),
                            SaplingOut(39_000, p2pkh(changeHash)),
                        ),
                    lockTime = 880_000,
                )

            val input =
                Bitcoin.SigningInput.parseFrom(
                    signer().buildSigningInputData(psbt, "", FROM_AMOUNT)
                )

            assertEquals(880_000, input.lockTime)

            assertEquals(SigningError.OK, input.plan.error)
            assertEquals(100_000L, input.plan.availableAmount)
            assertEquals(60_000L, input.plan.amount)
            assertEquals(39_000L, input.plan.change)
            assertEquals(1_000L, input.plan.fee)
            // ZIP-243 branch id must be on the plan or WalletCore derives the wrong digest.
            // Hardcoded on purpose (not the production constant): a golden value that fails loudly
            // if the branch id is ever changed without a deliberate update here per network
            // upgrade.
            assertEquals("30f33754", Numeric.toHexStringNoPrefix(input.plan.branchId.toByteArray()))

            assertEquals(1, input.utxoCount)
            val utxo = input.getUtxo(0)
            assertArrayEquals(
                Numeric.hexStringToByteArray(TXID_ONE).reversedArray(),
                utxo.outPoint.hash.toByteArray(),
            )
            assertEquals(2, utxo.outPoint.index)
            assertEquals(100_000L, utxo.amount)
            assertEquals(p2pkh(changeHash), Numeric.toHexStringNoPrefix(utxo.script.toByteArray()))

            // Derived from the PSBT output hash160s as transparent `t1…` addresses.
            assertTrue(input.toAddress.startsWith("t1"), "deposit ${input.toAddress}")
            assertTrue(input.changeAddress.startsWith("t1"), "change ${input.changeAddress}")
        } catch (e: Throwable) {
            skipIfJniUnavailable(e)
        }
    }

    private data class SaplingIn(
        val prevTxIdDisplay: String,
        val vout: Long,
        val sequence: Long,
        val amount: Long,
        val prevScriptHex: String,
        val unsignedScriptSigHex: String? = null,
    )

    private data class SaplingOut(val amount: Long, val scriptHex: String)

    private fun saplingIn(
        amount: Long,
        prevScriptHex: String = p2pkh("11".repeat(20)),
        unsignedScriptSigHex: String? = null,
    ) =
        SaplingIn(
            prevTxIdDisplay = TXID_ONE,
            vout = 0,
            sequence = 0xFFFFFFFFL,
            amount = amount,
            prevScriptHex = prevScriptHex,
            unsignedScriptSigHex = unsignedScriptSigHex,
        )

    private fun p2pkh(hash20Hex: String): String = "76a914$hash20Hex" + "88ac"

    /**
     * Minimal BIP-174 PSBT encoder whose unsigned-tx body is **Sapling-v4**: version +
     * versionGroupId
     * + transparent inputs/outputs + lockTime + expiryHeight + valueBalance + three shielded varint
     *   counts. Per-input maps carry WITNESS_UTXO (key 0x01), matching the captured ZEC fixture.
     */
    private fun encodeSaplingPsbt(
        inputs: List<SaplingIn>,
        outputs: List<SaplingOut>,
        versionGroupId: Long = SAPLING_VERSION_GROUP_ID,
        lockTime: Long = 0,
        expiryHeight: Long = 0,
        valueBalance: Long = 0,
        nShieldedSpend: Long = 0,
        nShieldedOutput: Long = 0,
        nJoinSplit: Long = 0,
        unsignedTxTrailerHex: String? = null,
    ): ByteArray {
        val unsigned = ByteArrayOutputStream()
        unsigned.write(le32(SAPLING_TX_VERSION)) // 0x80000004
        unsigned.write(le32(versionGroupId))
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
        unsigned.write(le32(expiryHeight))
        unsigned.write(le64(valueBalance))
        unsigned.write(varInt(nShieldedSpend))
        unsigned.write(varInt(nShieldedOutput))
        unsigned.write(varInt(nJoinSplit))
        unsignedTxTrailerHex?.let { unsigned.write(Numeric.hexStringToByteArray(it)) }

        val psbt = ByteArrayOutputStream()
        psbt.write(MAGIC)
        val unsignedBytes = unsigned.toByteArray()
        psbt.write(byteArrayOf(0x01, 0x00)) // global key 0x00
        psbt.write(varInt(unsignedBytes.size.toLong()))
        psbt.write(unsignedBytes)
        psbt.write(0x00) // global map terminator

        inputs.forEach { input ->
            val witnessUtxo = ByteArrayOutputStream()
            witnessUtxo.write(le64(input.amount))
            val script = Numeric.hexStringToByteArray(input.prevScriptHex)
            witnessUtxo.write(varInt(script.size.toLong()))
            witnessUtxo.write(script)
            val wu = witnessUtxo.toByteArray()
            psbt.write(byteArrayOf(0x01, 0x01)) // WITNESS_UTXO key 0x01
            psbt.write(varInt(wu.size.toLong()))
            psbt.write(wu)
            psbt.write(0x00) // input map terminator
        }
        outputs.forEach { psbt.write(0x00) } // empty per-output maps
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
        // Generous quoted swap amount — above every fixture's fee so the ceiling never trips except
        // in the dedicated rejection test (which passes its own small value).
        private val FROM_AMOUNT = BigInteger.valueOf(1_000_000)
        private const val TXID_ONE =
            "0000000000000000000000000000000000000000000000000000000000000001"
        private const val SAPLING_TX_VERSION = 0x80000004L
        private const val SAPLING_VERSION_GROUP_ID = 0x892F2085L
        private const val NU5_VERSION_GROUP_ID = 0x26A7270AL
    }
}
