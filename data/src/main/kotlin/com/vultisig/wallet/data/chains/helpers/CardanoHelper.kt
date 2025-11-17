package com.vultisig.wallet.data.chains.helpers

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.SignedTransactionResult
import com.google.protobuf.ByteString
import com.vultisig.wallet.data.crypto.CardanoUtils
import com.vultisig.wallet.data.crypto.checkError
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import wallet.core.jni.proto.Cardano
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.tss.getSignature
import com.vultisig.wallet.data.utils.Numeric
import com.vultisig.wallet.data.utils.Numeric.hexStringToByteArray
import timber.log.Timber
import wallet.core.java.AnySigner
import wallet.core.jni.CoinType
import wallet.core.jni.DataVector
import wallet.core.jni.PublicKey
import wallet.core.jni.PublicKeyType
import wallet.core.jni.TransactionCompiler
import wallet.core.jni.proto.Cardano.TransactionPlan
import wallet.core.jni.proto.Common.SigningError

@OptIn(ExperimentalStdlibApi::class)
object CardanoHelper {

    private const val ESTIMATE_TRANSACTION_FEE: Long = 180_000

    fun getPreSignedInputData(keysignPayload: KeysignPayload): ByteArray {
        require(keysignPayload.coin.chain == Chain.Cardano) { "Coin is not ada" }

        val (_, sendMaxAmount, ttl) = keysignPayload.blockChainSpecific as? BlockChainSpecific.Cardano
            ?: error("fail to get Cardano chain specific parameters")

        // For Cardano, we don't use UTXOs from Blockchair since it doesn't support Cardano
        // Instead, we create a simplified input structure
        var input = Cardano.SigningInput.newBuilder()
            .setTransferMessage(
                Cardano.Transfer.newBuilder()
                    .setAmount(keysignPayload.toAmount.toLong())
                    .setToAddress(keysignPayload.toAddress)
                    .setUseMaxAmount(sendMaxAmount)
                    .setChangeAddress(keysignPayload.coin.address)
                    .setForceFee(ESTIMATE_TRANSACTION_FEE)
            )
            // TODO: Implement memo support when WalletCore adds Cardano metadata support
            .setTtl(ttl.toLong())

        // Add UTXOs to the input
        for (inputUtxo in keysignPayload.utxos) {
            val utxo = Cardano.TxInput.newBuilder()
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

        return input.build().toByteArray()
    }

    fun getPreSignedImageHash(keysignPayload: KeysignPayload): List<String> {
        val inputData = getPreSignedInputData(keysignPayload)
        val hashes = TransactionCompiler.preImageHashes(
            CoinType.CARDANO,
            inputData
        )
        val preSigningOutput =
            wallet.core.jni.proto.TransactionCompiler.PreSigningOutput.parseFrom(hashes)
        if (preSigningOutput.errorMessage.isNotEmpty()) {
            val errorMessage = preSigningOutput.errorMessage
            Timber.e("$errorMessage")
            error(errorMessage)
        }
        return listOf(Numeric.toHexStringNoPrefix(preSigningOutput.dataHash.toByteArray()))
    }


    fun getSignedTransaction(
        vaultHexPublicKey: String,
        vaultHexChainCode: String,
        keysignPayload: KeysignPayload,
        signatures: Map<String, tss.KeysignResponse>,
    ): SignedTransactionResult {

        val extendedKeyData = CardanoUtils.createExtendedKey(
            spendingKeyHex = vaultHexPublicKey,
            chainCodeHex = vaultHexChainCode
        )
        val spendingKeyData = vaultHexPublicKey.hexToByteArray()
        val verificationKey = PublicKey(
            spendingKeyData,
            PublicKeyType.ED25519
        )
        val inputData = getPreSignedInputData(keysignPayload)
        val hashes = TransactionCompiler.preImageHashes(
            CoinType.CARDANO,
            inputData
        )
        val preSigningOutput =
            wallet.core.jni.proto.TransactionCompiler.PreSigningOutput.parseFrom(hashes)
                .checkError()
        val allSignatures = DataVector()
        val publicKeys = DataVector()

        val key = Numeric.toHexStringNoPrefix(preSigningOutput.dataHash.toByteArray())

        val signature = signatures[key]
            ?.getSignature()
            ?: error("Signature not found")

        if (!verificationKey.verify(
                signature,
                preSigningOutput.dataHash.toByteArray()
            )
        ) {
            error("Cardano signature verification failed")
        }

        allSignatures.add(signature)
        publicKeys.add(extendedKeyData)

        val compileWithSignature = TransactionCompiler.compileWithSignatures(
            CoinType.CARDANO,
            inputData,
            allSignatures,
            publicKeys
        )

        val output = Cardano.SigningOutput.parseFrom(compileWithSignature)
            .checkError()
        var transactionHash = CardanoUtils.calculateCardanoTransactionHash(output.encoded.toByteArray())
        return SignedTransactionResult(
            rawTransaction = output.encoded.toByteArray().toHexString(),
            transactionHash = transactionHash
        )
    }

    // TODO: Switch to plan calculation method
    fun getCardanoTransactionPlan(keysignPayload: KeysignPayload): TransactionPlan {
        val signingInput = Cardano.SigningInput.parseFrom(getPreSignedInputData(keysignPayload))
        val plan = AnySigner.plan(signingInput, CoinType.CARDANO, TransactionPlan.parser())
        if (plan.error == SigningError.OK) {
            return plan
        }

        Timber.e("Cardano Plan Error: ${plan.error.name}")

        throw RuntimeException("Signing Error During Plan calculation")
    }
}