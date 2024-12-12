package com.vultisig.wallet.data.chains.helpers

import com.google.protobuf.ByteString
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.SignedTransactionResult
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.tss.getSignature
import com.vultisig.wallet.data.utils.Numeric
import wallet.core.jni.AnyAddress
import wallet.core.jni.CoinType
import wallet.core.jni.DataVector
import wallet.core.jni.proto.Ripple
import wallet.core.jni.PublicKey
import wallet.core.jni.PublicKeyType
import wallet.core.jni.TransactionCompiler

@OptIn(ExperimentalStdlibApi::class)
object RippleHelper {

    fun getPreSignedInputData(keysignPayload: KeysignPayload): ByteArray {
        require(keysignPayload.coin.chain == Chain.Ton) { "Coin is not XRP" }


        val (sequence, gas) = keysignPayload.blockChainSpecific as? BlockChainSpecific.Ripple
            ?: throw RuntimeException("getPreSignedInputData: fail to get account number and sequence")

        val publicKey = PublicKey(
            keysignPayload.coin.hexPublicKey.hexToByteArray(),
            PublicKeyType.SECP256K1
        )

        val operation = Ripple.OperationPayment.newBuilder()
            .apply {
                keysignPayload.memo?.let {
                    destinationTag = it.toLongOrNull() ?: 0L
                }
                destination = keysignPayload.toAddress
                amount = keysignPayload.toAmount.toLong()
            }.build()

        val input = Ripple.SigningInput.newBuilder()
            .setFee(gas.toLong())
            .setSequence(sequence.toInt())
            .setAccount(keysignPayload.coin.address)
            .setPublicKey(
                ByteString.copyFrom(publicKey.data())
            )
            .setOpPayment(operation).build()

        return input.toByteArray()
    }

    fun getPreSignedImageHash(keysignPayload: KeysignPayload): List<String> {
        val inputData = getPreSignedInputData(
            keysignPayload
        )

        val hashes = TransactionCompiler.preImageHashes(
            CoinType.XRP,
            inputData
        )
        val preSigningOutput =
            wallet.core.jni.proto.TransactionCompiler.PreSigningOutput.parseFrom(hashes)
        if (preSigningOutput.errorMessage.isNotEmpty()) {
            println(preSigningOutput.errorMessage)
            throw Exception(preSigningOutput.errorMessage)
        }
        return listOf(preSigningOutput.dataHash.toByteArray().toHexString())
    }

    fun getSignedTransaction(
        keysignPayload: KeysignPayload,
        signatures: Map<String, tss.KeysignResponse>,
    ): SignedTransactionResult {
        val publicKey = PublicKey(
            keysignPayload.coin.hexPublicKey.hexToByteArray(),
            PublicKeyType.SECP256K1
        )

        val inputData = getPreSignedInputData(
            keysignPayload = keysignPayload,
        )
        val hashes = TransactionCompiler.preImageHashes(
            CoinType.XRP,
            inputData
        )
        val preSigningOutput = wallet.core.jni.proto.TransactionCompiler.PreSigningOutput
            .parseFrom(hashes)

        if (preSigningOutput.errorMessage.isNotEmpty()) {
            println(preSigningOutput.errorMessage)
            throw RuntimeException(preSigningOutput.errorMessage)
        }

        val allSignatures = DataVector()
        val publicKeys = DataVector()
        val signature = signatures[Numeric.toHexStringNoPrefix(preSigningOutput.data.toByteArray())]
            ?.getSignature()
            ?: throw Exception("Signature not found")



        if (!publicKey.verifyAsDER(
                signature,
                preSigningOutput.dataHash.toByteArray()
            )
        ) {
            val errorMessage = "Invalid signature"
            println(errorMessage)
            throw RuntimeException(errorMessage)
        }

        allSignatures.add(signature)
        publicKeys.add(publicKey.data())

        val compileWithSignature = TransactionCompiler.compileWithSignatures(
            CoinType.XRP,
            inputData,
            allSignatures,
            publicKeys
        )

        val output = wallet.core.jni.proto.Ripple.SigningOutput
            .parseFrom(compileWithSignature)

        if (output.errorMessage.isNotEmpty()) {
            val errorMessage = output.errorMessage
            println("errorMessage: $errorMessage")
            throw RuntimeException(errorMessage)
        }

        return SignedTransactionResult(
            rawTransaction = output.encoded.toStringUtf8(),
            transactionHash = ""
        )
    }

}