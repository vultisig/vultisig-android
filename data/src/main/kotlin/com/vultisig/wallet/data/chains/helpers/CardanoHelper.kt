package com.vultisig.wallet.data.chains.helpers

import CoinFactory.Companion.createCardanoExtendedKey
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.SignedTransactionResult
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import wallet.core.jni.proto.Cardano
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.tss.getSignature
import com.vultisig.wallet.data.utils.Numeric
import timber.log.Timber
import wallet.core.jni.AnyAddress
import wallet.core.jni.CoinType
import wallet.core.jni.DataVector
import wallet.core.jni.PublicKey
import wallet.core.jni.PublicKeyType
import wallet.core.jni.TransactionCompiler

@OptIn(ExperimentalStdlibApi::class)
object CardanoHelper {


    fun getPreSignedInputData(keysignPayload: KeysignPayload): ByteArray {
        require(keysignPayload.coin.chain == Chain.Cardano) { "Coin is not ADP" }


        val chainSpecific = keysignPayload.blockChainSpecific

        require(keysignPayload.coin.chain == Chain.Cardano) { "Coin is not XRP" }

        keysignPayload.blockChainSpecific as? BlockChainSpecific.Cardano




        val (byteFee, sendMaxAmount, ttl) = keysignPayload.blockChainSpecific as? BlockChainSpecific.Cardano
            ?: error("fail to get Cardano chain specific parameters")


        val toAddress = AnyAddress(
            keysignPayload.toAddress,
            CoinType.CARDANO
        )
//            ?: error("fail to get to address: ${keysignPayload.toAddress}")


        // Prevent from accidentally sending all balance
        var safeGuardMaxAmount = false
//        val rawBalance = keysignPayload.coin.rawBalance.toLongOrNull()
//        if (rawBalance != null &&
//            sendMaxAmount &&
//            rawBalance > 0 &&
//            rawBalance == keysignPayload.toAmount.toLong()
//        ) {
//            safeGuardMaxAmount = true
//        }

        // For Cardano, we don't use UTXOs from Blockchair since it doesn't support Cardano
        // Instead, we create a simplified input structure
        val input = Cardano.SigningInput.newBuilder().apply {
            transferMessage = Cardano.Transfer.newBuilder().apply {
//                toAddress = keysignPayload.toAddress
                changeAddress = keysignPayload.coin.address
                amount = keysignPayload.toAmount.toLong()
                useMaxAmount = safeGuardMaxAmount
            }.build()
            this.ttl = ttl.toLong()

            // TODO: Implement memo support when WalletCore adds Cardano metadata support
        }.build()

        // Add UTXOs to the input
        for (inputUtxo in keysignPayload.utxos) {
            val utxo = Cardano.TxInput.newBuilder().apply {
                outPoint = Cardano.OutPoint.newBuilder().apply {
                    txHash =
                        com.google.protobuf.ByteString.copyFrom(inputUtxo.hash.hexToByteArray())
                    outputIndex = inputUtxo.index.toLong()
                }.build()
                amount = inputUtxo.amount.toLong()
                address = keysignPayload.coin.address
            }.build()
            input.utxosList.add(utxo)
        }

        return input.toByteArray()
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
        signatures: Map<String, tss.KeysignResponse>
    ): SignedTransactionResult {
        val extendedKeyData = createCardanoExtendedKey(
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
        val preSigningOutput = wallet.core.jni.proto.TransactionCompiler.PreSigningOutput.parseFrom(hashes)

        val allSignatures = DataVector()
        val publicKeys = DataVector()
//        val signatureProvider = SignatureProvider(signatures)
//        val signature = signatureProvider.getSignature(preSigningOutput.dataHash)
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
        return SignedTransactionResult(
            rawTransaction = output.encoded.toByteArray().toHexString(),
            transactionHash = output.txId.toByteArray().toHexString()
        )
    }



}