package com.vultisig.wallet.data.chains.helpers

import com.vultisig.wallet.data.common.toHexByteArray
import com.vultisig.wallet.data.crypto.checkError
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.SignedTransactionResult
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.tss.getSignature
import com.vultisig.wallet.data.utils.Numeric
import io.ktor.util.encodeBase64
import wallet.core.jni.AnyAddress
import wallet.core.jni.Base64
import wallet.core.jni.CoinType
import wallet.core.jni.DataVector
import wallet.core.jni.PublicKey
import wallet.core.jni.PublicKeyType
import wallet.core.jni.SolanaAddress
import wallet.core.jni.TransactionCompiler
import wallet.core.jni.proto.Solana
import java.math.BigInteger

internal const val SOLANA_PRIORITY_FEE_PRICE = 1000000L
internal const val SOLANA_PRIORITY_FEE_LIMIT = 100000

class SolanaHelper(
    private val vaultHexPublicKey: String,
) {

    private val coinType = CoinType.SOLANA

    companion object {
        val DefaultFeeInLamports: BigInteger = 1000000.toBigInteger()
    }

    private fun getPreSignedInputData(keysignPayload: KeysignPayload): ByteArray {
        val solanaSpecific = keysignPayload.blockChainSpecific as? BlockChainSpecific.Solana
            ?: error("Invalid blockChainSpecific")
        if (keysignPayload.coin.chain != Chain.Solana) {
            error("Chain is not Solana")
        }
        val toAddress = AnyAddress(
            keysignPayload.toAddress,
            coinType
        )

        val input = Solana.SigningInput.newBuilder()
            .setV0Msg(true)
            .setRecentBlockhash(solanaSpecific.recentBlockHash)
            .setSender(keysignPayload.coin.address)
            .setPriorityFeePrice(
                Solana.PriorityFeePrice.newBuilder()
                    .setPrice(SOLANA_PRIORITY_FEE_PRICE)
                    .build()
            )
            .setPriorityFeeLimit(
                Solana.PriorityFeeLimit.newBuilder()
                    .setLimit(solanaSpecific.priorityLimit.toInt())
                    .build()
            )

        if (keysignPayload.coin.isNativeToken) {
            val transfer = Solana.Transfer.newBuilder()
                .setRecipient(toAddress.description())
                .setValue(keysignPayload.toAmount.toLong())
            keysignPayload.memo?.let {
                transfer.setMemo(it)
            }

            return input
                .setTransferTransaction(transfer.build())
                .build()
                .toByteArray()
        } else {
            if (!solanaSpecific.fromAddressPubKey.isNullOrEmpty() && !solanaSpecific.toAddressPubKey.isNullOrEmpty()) {
                val transfer = Solana.TokenTransfer.newBuilder()
                    .setTokenMintAddress(keysignPayload.coin.contractAddress)
                    .setSenderTokenAddress(solanaSpecific.fromAddressPubKey)
                    .setRecipientTokenAddress(solanaSpecific.toAddressPubKey)
                    .setAmount(keysignPayload.toAmount.toLong())
                    .setDecimals(keysignPayload.coin.decimal)
                    .setTokenProgramIdValue(if (solanaSpecific.programId) 1 else 0)

                return input
                    .setTokenTransferTransaction(transfer.build())
                    .build()
                    .toByteArray()
            } else {
                val receiverAddress = SolanaAddress(toAddress.description())
                val generatedRecipientAssociatedAddress = if (solanaSpecific.programId) {
                    receiverAddress.token2022Address(keysignPayload.coin.contractAddress)
                } else {
                    receiverAddress.defaultTokenAddress(
                        keysignPayload.coin.contractAddress
                    )
                }
                val transferTokenMessage =
                    Solana.CreateAndTransferToken.newBuilder()
                        .setRecipientMainAddress(toAddress.description())
                        .setTokenMintAddress(keysignPayload.coin.contractAddress)
                        .setRecipientTokenAddress(generatedRecipientAssociatedAddress)
                        .setSenderTokenAddress(solanaSpecific.fromAddressPubKey)
                        .setAmount(keysignPayload.toAmount.toLong())
                        .setDecimals(keysignPayload.coin.decimal)
                        .setTokenProgramIdValue(if (solanaSpecific.programId) 1 else 0)


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
        val hashes = TransactionCompiler.preImageHashes(
            coinType,
            result
        )
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
                signatures = signatures
            )
        }

        val publicKey = PublicKey(
            vaultHexPublicKey.toHexByteArray(),
            PublicKeyType.ED25519
        )
        val input = getPreSignedInputData(keysignPayload)
        val hashes = TransactionCompiler.preImageHashes(
            coinType,
            input
        )
        val preSigningOutput =
            wallet.core.jni.proto.TransactionCompiler.PreSigningOutput.parseFrom(hashes)
                .checkError()
        val key = Numeric.toHexStringNoPrefix(preSigningOutput.data.toByteArray())
        val allSignatures = DataVector()
        val publicKeys = DataVector()
        val signature = signatures[key]?.getSignature() ?: error("Signature not found")
        if (!publicKey.verify(
                signature,
                preSigningOutput.data.toByteArray()
            )
        ) {
            error("Signature verification failed")
        }
        allSignatures.add(signature)
        publicKeys.add(publicKey.data())
        val compiledWithSignature =
            TransactionCompiler.compileWithSignatures(
                coinType,
                input,
                allSignatures,
                publicKeys
            )
        val output = Solana.SigningOutput.parseFrom(compiledWithSignature)
            .checkError()
        return SignedTransactionResult(
            rawTransaction = output.encoded,
            transactionHash = output.encoded.take(64).encodeBase64()
        )
    }

    fun getSwapSignedTransaction(
        inputData: ByteArray,
        signatures: Map<String, tss.KeysignResponse>,
    ): SignedTransactionResult {
        val publicKey =
            PublicKey(
                vaultHexPublicKey.toHexByteArray(),
                PublicKeyType.ED25519
            )

        val preHashes = TransactionCompiler.preImageHashes(
            coinType,
            inputData
        )
        val preSigningOutput =
            wallet.core.jni.proto.TransactionCompiler.PreSigningOutput.parseFrom(preHashes)
                .checkError()

        val allSignatures = DataVector()
        val allPublicKeys = DataVector()
        val key = Numeric.toHexStringNoPrefix(preSigningOutput.data.toByteArray())

        val signature =
            signatures[key]?.getSignature() ?: error("Signature not found")

        val verified = publicKey.verify(
            signature,
            (preSigningOutput.data).toByteArray()
        )
        if (!verified) {
            error("Signature verification failed")
        }
        allSignatures.add(signature)

        allPublicKeys.add(publicKey.data())
        val compileWithSignature = TransactionCompiler.compileWithSignatures(
            coinType,
            inputData,
            allSignatures,
            allPublicKeys
        )

        val output = Solana.SigningOutput.parseFrom(compileWithSignature)
            .checkError()
        return SignedTransactionResult(
            rawTransaction = output.encoded,
            transactionHash = output.encoded.take(64).encodeBase64()
        )
    }


    fun getZeroSignedTransaction(
        keysignPayload: KeysignPayload,
    ): String {
        val publicKey = PublicKey(
            vaultHexPublicKey.toHexByteArray(),
            PublicKeyType.ED25519
        )
        val input = getPreSignedInputData(keysignPayload)
        val allSignatures = DataVector()
        val publicKeys = DataVector()
        allSignatures.add("0".repeat(128).toHexByteArray())
        publicKeys.add(publicKey.data())
        val compiledWithSignature =
            TransactionCompiler.compileWithSignatures(
                coinType,
                input,
                allSignatures,
                publicKeys
            )
        val output = Solana.SigningOutput.parseFrom(compiledWithSignature)
            .checkError()
        return output.encoded
    }

    fun getVersionedMessage(keysignPayload: KeysignPayload): String {
        val input = getPreSignedInputData(keysignPayload)
        val preHashes = TransactionCompiler.preImageHashes(
            coinType,
            input
        )
        val dataMessage =
            wallet.core.jni.proto.TransactionCompiler.PreSigningOutput.parseFrom(preHashes)
                .checkError().data.toByteArray()

        return Base64.encode(dataMessage)
    }

    private fun getPreSignedImageHashForRaw(base64Transaction: String): List<String> {
        val txData = android.util.Base64.decode(
            base64Transaction,
            android.util.Base64.DEFAULT
        )

        val decodedData = wallet.core.jni.TransactionDecoder.decode(
            coinType,
            txData
        )
        val decodingOutput = Solana.DecodingTransactionOutput.parseFrom(decodedData)

        if (decodingOutput.errorMessage.isNotEmpty()) {
            error(decodingOutput.errorMessage)
        }

        if (!decodingOutput.hasTransaction()) {
            error("Invalid transaction format")
        }

        val input = Solana.SigningInput.newBuilder()
            .setRawMessage(decodingOutput.transaction)
            .build()

        val inputData = input.toByteArray()

        val hashes = TransactionCompiler.preImageHashes(
            coinType,
            inputData
        )
        val preSigningOutput =
            wallet.core.jni.proto.TransactionCompiler.PreSigningOutput.parseFrom(hashes)
                .checkError()

        return listOf(Numeric.toHexStringNoPrefix(preSigningOutput.data.toByteArray()))
    }

    private fun signRawTransaction(
        coinHexPubKey: String,
        base64Transaction: String,
        signatures: Map<String, tss.KeysignResponse>,
    ): SignedTransactionResult {
        val pubkeyData = coinHexPubKey.toHexByteArray()
        val publicKey = PublicKey(
            pubkeyData,
            PublicKeyType.ED25519
        )

        val txData = android.util.Base64.decode(
            base64Transaction,
            android.util.Base64.DEFAULT
        )
            ?: error("Invalid base64 transaction")

        val decodedData = wallet.core.jni.TransactionDecoder.decode(
            coinType,
            txData
        )
        val decodingOutput = Solana.DecodingTransactionOutput.parseFrom(decodedData)

        if (decodingOutput.errorMessage.isNotEmpty()) {
            error(decodingOutput.errorMessage)
        }

        if (!decodingOutput.hasTransaction()) {
            error("Invalid transaction format")
        }

        val input = Solana.SigningInput.newBuilder()
            .setRawMessage(decodingOutput.transaction)
            .build()

        val inputData = input.toByteArray()

        val hashes = TransactionCompiler.preImageHashes(
            coinType,
            inputData
        )
        val preSigningOutput =
            wallet.core.jni.proto.TransactionCompiler.PreSigningOutput.parseFrom(hashes)

        if (preSigningOutput.errorMessage.isNotEmpty()) {
            error(preSigningOutput.errorMessage)
        }

        val key = Numeric.toHexStringNoPrefix(preSigningOutput.data.toByteArray())
        val signature = signatures[key]?.getSignature() ?: error("Signature not found")

        if (!publicKey.verify(
                signature,
                preSigningOutput.data.toByteArray()
            )
        ) {
            error("Signature verification failed")
        }

        val allSignatures = DataVector()
        val publicKeys = DataVector()
        allSignatures.add(signature)
        publicKeys.add(pubkeyData)

        val compiled = TransactionCompiler.compileWithSignatures(
            coinType,
            inputData,
            allSignatures,
            publicKeys
        )

        val output = Solana.SigningOutput.parseFrom(compiled)
            .checkError()

        return SignedTransactionResult(
            rawTransaction = output.encoded,
            transactionHash = output.encoded.take(64).encodeBase64()
        )
    }

}