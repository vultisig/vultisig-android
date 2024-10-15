@file:OptIn(ExperimentalStdlibApi::class)

package com.vultisig.wallet.data.crypto

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.SignedTransactionResult
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.tss.getSignature
import timber.log.Timber
import tss.KeysignResponse
import wallet.core.jni.AnyAddress
import wallet.core.jni.CoinType
import wallet.core.jni.DataVector
import wallet.core.jni.PublicKey
import wallet.core.jni.PublicKeyType
import wallet.core.jni.TransactionCompiler
import wallet.core.jni.proto.Sui

object SuiHelper {

    private val coinType = CoinType.SUI

    private fun getPreSignedInputData(keysignPayload: KeysignPayload): ByteArray {
        require(keysignPayload.coin.chain == Chain.Sui) { "Coin is not SUI" }

        val (referenceGasPrice, coins) = keysignPayload.blockChainSpecific as? BlockChainSpecific.Sui
            ?: throw RuntimeException("getPreSignedInputData fail to get SUI transaction information from RPC")

        val toAddress = AnyAddress(keysignPayload.toAddress, coinType)

        val suiCoins = coins.map { coin ->
            Sui.ObjectRef.newBuilder()
                .setObjectId(coin.coinObjectId)
                .setVersion(coin.version.toLong())
                .setObjectDigest(coin.digest)
                .build()
        }

        val input = Sui.SigningInput.newBuilder()
            .setPaySui(
                Sui.PaySui.newBuilder()
                    .addAllInputCoins(suiCoins)
                    .addAllRecipients(listOf(toAddress.description()))
                    .addAllAmounts(listOf(keysignPayload.toAmount.toLong()))
                    .build()
            )
            .setSigner(keysignPayload.coin.address)
            .setGasBudget(3000000L)
            .setReferenceGasPrice(referenceGasPrice.toLong())
            .build()

        return input.toByteArray()
    }

    fun getPreSignedImageHash(keysignPayload: KeysignPayload): List<String> {
        val inputData = getPreSignedInputData(keysignPayload)
        Timber.d("input data: ${inputData.toHexString()}")

        val hashes = TransactionCompiler.preImageHashes(coinType, inputData)
        val preSigningOutput =
            wallet.core.jni.proto.TransactionCompiler.PreSigningOutput.parseFrom(hashes)

        require(preSigningOutput.errorMessage.isEmpty()) { preSigningOutput.errorMessage }

        return listOf(preSigningOutput.dataHash.toByteArray().toHexString())
    }

    fun getSignedTransaction(
        vaultHexPubKey: String,
        keysignPayload: KeysignPayload,
        signatures: Map<String, KeysignResponse>
    ): SignedTransactionResult {
        val pubkeyData = vaultHexPubKey.hexToByteArray()
        val publicKey = PublicKey(pubkeyData, PublicKeyType.ED25519)

        val inputData = getPreSignedInputData(keysignPayload)
        val hashes = TransactionCompiler.preImageHashes(coinType, inputData)
        val preSigningOutput = wallet.core.jni.proto.TransactionCompiler.PreSigningOutput
            .parseFrom(hashes)
        val preSigningOutputDataBlake2b =preSigningOutput.dataHash.toByteArray()

        val key = preSigningOutputDataBlake2b.toHexString()

        val allSignatures = DataVector()
        val publicKeys = DataVector()

        val signature = signatures[key]?.getSignature()
            ?: throw Exception("Signature not found")

        if (!publicKey.verify(signature, preSigningOutputDataBlake2b)) {
            throw Exception("Signature verification failed")
        }

        allSignatures.add(signature)
        publicKeys.add(pubkeyData)
        val compileWithSignature = TransactionCompiler.compileWithSignatures(
            coinType,
            inputData,
            allSignatures,
            publicKeys
        )
        val output = Sui.SigningOutput.parseFrom(compileWithSignature)
        return SignedTransactionResult(output.unsignedTx, "", output.signature)
    }

}