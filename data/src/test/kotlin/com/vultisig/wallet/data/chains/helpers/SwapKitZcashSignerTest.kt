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
import wallet.core.jni.Hash
import wallet.core.jni.proto.Bitcoin
import wallet.core.jni.proto.BitcoinV2

/**
 * Tests for [SwapKitZcashSigner] (transparent ZEC NU5 v5 PSBT signing).
 *
 * The v5 body parsing + structural validation (version/group gate, shielded-field
 * rejection, P2PKH shape, output count, fee sign) is pure JVM and runs headlessly — those
 * rejections fire before any WalletCore call. The presigning happy path needs the WalletCore JNI
 * (V2 builder → `TransactionCompiler`) and skips gracefully
 * when it is unavailable.
 */
class SwapKitZcashSignerTest {

    // An empty chain code makes the signer use the key as-is, so its vault script is the P2PKH of
    // VAULT_PUB_KEY's hash160.
    private fun signer() = SwapKitZcashSigner(VAULT_PUB_KEY, "")

    /** hash160 of the vault key; needs the WalletCore JNI, so only call it inside JNI tests. */
    private fun vaultKeyHash(): String =
        Numeric.toHexStringNoPrefix(Hash.sha256RIPEMD(Numeric.hexStringToByteArray(VAULT_PUB_KEY)))

    @Test
    fun `rejects empty payload`() {
        val e =
            assertThrows(SwapKitZcashSignerException::class.java) {
                signer().buildSigningInputData(ByteArray(0), "", FROM_AMOUNT)
            }
        assertTrue(e.message!!.contains("empty"))
    }

    @Test
    fun `rejects a Sapling v4 PSBT`() {
        val psbt =
            encodeSaplingPsbt(
                version = SAPLING_TX_VERSION,
                versionGroupId = SAPLING_VERSION_GROUP_ID,
                inputs = listOf(saplingIn(amount = 100_000)),
                outputs = listOf(SaplingOut(99_000, p2pkh("22".repeat(20)))),
            )
        val e =
            assertThrows(SwapKitZcashSignerException::class.java) {
                signer().buildSigningInputData(psbt, "", FROM_AMOUNT, TEST_BRANCH_ID)
            }
        assertTrue(e.message!!.contains("unsupported Zcash version"))
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
                signer().buildSigningInputData(psbt, "", FROM_AMOUNT, TEST_BRANCH_ID)
            }
        assertTrue(e.message!!.contains("shielded"))
    }

    @Test
    fun `depositAddress refuses a non-P2PKH deposit output`() {
        val psbt =
            encodeSaplingPsbt(
                inputs = listOf(saplingIn(amount = 100_000)),
                outputs = listOf(SaplingOut(99_000, "0014" + "22".repeat(20))),
            )
        val e =
            assertThrows(SwapKitZcashSignerException::class.java) {
                SwapKitZcashSigner.depositAddress(psbt, "")
            }
        assertTrue(e.message!!.contains("output #0"))
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
                signer().buildSigningInputData(psbt, "", FROM_AMOUNT, TEST_BRANCH_ID)
            }
        assertTrue(e.message!!.contains("input #0"))
        assertTrue(e.message!!.contains("not P2PKH"))
    }

    @Test
    fun `rejects a PSBT branch id that differs from the live one`() {
        val psbt =
            encodeSaplingPsbt(
                inputs = listOf(saplingIn(amount = 100_000)),
                outputs = listOf(SaplingOut(99_000, p2pkh("22".repeat(20)))),
                branchIdHex = "01020304",
            )
        val e =
            assertThrows(SwapKitZcashSignerException::class.java) {
                signer().buildSigningInputData(psbt, "", FROM_AMOUNT, TEST_BRANCH_ID)
            }
        assertTrue(e.message!!.contains("differs from the live branch id"))
    }

    @Test
    fun `rejects more than one OP_RETURN output`() {
        val psbt =
            encodeSaplingPsbt(
                inputs = listOf(saplingIn(amount = 100_000, prevScriptHex = p2pkh(VAULT_HASH_HEX))),
                outputs =
                    listOf(
                        SaplingOut(0, "6a0161"),
                        SaplingOut(0, "6a0162"),
                        SaplingOut(99_000, p2pkh("22".repeat(20))),
                    ),
            )
        val e =
            assertThrows(SwapKitZcashSignerException::class.java) {
                signer().buildSigningInputData(psbt, "", FROM_AMOUNT, TEST_BRANCH_ID)
            }
        assertTrue(e.message!!.contains("more than one OP_RETURN"))
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
                signer().buildSigningInputData(psbt, "", FROM_AMOUNT, TEST_BRANCH_ID)
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
                signer().buildSigningInputData(psbt, "", BigInteger.valueOf(1_000), TEST_BRANCH_ID)
            }
        assertTrue(e.message!!.contains("exceeds the quoted swap amount"))
    }

    @Test
    fun `rejects a missing branch id rather than falling back to a constant`() {
        val psbt =
            encodeSaplingPsbt(
                inputs = listOf(saplingIn(amount = 100_000)),
                outputs = listOf(SaplingOut(99_000, p2pkh("22".repeat(20)))),
            )
        val e =
            assertThrows(SwapKitZcashSignerException::class.java) {
                // No branch id supplied (RPC unavailable) — must refuse rather than sign with a
                // stale value.
                signer().buildSigningInputData(psbt, "", FROM_AMOUNT, zcashBranchId = null)
            }
        assertTrue(e.message!!.contains("branch id is unavailable"))
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
                signer().buildSigningInputData(psbt, "", FROM_AMOUNT, TEST_BRANCH_ID)
            }
        assertTrue(e.message!!.contains("trailing bytes"))
    }

    @Test
    fun `getPreSignedImageHash - transparent happy path returns one sighash per input`() {
        try {
            val psbt =
                encodeSaplingPsbt(
                    inputs =
                        listOf(saplingIn(amount = 100_000, prevScriptHex = p2pkh(vaultKeyHash()))),
                    outputs =
                        listOf(
                            SaplingOut(60_000, p2pkh("22".repeat(20))),
                            SaplingOut(39_000, p2pkh(vaultKeyHash())),
                        ),
                )
            val hashes = signer().getPreSignedImageHash(psbt, "", FROM_AMOUNT, TEST_BRANCH_ID)
            assertEquals(1, hashes.size)
            assertEquals(64, hashes[0].length)
        } catch (e: Throwable) {
            skipIfJniUnavailable(e)
        }
    }

    @Test
    fun `buildSigningInputData - V2 input replays inputs, outputs, lockTime, expiry, branchId`() {
        try {
            val changeHash = VAULT_HASH_HEX
            val psbt =
                encodeSaplingPsbt(
                    inputs =
                        listOf(
                            SaplingIn(
                                prevTxIdDisplay = TXID_ONE,
                                vout = 2,
                                sequence = 0xFFFFFFFEL,
                                amount = 100_000,
                                prevScriptHex = p2pkh(changeHash),
                            )
                        ),
                    outputs =
                        listOf(
                            SaplingOut(60_000, p2pkh("22".repeat(20))),
                            SaplingOut(0, "6a0461626364"),
                            SaplingOut(39_000, p2pkh(changeHash)),
                        ),
                    lockTime = 880_000,
                    expiryHeight = 3_600_000,
                )

            val builder =
                Bitcoin.SigningInput.parseFrom(
                        signer().buildSigningInputData(psbt, "", FROM_AMOUNT, TEST_BRANCH_ID)
                    )
                    .signingV2
                    .builder

            assertEquals(880_000, builder.lockTime)
            assertEquals(3_600_000, builder.zcashExtraData.expiryHeight)
            assertEquals(
                TEST_BRANCH_ID,
                Numeric.toHexStringNoPrefix(builder.zcashExtraData.branchId.toByteArray()),
            )
            assertEquals(1, builder.inputsCount)
            val input = builder.getInputs(0)
            assertArrayEquals(
                Numeric.hexStringToByteArray(TXID_ONE).reversedArray(),
                input.outPoint.hash.toByteArray(),
            )
            assertEquals(2, input.outPoint.vout)
            assertEquals(100_000L, input.value)
            assertEquals(0xFFFFFFFE.toInt(), input.sequence.sequence)
            // Output scripts verbatim and in the PSBT's order (OP_RETURN stays in the middle).
            assertEquals(
                listOf(p2pkh("22".repeat(20)), "6a0461626364", p2pkh(changeHash)),
                builder.outputsList.map {
                    Numeric.toHexStringNoPrefix(it.customScriptPubkey.toByteArray())
                },
            )
            assertEquals(listOf(60_000L, 0L, 39_000L), builder.outputsList.map { it.value })
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
     * Minimal BIP-174 PSBT encoder whose unsigned-tx body is **NU5 v5**: version, versionGroupId,
     * branch id, lockTime, expiryHeight, transparent inputs/outputs and three shielded varint
     * counts. Per-input maps carry WITNESS_UTXO (key 0x01), matching the captured ZEC fixture.
     */
    private fun encodeSaplingPsbt(
        inputs: List<SaplingIn>,
        outputs: List<SaplingOut>,
        version: Long = ZCASH_TX_VERSION,
        versionGroupId: Long = ZCASH_VERSION_GROUP_ID,
        branchIdHex: String = TEST_BRANCH_ID,
        lockTime: Long = 0,
        expiryHeight: Long = 0,
        nShieldedSpend: Long = 0,
        nShieldedOutput: Long = 0,
        nOrchardAction: Long = 0,
        unsignedTxTrailerHex: String? = null,
    ): ByteArray {
        val unsigned = ByteArrayOutputStream()
        unsigned.write(le32(version))
        unsigned.write(le32(versionGroupId))
        unsigned.write(Numeric.hexStringToByteArray(branchIdHex))
        unsigned.write(le32(lockTime))
        unsigned.write(le32(expiryHeight))
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
        unsigned.write(varInt(nShieldedSpend))
        unsigned.write(varInt(nShieldedOutput))
        unsigned.write(varInt(nOrchardAction))
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
        private const val VAULT_PUB_KEY =
            "025476c2e83188368da1ff3e292e7acafcdb3566bb0ad253f62fc70f07aeee6357"
        // Sample little-endian ZIP-243 branch id passed into the signer in place of the (now
        // removed) compiled-in constant; the signer must land it verbatim on the plan.
        private const val TEST_BRANCH_ID = "30f33754"
        // hash160 of VAULT_PUB_KEY, so tests need no JNI to build vault-owned scripts.
        private const val VAULT_HASH_HEX = "1d0f172a0ecb48aee1be1f2687d2963ae33f71a1"
        private const val TXID_ONE =
            "0000000000000000000000000000000000000000000000000000000000000001"
        private const val SAPLING_TX_VERSION = 0x80000004L
        private const val SAPLING_VERSION_GROUP_ID = 0x892F2085L
        private const val ZCASH_TX_VERSION = 0x80000005L
        private const val ZCASH_VERSION_GROUP_ID = 0x26A7270AL
    }
}
