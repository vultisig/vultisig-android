package com.vultisig.wallet.data.chains.helpers

import com.vultisig.wallet.data.common.toHexByteArray
import com.vultisig.wallet.data.crypto.checkError
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.SignedTransactionResult
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.tss.getSignature
import com.vultisig.wallet.data.utils.Numeric
import java.math.BigInteger
import wallet.core.jni.AnyAddress
import wallet.core.jni.Base58
import wallet.core.jni.Base64
import wallet.core.jni.CoinType
import wallet.core.jni.DataVector
import wallet.core.jni.PublicKey
import wallet.core.jni.PublicKeyType
import wallet.core.jni.SolanaAddress
import wallet.core.jni.TransactionCompiler
import wallet.core.jni.proto.Solana

internal const val SOLANA_PRIORITY_FEE_PRICE = 1000000L
internal const val SOLANA_PRIORITY_FEE_LIMIT = 100000

/** Length in bytes of an ed25519 signature slot inside a serialized Solana transaction. */
private const val SOLANA_SIGNATURE_LENGTH = 64

const val SOLANA_DEFAULT_CONTRACT_ADDRESS = "So11111111111111111111111111111111111111112"

/**
 * Prefix of the error thrown when the sender has no associated token account for an SPL transfer.
 * The coin ticker follows the prefix so the keysign error screen can name the token in a localized
 * message; keep it in sync with the `KeysignErrorScreen` branch that matches on it.
 */
const val SOLANA_MISSING_TOKEN_ACCOUNT_PREFIX =
    "SPL token transfer failed: missing associated token account for "

class SolanaHelper(private val vaultHexPublicKey: String) {

    private val coinType = CoinType.SOLANA

    companion object {
        val DefaultFeeInLamports: BigInteger = 1000000.toBigInteger()

        /**
         * The Solana transaction id is the first signature in the signed transaction. WalletCore
         * populates [Solana.SigningOutput.getSignaturesList] (base58) on the
         * `compileWithSignatures` path, so read it directly instead of re-deriving it from the
         * encoded transaction.
         */
        internal fun Solana.SigningOutput.transactionHash(): String =
            signaturesList.firstOrNull()?.signature
                ?: error("Signed Solana transaction has no signature")
    }

    private fun getPreSignedInputData(keysignPayload: KeysignPayload): ByteArray {
        val solanaSpecific =
            keysignPayload.blockChainSpecific as? BlockChainSpecific.Solana
                ?: error("Invalid blockChainSpecific")
        if (keysignPayload.coin.chain != Chain.Solana) {
            error("Chain is not Solana")
        }
        if (
            !keysignPayload.coin.isNativeToken && solanaSpecific.fromAddressPubKey.isNullOrEmpty()
        ) {
            error("$SOLANA_MISSING_TOKEN_ACCOUNT_PREFIX${keysignPayload.coin.ticker}")
        }
        val toAddress = AnyAddress(keysignPayload.toAddress, coinType)

        val input =
            Solana.SigningInput.newBuilder()
                .setV0Msg(true)
                .setRecentBlockhash(solanaSpecific.recentBlockHash)
                .setSender(keysignPayload.coin.address)
                .setPriorityFeePrice(
                    Solana.PriorityFeePrice.newBuilder()
                        .setPrice(
                            maxOf(
                                solanaSpecific.priorityFee
                                    .min(BigInteger.valueOf(Long.MAX_VALUE))
                                    .toLong(),
                                SOLANA_PRIORITY_FEE_PRICE,
                            )
                        )
                        .build()
                )
                .setPriorityFeeLimit(
                    Solana.PriorityFeeLimit.newBuilder()
                        .setLimit(
                            solanaSpecific.priorityLimit
                                .min(BigInteger.valueOf(Int.MAX_VALUE.toLong()))
                                .toInt()
                        )
                        .build()
                )

        if (keysignPayload.coin.isNativeToken) {
            val transfer =
                Solana.Transfer.newBuilder()
                    .setRecipient(toAddress.description())
                    .setValue(keysignPayload.toAmount.toLong())
            keysignPayload.memo?.let { transfer.setMemo(it) }

            return input.setTransferTransaction(transfer.build()).build().toByteArray()
        } else {
            if (!solanaSpecific.toAddressPubKey.isNullOrEmpty()) {
                val transfer =
                    Solana.TokenTransfer.newBuilder()
                        .setTokenMintAddress(keysignPayload.coin.contractAddress)
                        .setSenderTokenAddress(solanaSpecific.fromAddressPubKey)
                        .setRecipientTokenAddress(solanaSpecific.toAddressPubKey)
                        .setAmount(keysignPayload.toAmount.toLong())
                        .setDecimals(keysignPayload.coin.decimal)
                        .setTokenProgramIdValue(if (solanaSpecific.programId == true) 1 else 0)
                keysignPayload.memo?.let { transfer.setMemo(it) }

                return input.setTokenTransferTransaction(transfer.build()).build().toByteArray()
            } else {
                val receiverAddress = SolanaAddress(toAddress.description())
                val generatedRecipientAssociatedAddress =
                    if (solanaSpecific.programId == true) {
                        receiverAddress.token2022Address(keysignPayload.coin.contractAddress)
                    } else {
                        receiverAddress.defaultTokenAddress(keysignPayload.coin.contractAddress)
                    }
                val transferTokenMessage =
                    Solana.CreateAndTransferToken.newBuilder()
                        .setRecipientMainAddress(toAddress.description())
                        .setTokenMintAddress(keysignPayload.coin.contractAddress)
                        .setRecipientTokenAddress(generatedRecipientAssociatedAddress)
                        .setSenderTokenAddress(solanaSpecific.fromAddressPubKey)
                        .setAmount(keysignPayload.toAmount.toLong())
                        .setDecimals(keysignPayload.coin.decimal)
                        .setTokenProgramIdValue(if (solanaSpecific.programId == true) 1 else 0)
                keysignPayload.memo?.let { transferTokenMessage.setMemo(it) }

                return input
                    .setCreateAndTransferTokenTransaction(transferTokenMessage.build())
                    .build()
                    .toByteArray()
            }
        }
    }

    fun getPreSignedImageHash(keysignPayload: KeysignPayload): List<String> {
        keysignPayload.signSolana?.let { signSolana ->
            return signSolana.rawTransactions.flatMap { base64Tx ->
                val txData =
                    android.util.Base64.decode(base64Tx, android.util.Base64.DEFAULT)
                        ?: error("Invalid base64 transaction")
                getPreSignedImageHashForRaw(txData)
            }
        }

        val result = getPreSignedInputData(keysignPayload)
        val hashes = TransactionCompiler.preImageHashes(coinType, result)
        val preSigningOutput =
            wallet.core.jni.proto.TransactionCompiler.PreSigningOutput.parseFrom(hashes)
        if (!preSigningOutput.errorMessage.isNullOrEmpty()) {
            error(preSigningOutput.errorMessage)
        }
        return listOf(Numeric.toHexStringNoPrefix(preSigningOutput.data.toByteArray()))
    }

    fun getSignedTransaction(
        keysignPayload: KeysignPayload,
        signatures: Map<String, tss.KeysignResponse>,
    ): SignedTransactionResult {
        keysignPayload.signSolana?.let { signSolana ->
            require(signSolana.rawTransactions.size == 1) {
                "Expected exactly one Solana raw transaction"
            }

            val txData =
                android.util.Base64.decode(
                    signSolana.rawTransactions.first(),
                    android.util.Base64.DEFAULT,
                ) ?: error("Invalid base64 transaction")

            return signRawTransaction(
                coinHexPubKey = keysignPayload.coin.hexPublicKey,
                txData = txData,
                signatures = signatures,
            )
        }

        val publicKey = PublicKey(vaultHexPublicKey.toHexByteArray(), PublicKeyType.ED25519)
        val input = getPreSignedInputData(keysignPayload)
        val hashes = TransactionCompiler.preImageHashes(coinType, input)
        val preSigningOutput =
            wallet.core.jni.proto.TransactionCompiler.PreSigningOutput.parseFrom(hashes)
                .checkError()
        val key = Numeric.toHexStringNoPrefix(preSigningOutput.data.toByteArray())
        val allSignatures = DataVector()
        val publicKeys = DataVector()
        val signature = signatures[key]?.getSignature() ?: error("Signature not found")
        if (!publicKey.verify(signature, preSigningOutput.data.toByteArray())) {
            error("Signature verification failed")
        }
        allSignatures.add(signature)
        publicKeys.add(publicKey.data())
        val compiledWithSignature =
            TransactionCompiler.compileWithSignatures(coinType, input, allSignatures, publicKeys)
        val output = Solana.SigningOutput.parseFrom(compiledWithSignature).checkError()
        return SignedTransactionResult(
            rawTransaction = output.encoded,
            transactionHash = output.transactionHash(),
        )
    }

    fun getSwapPreSignedInputData(keysignPayload: KeysignPayload): ByteArray {
        require(!keysignPayload.memo.isNullOrEmpty()) {
            "THORChain swap memo must not be null or empty for Solana swap transactions"
        }
        return getPreSignedInputData(keysignPayload)
    }

    fun getSwapSignedTransaction(
        inputData: ByteArray,
        signatures: Map<String, tss.KeysignResponse>,
    ): SignedTransactionResult {
        val publicKey = PublicKey(vaultHexPublicKey.toHexByteArray(), PublicKeyType.ED25519)

        val preHashes = TransactionCompiler.preImageHashes(coinType, inputData)
        val preSigningOutput =
            wallet.core.jni.proto.TransactionCompiler.PreSigningOutput.parseFrom(preHashes)
                .checkError()

        val allSignatures = DataVector()
        val allPublicKeys = DataVector()
        val key = Numeric.toHexStringNoPrefix(preSigningOutput.data.toByteArray())

        val signature = signatures[key]?.getSignature() ?: error("Signature not found")

        val verified = publicKey.verify(signature, (preSigningOutput.data).toByteArray())
        if (!verified) {
            error("Signature verification failed")
        }
        allSignatures.add(signature)

        allPublicKeys.add(publicKey.data())
        val compileWithSignature =
            TransactionCompiler.compileWithSignatures(
                coinType,
                inputData,
                allSignatures,
                allPublicKeys,
            )

        val output = Solana.SigningOutput.parseFrom(compileWithSignature).checkError()
        return SignedTransactionResult(
            rawTransaction = output.encoded,
            transactionHash = output.transactionHash(),
        )
    }

    fun getZeroSignedTransaction(keysignPayload: KeysignPayload): String {
        val publicKey = PublicKey(vaultHexPublicKey.toHexByteArray(), PublicKeyType.ED25519)
        val input = getPreSignedInputData(keysignPayload)
        val allSignatures = DataVector()
        val publicKeys = DataVector()
        allSignatures.add("0".repeat(128).toHexByteArray())
        publicKeys.add(publicKey.data())
        val compiledWithSignature =
            TransactionCompiler.compileWithSignatures(coinType, input, allSignatures, publicKeys)
        val output = Solana.SigningOutput.parseFrom(compiledWithSignature).checkError()
        return output.encoded
    }

    fun getVersionedMessage(keysignPayload: KeysignPayload): String {
        val input = getPreSignedInputData(keysignPayload)
        val preHashes = TransactionCompiler.preImageHashes(coinType, input)
        val dataMessage =
            wallet.core.jni.proto.TransactionCompiler.PreSigningOutput.parseFrom(preHashes)
                .checkError()
                .data
                .toByteArray()

        return Base64.encode(dataMessage)
    }

    /**
     * Computes the pre-image hash(es) to sign for a raw (already-decoded) Solana transaction.
     *
     * The message bytes are extracted verbatim from the wire transaction so the hash matches what
     * is broadcast, rather than re-serializing through WalletCore.
     *
     * @param txData the decoded wire transaction bytes
     * @return the message bytes to sign, hex-encoded without a `0x` prefix
     */
    internal fun getPreSignedImageHashForRaw(txData: ByteArray): List<String> {
        val messageBytes = extractRawMessage(txData).messageBytes

        return listOf(Numeric.toHexStringNoPrefix(messageBytes))
    }

    /**
     * Assembles a broadcastable Solana transaction for a raw (already-serialized) wire transaction.
     *
     * The verified 64-byte signature is spliced into the signer-0 slot of the original bytes so the
     * transaction is broadcast byte-for-byte, avoiding the `AccountLoadedTwice` error that
     * WalletCore re-serialization causes for v0 transactions with address-table lookups.
     *
     * @param coinHexPubKey the signer's ed25519 public key, hex-encoded
     * @param txData the decoded wire transaction bytes
     * @param signatures TSS signatures keyed by the hex-encoded message hash
     * @return the Base58-encoded signed transaction and its transaction hash (the signer-0
     *   signature)
     */
    internal fun signRawTransaction(
        coinHexPubKey: String,
        txData: ByteArray,
        signatures: Map<String, tss.KeysignResponse>,
    ): SignedTransactionResult {
        val pubkeyData = coinHexPubKey.toHexByteArray()
        val publicKey = PublicKey(pubkeyData, PublicKeyType.ED25519)

        val rawMessage = extractRawMessage(txData)
        val messageBytes = rawMessage.messageBytes

        val key = Numeric.toHexStringNoPrefix(messageBytes)
        val signature = signatures[key]?.getSignature() ?: error("Signature not found")

        if (!publicKey.verify(signature, messageBytes)) {
            error("Signature verification failed")
        }
        require(signature.size == SOLANA_SIGNATURE_LENGTH) {
            "Unexpected Solana signature length: ${signature.size}"
        }

        // Splice the signature into the original transaction bytes verbatim (mirroring iOS
        // `replaceSubrange`), so a v0 tx with address-table lookups is broadcast byte-for-byte
        // instead of being re-serialized through WalletCore's compiler (which duplicates accounts
        // and yields `AccountLoadedTwice`). `extractRawMessage` already copied out the message
        // region, so overwriting signer-0's slot in `txData` in place is safe.
        signature.copyInto(txData, destinationOffset = rawMessage.signatureOffset)

        return SignedTransactionResult(
            rawTransaction = Base58.encodeNoCheck(txData),
            transactionHash = Base58.encodeNoCheck(signature),
        )
    }

    /**
     * The location of the fee payer's signature slot and the message bytes within a serialized
     * Solana transaction.
     *
     * @property signatureOffset byte offset of signer index 0's 64-byte signature slot
     * @property messageBytes the serialized message that ed25519 signs and that is broadcast
     *   verbatim
     */
    internal class RawSolanaMessage(val signatureOffset: Int, val messageBytes: ByteArray)

    /**
     * Parses a serialized Solana transaction to locate the signer-0 signature slot and the message.
     *
     * A wire transaction is laid out as `[shortvec signature count][signatures...][message]`. The
     * fee payer is signer index 0, so its signature slot begins right after the shortvec prefix and
     * the message follows all signature slots.
     *
     * @param txData the decoded transaction bytes
     * @return the signer-0 signature offset and the raw message bytes
     */
    internal fun extractRawMessage(txData: ByteArray): RawSolanaMessage {
        val compactU16 = SolanaCompactU16.decode(txData)
        val signatureCount = compactU16.value
        val messageStart = compactU16.bytesRead + signatureCount * SOLANA_SIGNATURE_LENGTH
        // Strict `<` (matching iOS `Solana.swift`) rejects a zero-length message: a real Solana
        // message is never empty, so `messageStart == txData.size` means the buffer was truncated
        // and must not be hashed as an empty pre-image.
        require(signatureCount > 0 && messageStart < txData.size) {
            "Malformed Solana transaction: signature section out of bounds"
        }
        return RawSolanaMessage(
            signatureOffset = compactU16.bytesRead,
            messageBytes = txData.copyOfRange(messageStart, txData.size),
        )
    }
}

/**
 * A decoded Solana compact-u16 (shortvec).
 *
 * @property value the decoded integer value
 * @property bytesRead the number of bytes the encoding consumed
 */
internal data class CompactU16(val value: Int, val bytesRead: Int)

private const val SOLANA_COMPACT_U16_MAX_BYTES = 3
private const val SOLANA_COMPACT_U16_CONTINUATION_BIT = 0x80
private const val SOLANA_COMPACT_U16_VALUE_BITS = 0x7F
private const val SOLANA_COMPACT_U16_MAX_VALUE = 0xFFFF

/**
 * Decodes a Solana compact-u16: 7 payload bits per byte, high bit = continuation, at most 3 bytes
 * (values 0..65535). Rejects non-canonical encodings — e.g. `[0x81, 0x00]` as a padded alias for
 * `1`, whose canonical encoding is the single byte `0x01` — matching Solana's own deserializer
 * (`short_vec.rs`'s `VisitError::Alias`), which the real network also rejects.
 */
internal object SolanaCompactU16 {

    fun decode(bytes: ByteArray, offset: Int = 0): CompactU16 {
        require(offset >= 0 && offset <= bytes.size) {
            "Malformed Solana transaction: compact-u16 offset out of bounds"
        }

        var value = 0

        repeat(SOLANA_COMPACT_U16_MAX_BYTES) { byteIndex ->
            val index = offset + byteIndex
            require(index < bytes.size) { "Malformed Solana transaction: truncated compact-u16" }

            val byte = bytes[index].toInt() and 0xFF
            val payload = byte and SOLANA_COMPACT_U16_VALUE_BITS
            val hasNext = byte and SOLANA_COMPACT_U16_CONTINUATION_BIT != 0
            value = value or (payload shl (byteIndex * 7))

            if (!hasNext) {
                require(value <= SOLANA_COMPACT_U16_MAX_VALUE) {
                    "Malformed Solana transaction: compact-u16 overflow"
                }
                val bytesRead = byteIndex + 1
                require(bytesRead == encodedLength(value)) {
                    "Malformed Solana transaction: non-canonical compact-u16"
                }
                return CompactU16(value = value, bytesRead = bytesRead)
            }

            require(byteIndex < SOLANA_COMPACT_U16_MAX_BYTES - 1) {
                "Malformed Solana transaction: compact-u16 too long"
            }
        }

        error("Unreachable compact-u16 decoder state")
    }

    private fun encodedLength(value: Int): Int =
        when {
            value <= 0x7F -> 1
            value <= 0x3FFF -> 2
            value <= SOLANA_COMPACT_U16_MAX_VALUE -> 3
            else -> error("compact-u16 overflow")
        }
}
