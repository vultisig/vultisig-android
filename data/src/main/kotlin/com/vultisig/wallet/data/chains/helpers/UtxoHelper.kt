package com.vultisig.wallet.data.chains.helpers

import com.google.protobuf.ByteString
import com.vultisig.wallet.data.models.SignedTransactionResult
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.utils.Numeric
import timber.log.Timber
import tss.KeysignResponse
import wallet.core.java.AnySigner
import wallet.core.jni.BitcoinScript
import wallet.core.jni.CoinType
import wallet.core.jni.DataVector
import wallet.core.jni.PublicKey
import wallet.core.jni.PublicKeyType
import wallet.core.jni.TransactionCompiler
import wallet.core.jni.proto.Bitcoin

class UtxoHelper(
    val coinType: CoinType,
    val vaultHexPublicKey: String,
    val vaultHexChainCode: String,
) {
    companion object {
        fun getHelper(vault: Vault, coinType: CoinType): UtxoHelper {
            when (coinType) {
                CoinType.BITCOIN, CoinType.BITCOINCASH, CoinType.LITECOIN, CoinType.DOGECOIN, CoinType.DASH -> {
                    return UtxoHelper(
                        coinType = coinType,
                        vaultHexPublicKey = vault.pubKeyECDSA,
                        vaultHexChainCode = vault.hexChainCode
                    )
                }

                else -> throw Exception("Unsupported chain")
            }
        }
    }

    fun getPreSignedImageHash(keysignPayload: KeysignPayload): List<String> {
        val inputData = getBitcoinPreSigningInputData(keysignPayload)
        val preHashes = TransactionCompiler.preImageHashes(coinType, inputData)
        val preSigningOutput = Bitcoin.PreSigningOutput.parseFrom(preHashes)
        if (!preSigningOutput.errorMessage.isNullOrEmpty()) {
            throw Exception(preSigningOutput.errorMessage)
        }
        return preSigningOutput.hashPublicKeysList.map { Numeric.toHexStringNoPrefix(it.dataHash.toByteArray()) }
            .sorted()
    }

    @OptIn(ExperimentalStdlibApi::class)
    fun getSigningInputData(
        keysignPayload: KeysignPayload,
        signingInput: Bitcoin.SigningInput.Builder,
    ): ByteArray {
        val utxo = keysignPayload.blockChainSpecific as BlockChainSpecific.UTXO
        signingInput
            .setHashType(BitcoinScript.hashTypeForCoin(coinType))
            .setUseMaxAmount(utxo.sendMaxAmount)
            .setByteFee(utxo.byteFee.toLong())
        for (item in keysignPayload.utxos) {
            val lockScript =
                BitcoinScript.lockScriptForAddress(keysignPayload.coin.address, coinType)
            val output = Bitcoin.OutPoint.newBuilder()
                .setHash(ByteString.copyFrom(Numeric.hexStringToByteArray(item.hash).reversedArray()))
                .setIndex(item.index.toInt())
                .setSequence(Long.MAX_VALUE.toInt())
                .build()
            val utxoItem = Bitcoin.UnspentTransaction.newBuilder()
                .setAmount(item.amount)
                .setOutPoint(output)
                .setScript(ByteString.copyFrom(lockScript.data()))

            when (coinType) {
                CoinType.BITCOIN, CoinType.LITECOIN -> {
                    val keyHash = lockScript.matchPayToWitnessPublicKeyHash()
                    val redeemScript = BitcoinScript.buildPayToWitnessPubkeyHash(keyHash)
                    signingInput.putScripts(
                        keyHash.toHexString(),
                        ByteString.copyFrom(redeemScript.data())
                    )
                }

                CoinType.DOGECOIN, CoinType.BITCOINCASH, CoinType.DASH -> {
                    val keyHash = lockScript.matchPayToPubkeyHash()
                    val redeemScript = BitcoinScript.buildPayToPublicKeyHash(keyHash)
                    signingInput.putScripts(
                        keyHash.toHexString(),
                        ByteString.copyFrom(redeemScript.data())
                    )
                }

                else -> throw Exception("Unsupported coin")
            }
            signingInput.addUtxo(utxoItem.build())
        }

        val plan: Bitcoin.TransactionPlan =
            AnySigner.plan(signingInput.build(), coinType, Bitcoin.TransactionPlan.parser())
        signingInput.setPlan(plan)
        return signingInput.build().toByteArray()
    }


    @OptIn(ExperimentalStdlibApi::class)
    fun getBitcoinSigningInput(keysignPayload: KeysignPayload): Bitcoin.SigningInput.Builder {
        val utxo = keysignPayload.blockChainSpecific as BlockChainSpecific.UTXO
        val input = Bitcoin.SigningInput.newBuilder()
            .setHashType(BitcoinScript.hashTypeForCoin(coinType))
            .setAmount(keysignPayload.toAmount.toLong())
            .setUseMaxAmount(utxo.sendMaxAmount)
            .setToAddress(keysignPayload.toAddress)
            .setChangeAddress(keysignPayload.coin.address)
            .setByteFee(utxo.byteFee.toLong())
            .setCoinType(coinType.value())
        keysignPayload.memo?.let {
            input.setOutputOpReturn(ByteString.copyFromUtf8(it))
        }

        for (item in keysignPayload.utxos) {
            val lockScript =
                BitcoinScript.lockScriptForAddress(keysignPayload.coin.address, coinType)
            val output = Bitcoin.OutPoint.newBuilder()
                .setHash(ByteString.copyFrom(Numeric.hexStringToByteArray(item.hash).reversedArray()))
                .setIndex(item.index.toInt())
                .setSequence(Long.MAX_VALUE.toInt())
                .build()
            val utxoItem = Bitcoin.UnspentTransaction.newBuilder()
                .setAmount(item.amount)
                .setOutPoint(output)
                .setScript(ByteString.copyFrom(lockScript.data()))

            when (coinType) {
                CoinType.BITCOIN, CoinType.LITECOIN -> {
                    val keyHash = lockScript.matchPayToWitnessPublicKeyHash()
                    val redeemScript = BitcoinScript.buildPayToWitnessPubkeyHash(keyHash)
                    input.putScripts(
                        keyHash.toHexString(),
                        ByteString.copyFrom(redeemScript.data())
                    )
                }

                CoinType.DOGECOIN, CoinType.BITCOINCASH, CoinType.DASH -> {
                    val keyHash = lockScript.matchPayToPubkeyHash()
                    val redeemScript = BitcoinScript.buildPayToPublicKeyHash(keyHash)
                    input.putScripts(
                        keyHash.toHexString(),
                        ByteString.copyFrom(redeemScript.data())
                    )
                }

                else -> throw Exception("Unsupported coin")
            }
            input.addUtxo(utxoItem.build())
        }
        return input
    }

    private fun getBitcoinPreSigningInputData(keysignPayload: KeysignPayload): ByteArray {
        val signingInput = getBitcoinSigningInput(keysignPayload)
        val plan: Bitcoin.TransactionPlan =
            AnySigner.plan(signingInput.build(), coinType, Bitcoin.TransactionPlan.parser())
        signingInput.setPlan(plan)
        return signingInput.build().toByteArray()
    }

    fun getSignedTransaction(
        keysignPayload: KeysignPayload,
        signatures: Map<String, KeysignResponse>,
    ): SignedTransactionResult {
        val inputData = getBitcoinPreSigningInputData(keysignPayload)
        return getSignedTransaction(inputData, signatures)
    }

    @OptIn(ExperimentalStdlibApi::class)
    fun getSignedTransaction(
        inputData: ByteArray,
        signatures: Map<String, KeysignResponse>,
    ): SignedTransactionResult {
        val derivedPublicKey = PublicKeyHelper.getDerivedPublicKey(
            vaultHexPublicKey,
            vaultHexChainCode,
            coinType.derivationPath()
        )
        val publicKey = PublicKey(derivedPublicKey.hexToByteArray(), PublicKeyType.SECP256K1)
        val preHashes = TransactionCompiler.preImageHashes(coinType, inputData)
        val preSigningOutput = Bitcoin.PreSigningOutput.parseFrom(preHashes)
        val publicKeys = DataVector()
        val allSignatures = DataVector()
        for (item in preSigningOutput.hashPublicKeysList) {
            val preImageHash = item.dataHash
            val key = Numeric.toHexStringNoPrefix(preImageHash.toByteArray())
            signatures[key]?.let {
                if (!publicKey.verifyAsDER(
                        it.derSignature.hexToByteArray(),
                        preImageHash.toByteArray()
                    )
                ) {
                    Timber.d("Invalid signature")
                    throw Exception("Invalid signature")
                }
                allSignatures.add(it.derSignature.hexToByteArray())
                publicKeys.add(publicKey.data())
            }
        }

        val compiledWithSignature = TransactionCompiler.compileWithSignatures(
            coinType,
            inputData,
            allSignatures,
            publicKeys
        )
        val output = Bitcoin.SigningOutput.parseFrom(compiledWithSignature)
        return SignedTransactionResult(
            rawTransaction = Numeric.toHexStringNoPrefix(
                output.encoded.toByteArray()
            ),
            transactionHash = output.transactionId
        )
    }

    fun getBitcoinTransactionPlan(keysignPayload: KeysignPayload): Bitcoin.TransactionPlan {
        val signingInput = getBitcoinSigningInput(keysignPayload)
        val plan: Bitcoin.TransactionPlan =
            AnySigner.plan(signingInput.build(), coinType, Bitcoin.TransactionPlan.parser())
        return plan
    }
}