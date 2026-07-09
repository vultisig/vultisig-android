package com.vultisig.wallet.data.chains.helpers

import com.vultisig.wallet.data.common.toHexByteArray
import com.vultisig.wallet.data.crypto.checkError
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.SignedTransactionResult
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.tss.getSignature
import com.vultisig.wallet.data.utils.Numeric
import io.ktor.util.decodeBase64Bytes
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
            val allHashes = mutableListOf<String>()
            for (base64Tx in signSolana.rawTransactions) {
                val hashes = getPreSignedImageHashForRaw(base64Tx)
                allHashes.addAll(hashes)
            }
            return allHashes
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

            return signRawTransaction(
                coinHexPubKey = keysignPayload.coin.hexPublicKey,
                base64Transaction = signSolana.rawTransactions.first(),
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

    private fun getPreSignedImageHashForRaw(base64Transaction: String): List<String> =
        listOf(Numeric.toHexStringNoPrefix(parseRawTransaction(base64Transaction).message))

    private fun signRawTransaction(
        coinHexPubKey: String,
        base64Transaction: String,
        signatures: Map<String, tss.KeysignResponse>,
    ): SignedTransactionResult {
        val pubkeyData = coinHexPubKey.toHexByteArray()
        val publicKey = PublicKey(pubkeyData, PublicKeyType.ED25519)

        val transaction = parseRawTransaction(base64Transaction)
        val key = Numeric.toHexStringNoPrefix(transaction.message)
        val signature = signatures[key]?.getSignature() ?: error("Signature not found")
        if (!publicKey.verify(signature, transaction.message)) {
            error("Signature verification failed")
        }

        // Splice signer 0's signature into the original bytes; the message and any
        // further signer slots stay exactly as the dApp built them, so the broadcast
        // transaction matches the pre-image that was signed.
        val signedTransaction = transaction.bytes.copyOf()
        signature.copyInto(signedTransaction, destinationOffset = transaction.firstSignatureOffset)

        // Android broadcasts Solana transactions with the RPC's default base58
        // encoding (see SolanaApi.broadcastTransaction), unlike iOS which pins base64,
        // so the signed transaction and its hash are base58 here.
        return SignedTransactionResult(
            rawTransaction = Base58.encodeNoCheck(signedTransaction),
            transactionHash = Base58.encodeNoCheck(signature),
        )
    }

    /**
     * A dApp-supplied raw Solana transaction split into its wire envelope `[compact-u16 signature
     * count][count × 64-byte signature slot][message]`.
     *
     * [message] is the pre-image ed25519 signs verbatim; [firstSignatureOffset] is where signer 0's
     * slot begins, so the produced signature can be spliced back into [bytes] without disturbing
     * anything else.
     */
    private class RawSolanaTransaction(
        val bytes: ByteArray,
        val firstSignatureOffset: Int,
        val message: ByteArray,
    )

    /**
     * Parses the `[shortvec(numSignatures)][numSignatures × 64-byte slot][message]` envelope of a
     * dApp-supplied raw transaction and returns its message bytes verbatim.
     *
     * Signing over these original bytes — rather than decoding into WalletCore's representation and
     * re-serializing it (the `TransactionDecoder` → `SigningInput.rawMessage` →
     * `TransactionCompiler` round trip) — keeps the pre-image hash independent of WalletCore's
     * encoder. That re-encode is not guaranteed to reproduce the original bytes for a v0 message
     * referencing an Address Lookup Table (the standard shape for DEX/aggregator swaps), which
     * would make co-signing devices compute mismatching hashes and stall the ceremony.
     */
    private fun parseRawTransaction(base64Transaction: String): RawSolanaTransaction {
        val bytes = base64Transaction.decodeBase64Bytes()

        var offset = 0
        var numSignatures = 0
        var shift = 0
        // Solana compact-u16 (shortvec): 7 payload bits per byte, high bit = continuation.
        while (offset < bytes.size) {
            val byte = bytes[offset].toInt() and 0xFF
            numSignatures = numSignatures or ((byte and 0x7F) shl shift)
            offset++
            if (byte and 0x80 == 0) break
            shift += 7
            if (shift > 14) error("Malformed signature count in Solana transaction")
        }
        check(numSignatures >= 1) { "Solana transaction declares no signatures" }

        val firstSignatureOffset = offset
        val messageOffset = offset + numSignatures * 64
        check(messageOffset < bytes.size) {
            "Solana transaction too short for its $numSignatures declared signature(s)"
        }
        return RawSolanaTransaction(
            bytes = bytes,
            firstSignatureOffset = firstSignatureOffset,
            message = bytes.copyOfRange(messageOffset, bytes.size),
        )
    }
}
