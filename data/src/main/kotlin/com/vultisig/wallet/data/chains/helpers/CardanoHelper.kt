package com.vultisig.wallet.data.chains.helpers

import com.google.protobuf.ByteString
import com.vultisig.wallet.data.common.Utils
import com.vultisig.wallet.data.crypto.CardanoUtils
import com.vultisig.wallet.data.crypto.checkError
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.SignedTransactionResult
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.tss.getSignature
import com.vultisig.wallet.data.utils.Numeric
import com.vultisig.wallet.data.utils.Numeric.hexStringToByteArray
import java.io.ByteArrayOutputStream
import timber.log.Timber
import wallet.core.java.AnySigner
import wallet.core.jni.CoinType
import wallet.core.jni.DataVector
import wallet.core.jni.PublicKey
import wallet.core.jni.PublicKeyType
import wallet.core.jni.TransactionCompiler
import wallet.core.jni.proto.Cardano
import wallet.core.jni.proto.Cardano.TransactionPlan
import wallet.core.jni.proto.Common.SigningError

@OptIn(ExperimentalStdlibApi::class)
object CardanoHelper {

    private const val CIP20_MSG_CHUNK_BYTES: Int = 64
    private const val AUX_DATA_HASH_BODY_KEY: Byte = 0x07
    private const val AUX_DATA_HASH_SIZE: Int = 32

    /**
     * Assembles the base [Cardano.SigningInput.Builder] from [keysignPayload] without a forced fee.
     */
    private fun buildSigningInputBuilder(
        keysignPayload: KeysignPayload
    ): Cardano.SigningInput.Builder {
        require(keysignPayload.coin.chain == Chain.Cardano) { "Coin is not ada" }

        val (_, sendMaxAmount, ttl) =
            keysignPayload.blockChainSpecific as? BlockChainSpecific.Cardano
                ?: error("fail to get Cardano chain specific parameters")

        var input =
            Cardano.SigningInput.newBuilder()
                .setTransferMessage(
                    Cardano.Transfer.newBuilder()
                        .setAmount(keysignPayload.toAmount.toLong())
                        .setToAddress(keysignPayload.toAddress)
                        .setUseMaxAmount(sendMaxAmount)
                        .setChangeAddress(keysignPayload.coin.address)
                )
                .setTtl(ttl.toLong())

        // Add UTXOs to the input
        for (inputUtxo in keysignPayload.utxos) {
            val utxo =
                Cardano.TxInput.newBuilder()
                    .setOutPoint(
                        Cardano.OutPoint.newBuilder()
                            .setTxHash(ByteString.copyFrom(hexStringToByteArray(inputUtxo.hash)))
                            .setOutputIndex(inputUtxo.index.toLong())
                            .build()
                    )
                    .setAmount(inputUtxo.amount.toLong())
                    .setAddress(keysignPayload.coin.address)
                    .build()
            input.addUtxos(utxo)
        }

        return input
    }

    /**
     * Returns serialized [Cardano.SigningInput] bytes with the fee obtained from
     * [getCardanoTransactionPlan], plus a per-byte allowance for CIP-20 auxiliary data when
     * [KeysignPayload.memo] is set.
     */
    fun getPreSignedInputData(keysignPayload: KeysignPayload): ByteArray {
        val plan = getCardanoTransactionPlan(keysignPayload)
        val cardanoSpecific = keysignPayload.blockChainSpecific as BlockChainSpecific.Cardano
        val finalFee = plan.fee + auxDataExtraFee(keysignPayload.memo, cardanoSpecific.byteFee)
        return buildSigningInputBuilder(keysignPayload)
            .setTransferMessage(
                Cardano.Transfer.newBuilder()
                    .setAmount(keysignPayload.toAmount.toLong())
                    .setToAddress(keysignPayload.toAddress)
                    .setUseMaxAmount(cardanoSpecific.sendMaxAmount)
                    .setChangeAddress(keysignPayload.coin.address)
                    .setForceFee(finalFee)
            )
            .build()
            .toByteArray()
    }

    /**
     * Returns the Blake2b-256 pre-image hash for the given [keysignPayload], used in TSS signing.
     * When the payload carries a memo, the body is rewritten to commit to the CIP-20 auxiliary data
     * hash before hashing.
     */
    fun getPreSignedImageHash(keysignPayload: KeysignPayload): List<String> {
        val inputData = getPreSignedInputData(keysignPayload)
        val hashes = TransactionCompiler.preImageHashes(CoinType.CARDANO, inputData)
        val preSigningOutput =
            wallet.core.jni.proto.TransactionCompiler.PreSigningOutput.parseFrom(hashes)
        if (preSigningOutput.errorMessage.isNotEmpty()) {
            val errorMessage = preSigningOutput.errorMessage
            Timber.e("$errorMessage")
            error(errorMessage)
        }
        val memo = keysignPayload.memo?.takeIf { it.isNotEmpty() }
        val dataHash =
            if (memo == null) {
                preSigningOutput.dataHash.toByteArray()
            } else {
                val auxData = encodeCip20AuxData(memo)
                val body =
                    appendAuxDataHashToBody(
                        preSigningOutput.data.toByteArray(),
                        Utils.blake2bHash(auxData),
                    )
                Utils.blake2bHash(body)
            }
        return listOf(Numeric.toHexStringNoPrefix(dataHash))
    }

    /** Compiles and returns the signed Cardano transaction from TSS [signatures]. */
    fun getSignedTransaction(
        vaultHexPublicKey: String,
        vaultHexChainCode: String,
        keysignPayload: KeysignPayload,
        signatures: Map<String, tss.KeysignResponse>,
    ): SignedTransactionResult {
        val memo = keysignPayload.memo?.takeIf { it.isNotEmpty() }
        return if (memo == null) {
            compileSignedTransactionWithoutMemo(
                vaultHexPublicKey,
                vaultHexChainCode,
                keysignPayload,
                signatures,
            )
        } else {
            compileSignedTransactionWithMemo(vaultHexPublicKey, keysignPayload, memo, signatures)
        }
    }

    private fun compileSignedTransactionWithoutMemo(
        vaultHexPublicKey: String,
        vaultHexChainCode: String,
        keysignPayload: KeysignPayload,
        signatures: Map<String, tss.KeysignResponse>,
    ): SignedTransactionResult {
        val extendedKeyData =
            CardanoUtils.createExtendedKey(
                spendingKeyHex = vaultHexPublicKey,
                chainCodeHex = vaultHexChainCode,
            )
        val spendingKeyData = vaultHexPublicKey.hexToByteArray()
        val verificationKey = PublicKey(spendingKeyData, PublicKeyType.ED25519)
        val inputData = getPreSignedInputData(keysignPayload)
        val hashes = TransactionCompiler.preImageHashes(CoinType.CARDANO, inputData)
        val preSigningOutput =
            wallet.core.jni.proto.TransactionCompiler.PreSigningOutput.parseFrom(hashes)
                .checkError()
        val allSignatures = DataVector()
        val publicKeys = DataVector()

        val key = Numeric.toHexStringNoPrefix(preSigningOutput.dataHash.toByteArray())

        val signature = signatures[key]?.getSignature() ?: error("Signature not found")

        if (!verificationKey.verify(signature, preSigningOutput.dataHash.toByteArray())) {
            error("Cardano signature verification failed")
        }

        allSignatures.add(signature)
        publicKeys.add(extendedKeyData)

        val compileWithSignature =
            TransactionCompiler.compileWithSignatures(
                CoinType.CARDANO,
                inputData,
                allSignatures,
                publicKeys,
            )

        val output = Cardano.SigningOutput.parseFrom(compileWithSignature).checkError()
        val transactionHash =
            CardanoUtils.calculateCardanoTransactionHash(output.encoded.toByteArray())
        return SignedTransactionResult(
            rawTransaction = output.encoded.toByteArray().toHexString(),
            transactionHash = transactionHash,
        )
    }

    /**
     * Builds a signed Cardano transaction that commits to CIP-20 auxiliary metadata. Because
     * WalletCore's Cardano `SigningInput` does not yet expose metadata fields, the tx body is
     * extended with `auxiliary_data_hash` (key 7) before hashing, the MPC signature is wrapped in a
     * hand-encoded CBOR witness set, and the auxiliary data is appended as the 4th element of the
     * signed tx envelope.
     */
    private fun compileSignedTransactionWithMemo(
        vaultHexPublicKey: String,
        keysignPayload: KeysignPayload,
        memo: String,
        signatures: Map<String, tss.KeysignResponse>,
    ): SignedTransactionResult {
        val spendingKeyData = vaultHexPublicKey.hexToByteArray()
        val verificationKey = PublicKey(spendingKeyData, PublicKeyType.ED25519)
        val inputData = getPreSignedInputData(keysignPayload)
        val hashes = TransactionCompiler.preImageHashes(CoinType.CARDANO, inputData)
        val preSigningOutput =
            wallet.core.jni.proto.TransactionCompiler.PreSigningOutput.parseFrom(hashes)
                .checkError()

        val auxData = encodeCip20AuxData(memo)
        val auxDataHash = Utils.blake2bHash(auxData)
        val body = appendAuxDataHashToBody(preSigningOutput.data.toByteArray(), auxDataHash)
        val bodyHash = Utils.blake2bHash(body)
        val key = Numeric.toHexStringNoPrefix(bodyHash)
        val signature = signatures[key]?.getSignature() ?: error("Signature not found")
        if (!verificationKey.verify(signature, bodyHash)) {
            error("Cardano signature verification failed")
        }

        val witnessSet = encodeVKeyWitnessSet(spendingKeyData, signature)
        val rawTx = encodeSignedTx(body, witnessSet, auxData)
        return SignedTransactionResult(
            rawTransaction = rawTx.toHexString(),
            transactionHash = bodyHash.toHexString(),
        )
    }

    /**
     * Computes and returns the [TransactionPlan] for the given [keysignPayload] using WalletCore.
     */
    fun getCardanoTransactionPlan(keysignPayload: KeysignPayload): TransactionPlan {
        val signingInput = buildSigningInputBuilder(keysignPayload).build()
        val plan = AnySigner.plan(signingInput, CoinType.CARDANO, TransactionPlan.parser())
        if (plan.error == SigningError.OK) {
            return plan
        }

        Timber.e("Cardano Plan Error: ${plan.error.name}")

        throw RuntimeException("Signing Error During Plan calculation")
    }

    /**
     * Estimates the lovelace fee bump required to cover the auxiliary data hash entry in the body
     * and the auxiliary data tail in the signed transaction.
     */
    internal fun auxDataExtraFee(memo: String?, byteFee: Long): Long {
        val nonEmpty = memo?.takeIf { it.isNotEmpty() } ?: return 0L
        val auxDataSize = encodeCip20AuxData(nonEmpty).size
        // Body grows by: 1 (key) + 2 (bytes(32) header) + 32 (hash) = 35 bytes.
        // Signed tx tail grows by auxDataSize (replaces the single-byte CBOR null).
        return byteFee * (35L + auxDataSize - 1L)
    }

    /**
     * Inserts an `auxiliary_data_hash` (CBOR map key 7, 32 bytes) entry at the end of the Cardano
     * transaction body map. Assumes the body is a CBOR map with all keys < 7, which is the case for
     * simple ADA transfers (keys 0,1,2,3).
     */
    internal fun appendAuxDataHashToBody(body: ByteArray, hash: ByteArray): ByteArray {
        require(hash.size == AUX_DATA_HASH_SIZE) { "aux_data_hash must be 32 bytes" }
        require(body.isNotEmpty()) { "tx body cannot be empty" }
        val firstByte = body[0].toInt() and 0xFF
        val majorType = (firstByte shr 5) and 0x07
        require(majorType == 5) { "Cardano tx body must be a CBOR map" }
        val mapSize = firstByte and 0x1F
        require(mapSize in 1..22) { "unexpected Cardano body map size: $mapSize" }

        val out = ByteArrayOutputStream(body.size + 35)
        out.write(0xA0 or (mapSize + 1))
        out.write(body, 1, body.size - 1)
        out.write(AUX_DATA_HASH_BODY_KEY.toInt())
        out.write(0x58) // bytes, 1-byte length follows
        out.write(AUX_DATA_HASH_SIZE)
        out.write(hash)
        return out.toByteArray()
    }

    /**
     * Encodes CIP-20 auxiliary data carrying [memo] as Alonzo+ tagged metadata `#6.259({ 0 => { 674
     * => { "msg" => [chunks] } } })`. Long memos are split into UTF-8 chunks of at most 64 bytes
     * per CIP-20.
     */
    internal fun encodeCip20AuxData(memo: String): ByteArray {
        val chunks = chunkUtf8Bytes(memo, CIP20_MSG_CHUNK_BYTES)
        val out = ByteArrayOutputStream()
        // tag(259)
        out.write(0xD9)
        out.write(0x01)
        out.write(0x03)
        // map(1) { 0 => transaction_metadata }
        out.write(0xA1)
        out.write(0x00)
        // transaction_metadata = map(1) { 674 => CIP-20 payload }
        out.write(0xA1)
        // uint 674 = 0x02A2 → encoded as `19 02 A2`
        out.write(0x19)
        out.write(0x02)
        out.write(0xA2)
        // CIP-20 payload = map(1) { "msg" => [chunks] }
        out.write(0xA1)
        out.write(0x63) // text(3)
        out.write('m'.code)
        out.write('s'.code)
        out.write('g'.code)
        writeArrayHeader(out, chunks.size)
        for (chunk in chunks) {
            writeTextString(out, chunk)
        }
        return out.toByteArray()
    }

    /**
     * Encodes a CBOR vkey witness set: `{ 0 => [ [vkey, signature] ] }`. Cardano requires
     * unsigned-integer keys, so this is hand-encoded rather than going through a generic CBOR
     * library that may tag the map.
     */
    internal fun encodeVKeyWitnessSet(publicKey: ByteArray, signature: ByteArray): ByteArray {
        require(publicKey.size == 32) { "Ed25519 public key must be 32 bytes" }
        require(signature.size == 64) { "Ed25519 signature must be 64 bytes" }
        val out = ByteArrayOutputStream(2 + 1 + 1 + 2 + 32 + 2 + 64)
        out.write(0xA1) // map(1)
        out.write(0x00) // key 0 (vkeywitnesses)
        out.write(0x81) // array(1)
        out.write(0x82) // array(2) [vkey, signature]
        out.write(0x58) // bytes, 1-byte length follows
        out.write(32)
        out.write(publicKey)
        out.write(0x58)
        out.write(64)
        out.write(signature)
        return out.toByteArray()
    }

    /** Wraps a Cardano signed transaction: `[body, witnesses, true, aux_data]`. */
    internal fun encodeSignedTx(
        body: ByteArray,
        witnessSet: ByteArray,
        auxData: ByteArray,
    ): ByteArray {
        val out = ByteArrayOutputStream(1 + body.size + witnessSet.size + 1 + auxData.size)
        out.write(0x84) // array(4)
        out.write(body)
        out.write(witnessSet)
        out.write(0xF5) // is_valid = true
        out.write(auxData)
        return out.toByteArray()
    }

    private fun writeArrayHeader(out: ByteArrayOutputStream, length: Int) {
        when {
            length < 24 -> out.write(0x80 or length)
            length < 256 -> {
                out.write(0x98)
                out.write(length)
            }
            else -> error("CIP-20 memo produces too many chunks: $length")
        }
    }

    private fun writeTextString(out: ByteArrayOutputStream, text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        when {
            bytes.size < 24 -> out.write(0x60 or bytes.size)
            bytes.size < 256 -> {
                out.write(0x78)
                out.write(bytes.size)
            }
            else -> error("CIP-20 chunk exceeds CBOR single-byte length")
        }
        out.write(bytes)
    }

    /**
     * Splits [text] into UTF-8 byte chunks of at most [maxBytes], without splitting code points.
     */
    private fun chunkUtf8Bytes(text: String, maxBytes: Int): List<String> {
        if (text.isEmpty()) return listOf("")
        val chunks = mutableListOf<String>()
        val buffer = StringBuilder()
        var bufferBytes = 0
        for (codePoint in text.codePoints()) {
            val cpBytes = String(Character.toChars(codePoint)).toByteArray(Charsets.UTF_8).size
            if (bufferBytes + cpBytes > maxBytes && buffer.isNotEmpty()) {
                chunks += buffer.toString()
                buffer.clear()
                bufferBytes = 0
            }
            buffer.appendCodePoint(codePoint)
            bufferBytes += cpBytes
        }
        if (buffer.isNotEmpty()) chunks += buffer.toString()
        return chunks
    }
}
