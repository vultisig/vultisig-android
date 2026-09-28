package com.vultisig.wallet.data.chains.helpers

import com.vultisig.wallet.data.WalletCoreNative
import com.vultisig.wallet.data.utils.Numeric
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.MessageDigest
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import org.junit.Before
import org.junit.Test
import wallet.core.jni.CoinType
import wallet.core.jni.DataVector
import wallet.core.jni.Hash
import wallet.core.jni.PrivateKey
import wallet.core.jni.TransactionCompiler
import wallet.core.jni.proto.Bitcoin

/**
 * Instrumented because the sighashes and the compiled transaction come from WalletCore's JNI. The
 * pure-JVM half (version rule, byte rewrite, live captures) lives in
 * `SwapKitLegacyP2PKHSignerTest`.
 *
 * Pins the cross-client property the version normalization rests on: a co-signer hashing the raw
 * version-2 PSBT and one hashing its version-1 rewrite sign the same digests, and the broadcast
 * transaction is exactly the version-1 PSBT with signatures filled in.
 */
class SwapKitLegacyP2PKHSignerAndroidTest {

    private lateinit var key: PrivateKey
    private lateinit var vaultPubKeyHex: String
    private lateinit var vaultKeyHash: ByteArray

    @Before
    fun setUp() {
        WalletCoreNative.ensureLoaded()
        key = PrivateKey(Numeric.hexStringToByteArray("11".repeat(32)))
        val pubKey = key.getPublicKeySecp256k1(true).data()
        vaultPubKeyHex = Numeric.toHexStringNoPrefix(pubKey)
        vaultKeyHash = Hash.sha256RIPEMD(pubKey)
    }

    @Test
    fun dogeVersion2AndItsVersion1RewriteSignIdentically() = assertSignsAsV1(CoinType.DOGECOIN)

    @Test
    fun bchVersion2AndItsVersion1RewriteSignIdentically() = assertSignsAsV1(CoinType.BITCOINCASH)

    @Test fun dashVersion2AndItsVersion1RewriteSignIdentically() = assertSignsAsV1(CoinType.DASH)

    private fun assertSignsAsV1(coin: CoinType) {
        // An empty chain code makes the signer use the key as-is.
        val signer = SwapKitLegacyP2PKHSigner(vaultPubKeyHex, "", coin)
        val v2 = legacyPsbt(version = 2)
        val v1 = SwapKitLegacyPsbtVersion.normalizeToV1(v2)

        assertEquals(
            signer.getPreSignedImageHash(v1, "", FROM_AMOUNT),
            signer.getPreSignedImageHash(v2, "", FROM_AMOUNT),
            "$coin sighashes",
        )

        val inputData = signer.buildSigningInputData(v1, "", FROM_AMOUNT)
        val preSigning =
            Bitcoin.PreSigningOutput.parseFrom(TransactionCompiler.preImageHashes(coin, inputData))
        val signatures = DataVector()
        val publicKeys = DataVector()
        preSigning.hashPublicKeysList.forEach {
            signatures.add(key.signAsDER(it.dataHash.toByteArray()))
            publicKeys.add(key.getPublicKeySecp256k1(true).data())
        }
        val signed =
            Bitcoin.SigningOutput.parseFrom(
                TransactionCompiler.compileWithSignatures(coin, inputData, signatures, publicKeys)
            )
        assertEquals("", signed.errorMessage, "$coin compile")

        val broadcast = signed.encoded.toByteArray()
        assertEquals("01000000", Numeric.toHexStringNoPrefix(broadcast.copyOfRange(0, 4)))
        assertContentEquals(unsignedTx(v1), stripScriptSigs(broadcast), "$coin broadcast body")
    }

    /** One vault input, a deposit and a vault change output; every sequence disables BIP68. */
    private fun legacyPsbt(version: Long): ByteArray {
        val vaultScript = p2pkh(vaultKeyHash)
        val prevTx = prevTx(amount = 100_000, script = vaultScript)
        val unsigned = ByteArrayOutputStream()
        unsigned.write(le32(version))
        unsigned.write(1)
        unsigned.write(sha256d(prevTx))
        unsigned.write(le32(0))
        unsigned.write(0) // empty scriptSig
        unsigned.write(le32(0xFFFFFFFFL))
        unsigned.write(2)
        writeOutput(unsigned, 60_000, p2pkh(ByteArray(20) { 0x22 }))
        writeOutput(unsigned, 39_000, vaultScript)
        unsigned.write(le32(0))
        val unsignedBytes = unsigned.toByteArray()

        val psbt = ByteArrayOutputStream()
        psbt.write(byteArrayOf(0x70, 0x73, 0x62, 0x74, 0xff.toByte()))
        psbt.write(byteArrayOf(0x01, 0x00))
        psbt.write(unsignedBytes.size)
        psbt.write(unsignedBytes)
        psbt.write(0) // globals terminator
        psbt.write(byteArrayOf(0x01, 0x00)) // NON_WITNESS_UTXO
        psbt.write(prevTx.size)
        psbt.write(prevTx)
        psbt.write(0) // input map terminator
        psbt.write(0) // output maps
        psbt.write(0)
        return psbt.toByteArray()
    }

    private fun prevTx(amount: Long, script: ByteArray): ByteArray {
        val tx = ByteArrayOutputStream()
        tx.write(le32(1))
        tx.write(1)
        tx.write(ByteArray(32))
        tx.write(le32(0))
        tx.write(0)
        tx.write(le32(0xFFFFFFFFL))
        tx.write(1)
        writeOutput(tx, amount, script)
        tx.write(le32(0))
        return tx.toByteArray()
    }

    private fun unsignedTx(psbt: ByteArray): ByteArray {
        val offset = SwapKitPsbtParser.unsignedTxOffset(psbt)
        val length = psbt[offset - 1].toInt() and 0xFF
        return psbt.copyOfRange(offset, offset + length)
    }

    /** The broadcast tx with every scriptSig emptied, i.e. the body the PSBT describes. */
    private fun stripScriptSigs(tx: ByteArray): ByteArray {
        val cursor = PsbtCursor(tx)
        val out = ByteArrayOutputStream()
        out.write(cursor.readBytes(4))
        val inputs = cursor.readCompactSize().toInt()
        out.write(inputs)
        repeat(inputs) {
            out.write(cursor.readBytes(36))
            cursor.readBytes(cursor.readCompactSize().toInt())
            out.write(0)
            out.write(cursor.readBytes(4))
        }
        val rest = tx.copyOfRange(cursor.offset, tx.size)
        out.write(rest)
        return out.toByteArray()
    }

    private fun writeOutput(out: ByteArrayOutputStream, amount: Long, script: ByteArray) {
        out.write(le64(amount))
        out.write(script.size)
        out.write(script)
    }

    private fun p2pkh(hash: ByteArray): ByteArray =
        byteArrayOf(0x76, 0xa9.toByte(), 0x14) + hash + byteArrayOf(0x88.toByte(), 0xac.toByte())

    private fun le32(v: Long) = ByteArray(4) { i -> (v ushr (i * 8)).toByte() }

    private fun le64(v: Long) = ByteArray(8) { i -> (v ushr (i * 8)).toByte() }

    private fun sha256d(data: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(digest.digest(data))
    }

    private companion object {
        val FROM_AMOUNT: BigInteger = BigInteger.valueOf(1_000_000)
    }
}
