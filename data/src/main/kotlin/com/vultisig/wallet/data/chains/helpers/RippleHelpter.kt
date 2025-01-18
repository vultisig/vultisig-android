package com.vultisig.wallet.data.chains.helpers

import com.google.protobuf.ByteString
import com.vultisig.wallet.data.crypto.checkError
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.SignedTransactionResult
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.tss.getSignature
import com.vultisig.wallet.data.utils.Numeric
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


        val (sequence, gas) = keysignPayload.blockChainSpecific as? BlockChainSpecific.Ripple
            ?: error("getPreSignedInputData: fail to get account number and sequence")

        val publicKey = PublicKey(
            keysignPayload.coin.hexPublicKey.hexToByteArray(),
            PublicKeyType.SECP256K1
        )

        val operation = Ripple.OperationPayment.newBuilder()
            .setDestination(keysignPayload.toAddress)
            .setAmount(keysignPayload.toAmount.toLong())
            .let {
                if (keysignPayload.memo != null)
                    it.setDestinationTag(keysignPayload.memo.toLongOrNull() ?: 0L)
                else it
            }.build()

        val input = Ripple.SigningInput.newBuilder()
            .setFee(gas.toLong())
            .setSequence(sequence.toInt())
            .setAccount(keysignPayload.coin.address)
            .setPublicKey(
                ByteString.copyFrom(publicKey.data())
            )
            .setOpPayment(operation)
            .build()
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
                .checkError()
        return listOf(Numeric.toHexString(preSigningOutput.dataHash.toByteArray()))
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
            .checkError()

        val allSignatures = DataVector()
        val publicKeys = DataVector()
        val key = Numeric.toHexString(preSigningOutput.dataHash.toByteArray())
        signatures[key]
            ?.getSignature()
            ?: error("Signature not found")

        signatures[key]?.let {
            if (!publicKey.verifyAsDER(
                    it.derSignature.hexToByteArray(),
                    preSigningOutput.dataHash.toByteArray()
                )
            ) {
                Timber.e("Invalid signature")
                error("Invalid signature")
            }
            allSignatures.add(it.derSignature.hexToByteArray())
            publicKeys.add(publicKey.data())
        }


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
            Timber.e("$errorMessage")
            error(errorMessage)
        }

        return SignedTransactionResult(
            rawTransaction = output.encoded.toByteArray().toHexString(),
            transactionHash = ""
        )
    }

}