package com.vultisig.wallet.data.chains.helpers

import com.google.protobuf.ByteString
import com.vultisig.wallet.data.crypto.checkError
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.SignedTransactionResult
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.tss.getSignature
import com.vultisig.wallet.data.tss.getSignatureWithRecoveryID
import com.vultisig.wallet.data.utils.Numeric
import java.security.MessageDigest
import timber.log.Timber
import wallet.core.jni.CoinType
import wallet.core.jni.DataVector
import wallet.core.jni.PublicKey
import wallet.core.jni.PublicKeyType
import wallet.core.jni.TransactionCompiler
import wallet.core.jni.proto.Ripple

@OptIn(ExperimentalStdlibApi::class)
object RippleHelper {

    const val DEFAULT_EXISTENTIAL_DEPOSIT = 1000000

    fun getPreSignedInputData(keysignPayload: KeysignPayload): ByteArray {
        require(keysignPayload.coin.chain == Chain.Ripple) { "Coin is not XRP" }

        val rippleSpecific =
            keysignPayload.blockChainSpecific as? BlockChainSpecific.Ripple
                ?: error("getPreSignedInputData: fail to get account number and sequence")
        val (sequence, gas, lastLedgerSequence) = rippleSpecific

        val publicKey =
            PublicKey(keysignPayload.coin.hexPublicKey.hexToByteArray(), PublicKeyType.SECP256K1)

        val memoValue = keysignPayload.memo

        val input =
            Ripple.SigningInput.newBuilder()
                .setFee(gas.toLong())
                .setSequence(sequence.toInt())
                .setAccount(keysignPayload.coin.address)
                .setPublicKey(ByteString.copyFrom(publicKey.data()))
                .setLastLedgerSequence(lastLedgerSequence.toInt())

        val operation =
            Ripple.OperationPayment.newBuilder()
                .setDestination(keysignPayload.toAddress)
                .setAmount(keysignPayload.toAmount.toLong())

        // The destination tag comes from its own send-form field, carried in the first-class
        // RippleSpecific field. The memo field is an independent free-text memo.
        val destinationTag = rippleSpecific.destinationTag
        val memo = memoValue?.takeIf { it.isNotBlank() }

        when {
            // Tag + free-text memo: WalletCore's OperationPayment can't carry both, so hand-build
            // a Payment that sets DestinationTag and a Memos blob.
            destinationTag != null && memo != null ->
                input.setRawJson(
                    buildPaymentRawJson(
                        keysignPayload = keysignPayload,
                        gas = gas,
                        sequence = sequence,
                        lastLedgerSequence = lastLedgerSequence,
                        destinationTag = destinationTag.toLong(),
                        memo = memo,
                    )
                )

            destinationTag != null -> {
                operation.setDestinationTag(destinationTag.toLong())
                input.setOpPayment(operation)
            }

            memo != null -> {
                // No first-class tag: a purely numeric memo is the legacy destination-tag carrier
                // (older payloads and swap contracts); any other memo is an on-chain Memos blob.
                val memoAsLong = memo.toLongOrNull()
                if (memoAsLong != null) {
                    operation.setDestinationTag(memoAsLong)
                    input.setOpPayment(operation)
                } else {
                    input.setRawJson(
                        buildPaymentRawJson(
                            keysignPayload = keysignPayload,
                            gas = gas,
                            sequence = sequence,
                            lastLedgerSequence = lastLedgerSequence,
                            destinationTag = null,
                            memo = memo,
                        )
                    )
                }
            }

            else -> input.setOpPayment(operation)
        }
        return input.build().toByteArray()
    }

    /**
     * Hand-builds a Payment rawJSON carrying an on-chain Memos blob (and optionally a
     * [destinationTag]) for cases WalletCore's typed [Ripple.OperationPayment] can't express.
     */
    private fun buildPaymentRawJson(
        keysignPayload: KeysignPayload,
        gas: ULong,
        sequence: ULong,
        lastLedgerSequence: ULong,
        destinationTag: Long?,
        memo: String,
    ): String {
        val txJson: MutableMap<String, Any> =
            mutableMapOf(
                "TransactionType" to "Payment",
                "Account" to keysignPayload.coin.address,
                "Destination" to keysignPayload.toAddress,
                "Amount" to keysignPayload.toAmount.toString(),
                "Fee" to gas.toString(),
                "Sequence" to sequence,
                "LastLedgerSequence" to lastLedgerSequence,
                "Memos" to
                    listOf(
                        mapOf(
                            "Memo" to
                                mapOf(
                                    "MemoData" to
                                        memo.toByteArray(Charsets.UTF_8).joinToString("") {
                                            "%02x".format(it)
                                        }
                                )
                        )
                    ),
            )
        if (destinationTag != null) {
            txJson["DestinationTag"] = destinationTag
        }
        return try {
            org.json.JSONObject(txJson).toString()
        } catch (e: Exception) {
            Timber.e("Failed to create JSON string ${e.message}")
            error("Failed to create JSON string ${e.message}")
        }
    }

    fun getPreSignedImageHash(keysignPayload: KeysignPayload): List<String> {
        val inputData = getPreSignedInputData(keysignPayload)

        val hashes = TransactionCompiler.preImageHashes(CoinType.XRP, inputData)
        val preSigningOutput =
            wallet.core.jni.proto.TransactionCompiler.PreSigningOutput.parseFrom(hashes)
                .checkError()
        return listOf(Numeric.toHexStringNoPrefix(preSigningOutput.dataHash.toByteArray()))
    }

    fun getSignedTransaction(
        keysignPayload: KeysignPayload,
        signatures: Map<String, tss.KeysignResponse>,
    ): SignedTransactionResult {
        val publicKey =
            PublicKey(keysignPayload.coin.hexPublicKey.hexToByteArray(), PublicKeyType.SECP256K1)

        val inputData = getPreSignedInputData(keysignPayload = keysignPayload)
        val hashes = TransactionCompiler.preImageHashes(CoinType.XRP, inputData)
        val preSigningOutput =
            wallet.core.jni.proto.TransactionCompiler.PreSigningOutput.parseFrom(hashes)
                .checkError()

        val allSignatures = DataVector()
        val publicKeys = DataVector()
        val key = Numeric.toHexStringNoPrefix(preSigningOutput.dataHash.toByteArray())
        signatures[key]?.getSignature() ?: error("Signature not found")

        signatures[key]?.let {
            if (
                !publicKey.verify(
                    it.getSignatureWithRecoveryID(),
                    preSigningOutput.dataHash.toByteArray(),
                )
            ) {
                Timber.e("Invalid signature")
                error("Invalid signature")
            }
            allSignatures.add(it.getSignatureWithRecoveryID())
            publicKeys.add(publicKey.data())
        }

        val compileWithSignature =
            TransactionCompiler.compileWithSignatures(
                CoinType.XRP,
                inputData,
                allSignatures,
                publicKeys,
            )

        val output = Ripple.SigningOutput.parseFrom(compileWithSignature)

        if (output.errorMessage.isNotEmpty()) {
            val errorMessage = output.errorMessage
            Timber.e("$errorMessage")
            error(errorMessage)
        }

        val signedTransaction = output.encoded.toByteArray()
        return SignedTransactionResult(
            rawTransaction = signedTransaction.toHexString(),
            transactionHash = calculateTransactionHash(signedTransaction),
        )
    }

    /**
     * Derives the canonical XRPL transaction ID: the first 32 bytes (SHA-512Half) of the SHA-512
     * digest of the signed transaction blob prefixed with the transaction hash prefix 0x54584E00.
     * WalletCore's [Ripple.SigningOutput] does not expose the hash, so it must be computed here to
     * match the value the node returns and the explorer indexes - an empty hash breaks status
     * polling, explorer deep-links, and duplicate-broadcast recovery.
     */
    fun calculateTransactionHash(signedTransaction: ByteArray): String {
        val digest =
            MessageDigest.getInstance("SHA-512").digest(TRANSACTION_HASH_PREFIX + signedTransaction)
        return digest.copyOfRange(0, 32).toHexString(HexFormat.UpperCase)
    }

    // 0x54584E00 is the ASCII "TXN" mnemonic plus a zero byte: XRPL's transaction-ID hash prefix.
    private val TRANSACTION_HASH_PREFIX = byteArrayOf(0x54, 0x58, 0x4E, 0x00)
}
