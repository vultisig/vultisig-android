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
import wallet.core.jni.Base64
import wallet.core.jni.CoinType
import wallet.core.jni.DataVector
import wallet.core.jni.PublicKey
import wallet.core.jni.PublicKeyType
import wallet.core.jni.TransactionCompiler
import wallet.core.jni.proto.Sui

object SuiHelper {

    private val coinType = CoinType.SUI
    private val suiContractAddress = "0x2::sui::SUI"

    private fun getPreSignedInputData(keysignPayload: KeysignPayload): ByteArray {
        require(keysignPayload.coin.chain == Chain.Sui) { "Coin is not SUI" }

        val (referenceGasPrice, gasBudget, coins) = keysignPayload.blockChainSpecific as? BlockChainSpecific.Sui
            ?: throw RuntimeException("getPreSignedInputData fail to get SUI transaction information from RPC")

        val toAddress = AnyAddress(keysignPayload.toAddress, coinType)

        val (nonSuiObjectRef, suiObjectRefs) = coins.partition { it.coinType.equals(keysignPayload.coin.contractAddress) && !it.coinType.equals(suiContractAddress) }
            .let { (notSui, sui) ->
                notSui.map {
                    Sui.ObjectRef.newBuilder()
                        .setObjectId(it.coinObjectId)
                        .setVersion(it.version.toLong())
                        .setObjectDigest(it.digest)
                        .build()
                } to sui.filter { it.coinType.equals(suiContractAddress) }.map {
                    Sui.ObjectRef.newBuilder()
                        .setObjectId(it.coinObjectId)
                        .setVersion(it.version.toLong())
                        .setObjectDigest(it.digest)
                        .build()
                }
            }
        val gascoin =
            coins.filter { it.coinType == suiContractAddress && it.balance.toBigInteger() > referenceGasPrice }
                .minByOrNull { it.balance.toBigInteger() }

        val input = if (nonSuiObjectRef.isNotEmpty()) {
            Sui.SigningInput.newBuilder()
                .setPay(
                    Sui.Pay.newBuilder()
                        .setGas(suiObjectRefs.first { it.objectId == gascoin?.coinObjectId })
                        .addAllInputCoins(nonSuiObjectRef)
                        .addAllRecipients(listOf(toAddress.description()))
                        .addAllAmounts(listOf(keysignPayload.toAmount.toLong()))
                        .build()
                )

        } else {
            Sui.SigningInput.newBuilder()
                .setPaySui(
                    Sui.PaySui.newBuilder()
                        .addAllInputCoins(suiObjectRefs)
                        .addAllRecipients(listOf(toAddress.description()))
                        .addAllAmounts(listOf(keysignPayload.toAmount.toLong()))
                        .build()
                )
        }.setSigner(keysignPayload.coin.address)
            .setGasBudget(gasBudget.toLong())
            .setReferenceGasPrice(referenceGasPrice.toLong())
            .build()

        return input.toByteArray()
    }

    private fun getPreSigningOutput(keysignPayload: KeysignPayload):
            wallet.core.jni.proto.TransactionCompiler.PreSigningOutput {
        val inputData = getPreSignedInputData(keysignPayload)
        Timber.d("input data: ${inputData.toHexString()}")

        val hashes = TransactionCompiler.preImageHashes(coinType, inputData)
        val preSigningOutput =
            wallet.core.jni.proto.TransactionCompiler.PreSigningOutput.parseFrom(hashes)
                .checkError()

        require(preSigningOutput.errorMessage.isEmpty()) { preSigningOutput.errorMessage }

        return preSigningOutput
    }

    fun getPreSignedImageHash(keysignPayload: KeysignPayload): List<String> {
        val preSigningOutput = getPreSigningOutput(keysignPayload)
        return listOf(preSigningOutput.dataHash.toByteArray().toHexString())
    }

    fun getZeroSignedTransaction(keysignPayload: KeysignPayload): String {
        val preSigningOutput = getPreSigningOutput(keysignPayload)

        // Drop 3 first bytes which represents signature, they're added by WalletCore
        // but for simulations or blockaid is not required
        val tx = preSigningOutput.data.toByteArray().drop(3).toByteArray()

        return Base64.encode(tx)
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
            .checkError()
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
            .checkError()
        return SignedTransactionResult(output.unsignedTx, "", output.signature)
    }

}

internal val DEFAULT_SUI_GAS_BUDGET = "3000000".toBigInteger()