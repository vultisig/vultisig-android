package com.vultisig.wallet.chains

import android.util.Log
import com.google.protobuf.ByteString
import com.vultisig.wallet.common.Numeric
import com.vultisig.wallet.models.Chain
import com.vultisig.wallet.models.Coin
import com.vultisig.wallet.models.Coins
import com.vultisig.wallet.models.SignedTransactionResult
import com.vultisig.wallet.models.Vault
import com.vultisig.wallet.presenter.keysign.BlockChainSpecific
import com.vultisig.wallet.presenter.keysign.KeysignPayload
import wallet.core.java.AnySigner
import wallet.core.jni.BitcoinScript
import wallet.core.jni.CoinType
import wallet.core.jni.DataVector
import wallet.core.jni.PublicKey
import wallet.core.jni.PublicKeyType
import wallet.core.jni.TransactionCompiler
import wallet.core.jni.proto.Bitcoin

class utxoHelper(
    val coinType: CoinType,
    val vaultHexPublicKey: String,
    val vaultHexChainCode: String,
) {
    companion object {
        fun getHelper(vault: Vault, coin: Coin): utxoHelper {
            when (coin.chain) {
                Chain.bitcoin, Chain.bitcoinCash, Chain.litecoin, Chain.dogecoin, Chain.dash -> {
                    return utxoHelper(
                        coinType = CoinType.valueOf(coin.chain.name.uppercase()),
                        vaultHexPublicKey = vault.pubKeyECDSA,
                        vaultHexChainCode = vault.hexChainCode
                    )
                }

                else -> throw Exception("Unsupported chain")
            }
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    fun getCoin(): Coin? {
        val ticker = when (coinType) {
            CoinType.BITCOIN -> "BTC"
            CoinType.BITCOINCASH -> "BCH"
            CoinType.LITECOIN -> "LTC"
            CoinType.DOGECOIN -> "DOGE"
            CoinType.DASH -> "DASH"
            else -> throw Exception("Unsupported coin")
        }
        val derivedPublicKey = PublicKeyHelper.getDerivedPublicKey(
            vaultHexPublicKey,
            vaultHexChainCode,
            coinType.derivationPath()
        )
        val publicKey = PublicKey(derivedPublicKey.hexToByteArray(), PublicKeyType.SECP256K1)
        val address = coinType.deriveAddressFromPublicKey(publicKey)
        return Coins.getCoin(ticker, address, derivedPublicKey, coinType)
    }

    fun getPreSignedImageHash(keysignPayload: KeysignPayload): List<String> {
        val inputData = getBitcoinPreSigningInputData(keysignPayload)
        val preHashes = TransactionCompiler.preImageHashes(coinType, inputData)
        val preSigningOutput = Bitcoin.PreSigningOutput.parseFrom(preHashes)
        return preSigningOutput.hashPublicKeysList.map { Numeric.toHexString(it.dataHash.toByteArray()) }
    }

    @OptIn(ExperimentalStdlibApi::class)
    fun getSigningInputData(
        keysignPayload: KeysignPayload,
        signingInput: Bitcoin.SigningInput.Builder,
    ): ByteArray {
        val utxo = keysignPayload.blockChainSpecific as BlockChainSpecific.UTXO
        signingInput.setByteFee(utxo.byteFee.toLong())
            .setHashType(BitcoinScript.hashTypeForCoin(coinType))
            .setUseMaxAmount(false)
            .setByteFee(utxo.byteFee.toLong())
        for (item in keysignPayload.utxos) {
            val lockScript =
                BitcoinScript.lockScriptForAddress(keysignPayload.coin.address, coinType)
            val output = Bitcoin.OutPoint.newBuilder()
                .setHash(ByteString.copyFrom(Numeric.hexStringToByteArray(item.hash)))
                .setIndex(item.index.toInt())
                .setSequence(Long.MAX_VALUE.toInt())
                .build()
            val utxoItem = Bitcoin.UnspentTransaction.newBuilder()
                .setAmount(item.amount.toLong())
                .setOutPoint(output)
                .setScript(ByteString.copyFrom(lockScript.data()))

            when (coinType) {
                CoinType.BITCOIN, CoinType.LITECOIN -> {
                    val keyHash = lockScript.matchPayToWitnessPublicKeyHash()
                    val redeemScript = BitcoinScript.buildPayToWitnessPubkeyHash(keyHash)
                    utxoItem.setSpendingScript(ByteString.copyFrom(redeemScript.data()))
                    signingInput.putScripts(
                        keyHash.toHexString(),
                        ByteString.copyFrom(redeemScript.data())
                    )
                }

                CoinType.DOGECOIN, CoinType.BITCOINCASH, CoinType.DASH -> {
                    val keyHash = lockScript.matchPayToPubkeyHash()
                    val redeemScript = BitcoinScript.buildPayToPublicKeyHash(keyHash)
                    utxoItem.setSpendingScript(ByteString.copyFrom(redeemScript.data()))
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
            .setByteFee(utxo.byteFee.toLong())
            .setHashType(BitcoinScript.hashTypeForCoin(coinType))
            .setAmount(keysignPayload.toAmount.toLong())
            .setUseMaxAmount(false)
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
                .setHash(ByteString.copyFrom(Numeric.hexStringToByteArray(item.hash)))
                .setIndex(item.index.toInt())
                .setSequence(Long.MAX_VALUE.toInt())
                .build()
            val utxoItem = Bitcoin.UnspentTransaction.newBuilder()
                .setAmount(item.amount.toLong())
                .setOutPoint(output)
                .setScript(ByteString.copyFrom(lockScript.data()))

            when (coinType) {
                CoinType.BITCOIN, CoinType.LITECOIN -> {
                    val keyHash = lockScript.matchPayToWitnessPublicKeyHash()
                    val redeemScript = BitcoinScript.buildPayToWitnessPubkeyHash(keyHash)
                    utxoItem.setSpendingScript(ByteString.copyFrom(redeemScript.data()))
                    input.putScripts(
                        keyHash.toHexString(),
                        ByteString.copyFrom(redeemScript.data())
                    )
                }

                CoinType.DOGECOIN, CoinType.BITCOINCASH, CoinType.DASH -> {
                    val keyHash = lockScript.matchPayToPubkeyHash()
                    val redeemScript = BitcoinScript.buildPayToPublicKeyHash(keyHash)
                    utxoItem.setSpendingScript(ByteString.copyFrom(redeemScript.data()))
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

    fun getBitcoinPreSigningInputData(keysignPayload: KeysignPayload): ByteArray {
        val signingInput = getBitcoinSigningInput(keysignPayload)
        val plan: Bitcoin.TransactionPlan =
            AnySigner.plan(signingInput.build(), coinType, Bitcoin.TransactionPlan.parser())
        signingInput.setPlan(plan)
        return signingInput.build().toByteArray()
    }

    fun getBitcoinTransactionPlan(keysignPayload: KeysignPayload): Bitcoin.TransactionPlan {
        val signingInput = getBitcoinSigningInput(keysignPayload)
        return AnySigner.plan(signingInput.build(), coinType, Bitcoin.TransactionPlan.parser())
    }

    @OptIn(ExperimentalStdlibApi::class)
    fun getSignedTransaction(
        inputData: ByteArray,
        signatures: Map<String, tss.KeysignResponse>,
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
            val key = Numeric.toHexString(preImageHash.toByteArray())
            signatures[key]?.let {
                if (!publicKey.verifyAsDER(
                        it.derSignature.hexToByteArray(),
                        preImageHash.toByteArray()
                    )
                ) {
                    Log.d("utxoHelper", "Invalid signature")
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
            rawTransaction = Numeric.toHexString(
                output.encoded.toByteArray()
            ),
            transactionHash = output.transactionId
        )
    }
}