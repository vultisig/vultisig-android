package com.vultisig.wallet.chains

import com.google.gson.Gson
import com.google.protobuf.ByteString
import com.vultisig.wallet.data.models.CosmoSignature
import com.vultisig.wallet.data.models.SignedTransactionResult
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.transactionHash
import com.vultisig.wallet.data.utils.Numeric
import com.vultisig.wallet.presenter.keysign.KeysignPayload
import com.vultisig.wallet.tss.getSignatureWithRecoveryID
import wallet.core.jni.CoinType
import wallet.core.jni.DataVector
import wallet.core.jni.PublicKey
import wallet.core.jni.PublicKeyType
import wallet.core.jni.proto.Cosmos

@OptIn(ExperimentalStdlibApi::class)
internal class KujiraHelper(
    private val vaultHexPublicKey: String,
    private val vaultHexChainCode: String,
) {
    val coinType = CoinType.KUJIRA
    companion object {
        const val KUJI_GAS_LIMIT = 200000
    }

    private fun getPreSignedInputData(keysignPayload: KeysignPayload): ByteArray {
        val atomData = keysignPayload.blockChainSpecific as? BlockChainSpecific.Cosmos
            ?: throw Exception("Invalid blockChainSpecific")
        val publicKey =
            PublicKey(keysignPayload.coin.hexPublicKey.hexToByteArray(), PublicKeyType.SECP256K1)
        var input = Cosmos.SigningInput.newBuilder()
            .setPublicKey(ByteString.copyFrom(publicKey.data()))
            .setSigningMode(Cosmos.SigningMode.Protobuf)
            .setChainId(coinType.chainId())
            .setAccountNumber(atomData.accountNumber.toLong())
            .setSequence(atomData.sequence.toLong())
            .setMode(Cosmos.BroadcastMode.SYNC)
            .addAllMessages(
                listOf(
                    Cosmos.Message.newBuilder()
                        .setSendCoinsMessage(
                            Cosmos.Message.Send.newBuilder()
                                .setFromAddress(keysignPayload.coin.address)
                                .setToAddress(keysignPayload.toAddress)
                                .addAllAmounts(
                                    listOf(
                                        Cosmos.Amount.newBuilder()
                                            .setDenom("ukuji")
                                            .setAmount(keysignPayload.toAmount.toString())
                                            .build()
                                    )
                                )
                                .build()
                        )
                        .build()
                )
            )
            .setFee(
                Cosmos.Fee.newBuilder()
                    .setGas(KUJI_GAS_LIMIT.toLong())
                    .addAllAmounts(
                        listOf(
                            Cosmos.Amount.newBuilder()
                                .setDenom("ukuji")
                                .setAmount(atomData.gas.toString())
                                .build()
                        )
                    )
            )
        keysignPayload.memo?.let {
            input = input.setMemo(it)
        }

        return input.build().toByteArray()
    }

    fun getPreSignedImageHash(keysignPayload: KeysignPayload): List<String> {
        val result = getPreSignedInputData(keysignPayload)
        val hashes = wallet.core.jni.TransactionCompiler.preImageHashes(coinType, result)
        val preSigningOutput =
            wallet.core.jni.proto.TransactionCompiler.PreSigningOutput.parseFrom(hashes)
        if (!preSigningOutput.errorMessage.isNullOrEmpty()) {
            throw Exception(preSigningOutput.errorMessage)
        }
        return listOf(Numeric.toHexStringNoPrefix(preSigningOutput.dataHash.toByteArray()))
    }

    fun getSignedTransaction(
        keysignPayload: KeysignPayload,
        signatures: Map<String, tss.KeysignResponse>,
    ): SignedTransactionResult {
        val publicKey =
            PublicKey(keysignPayload.coin.hexPublicKey.hexToByteArray(), PublicKeyType.SECP256K1)
        val input = getPreSignedInputData(keysignPayload)
        val hashes = wallet.core.jni.TransactionCompiler.preImageHashes(coinType, input)
        val preSigningOutput =
            wallet.core.jni.proto.TransactionCompiler.PreSigningOutput.parseFrom(hashes)
        val key = Numeric.toHexStringNoPrefix(preSigningOutput.dataHash.toByteArray())
        val signature = signatures[key]?.getSignatureWithRecoveryID()
            ?: throw Exception("Invalid signature")
        if (!publicKey.verify(signature, preSigningOutput.dataHash.toByteArray())) {
            throw Exception("Invalid signature")
        }
        val allSignatures = DataVector()
        val allPublicKeys = DataVector()
        allSignatures.add(signature)
        allPublicKeys.add(publicKey.data())
        val compileWithSignature = wallet.core.jni.TransactionCompiler.compileWithSignatures(
            coinType,
            input,
            allSignatures,
            allPublicKeys
        )
        val output = Cosmos.SigningOutput.parseFrom(compileWithSignature)
        val cosmosSig = Gson().fromJson(output.serialized, CosmoSignature::class.java)
        return SignedTransactionResult(
            output.serialized,
            cosmosSig.transactionHash(),
        )
    }
}