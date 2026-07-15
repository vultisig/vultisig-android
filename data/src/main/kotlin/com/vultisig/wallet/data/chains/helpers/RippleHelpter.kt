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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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

    private val rawJsonParser = Json { ignoreUnknownKeys = true }

    fun getPreSignedInputData(keysignPayload: KeysignPayload): ByteArray {
        require(keysignPayload.coin.chain == Chain.Ripple) { "Coin is not XRP" }

        // dApp-supplied XRPL transaction (via the extension's GemWallet provider): the raw JSON is
        // already a complete transaction — Account, Fee, Sequence, LastLedgerSequence and amounts
        // are baked in — so sign it verbatim through WalletCore's rawJson path. Every co-signer
        // rebuilds identical signing bytes from the same JSON, matching the extension and
        // @vultisig/core-mpc byte-for-byte. Reconstructing an OperationPayment from
        // toAddress/toAmount would diverge and produce a non-matching MPC signature.
        keysignPayload.signRipple?.let { signRipple ->
            return buildDappRawJsonInputData(
                rawJson = signRipple.rawJson,
                expectedAccount = keysignPayload.coin.address,
                hexPublicKey = keysignPayload.coin.hexPublicKey,
            )
        }

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

        // When the memo merely echoes the tag (memo == the tag's canonical decimal), it is not a
        // distinct memo — drop it and sign tag-only. This keeps a co-signer that predates the
        // destination_tag field byte-identical: it reads the numeric memo and builds the same
        // DestinationTag, so the pre-image matches instead of diverging on a Memos blob.
        val memoEchoesTag = memo != null && memo == destinationTag?.toString()

        when {
            // Tag + a distinct free-text memo: WalletCore's OperationPayment can't carry both, so
            // hand-build a Payment that sets DestinationTag and a Memos blob.
            destinationTag != null && memo != null && !memoEchoesTag ->
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
                // No first-class tag: a canonical uint32 memo is the legacy destination-tag carrier
                // (older payloads and swap contracts); any other memo is an on-chain Memos blob.
                // Use the same canonical parser as the dedicated field, so "0", leading zeros and
                // out-of-uint32 values are kept as a plain memo instead of being silently
                // reinterpreted — or overflowed — into a DestinationTag. (iOS rejects a bare "0"
                // memo outright; we intentionally preserve it as free text rather than reject.)
                val memoAsTag = RippleDestinationTag.parseCanonicalDestinationTag(memo)
                if (memoAsTag != null) {
                    operation.setDestinationTag(memoAsTag.toLong())
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
                // org.json can't wrap a Kotlin ULong (a value class), so these would be dropped
                // from the JSON — pass Long so Sequence/LastLedgerSequence serialize as numbers.
                "Sequence" to sequence.toLong(),
                "LastLedgerSequence" to lastLedgerSequence.toLong(),
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

    /**
     * Builds the WalletCore [Ripple.SigningInput] for a dApp-supplied transaction ([SignRipple]),
     * signing the raw JSON verbatim. Fails closed first: rejects any transaction whose `Account`
     * differs from this vault's derived XRP address, so a co-signer can never sign a spend from an
     * account other than its own — the same defense as `@vultisig/core-mpc` 1.11.0.
     *
     * Only [setRawJson] and [setPublicKey] are set: the JSON is the source of truth for every tx
     * field, and the vault ECDSA public key (identical across all co-signers) is what WalletCore
     * embeds as `SigningPubKey` and hashes into the pre-image — so every device produces the same
     * signing bytes.
     */
    private fun buildDappRawJsonInputData(
        rawJson: String,
        expectedAccount: String,
        hexPublicKey: String,
    ): ByteArray {
        verifyDappTransactionAccount(rawJson, expectedAccount)

        val publicKey = PublicKey(hexPublicKey.hexToByteArray(), PublicKeyType.SECP256K1)

        return Ripple.SigningInput.newBuilder()
            .setRawJson(rawJson)
            .setPublicKey(ByteString.copyFrom(publicKey.data()))
            .build()
            .toByteArray()
    }

    /**
     * Fail-closed guard for a dApp-supplied [SignRipple] transaction: throws unless the JSON's
     * `Account` equals this vault's derived XRP [expectedAccount]. Pure (no JNI), so it is
     * unit-testable independently of WalletCore.
     */
    internal fun verifyDappTransactionAccount(rawJson: String, expectedAccount: String) {
        require(rawJson.isNotBlank()) { "SignRipple rawJson is empty" }
        val account =
            parseRawJsonAccount(rawJson)
                ?: error("SignRipple rawJson has no readable Account field")
        require(account == expectedAccount) {
            "SignRipple Account $account does not match this vault's XRP address $expectedAccount"
        }
    }

    /** Extracts the XRPL `Account` from a raw transaction JSON, or null if absent/unparseable. */
    internal fun parseRawJsonAccount(rawJson: String): String? =
        try {
            rawJsonParser
                .parseToJsonElement(rawJson)
                .jsonObject["Account"]
                ?.jsonPrimitive
                ?.contentOrNull
        } catch (e: Exception) {
            Timber.e("Failed to parse SignRipple rawJson: %s", e.message)
            null
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
