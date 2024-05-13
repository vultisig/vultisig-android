package com.vultisig.wallet.chains

import com.google.gson.Gson
import com.google.protobuf.ByteString
import com.vultisig.wallet.common.Numeric
import com.vultisig.wallet.models.Coin
import com.vultisig.wallet.models.Coins
import com.vultisig.wallet.models.CosmoSignature
import com.vultisig.wallet.models.SignedTransactionResult
import com.vultisig.wallet.models.transactionHash
import com.vultisig.wallet.presenter.keysign.BlockChainSpecific
import com.vultisig.wallet.presenter.keysign.KeysignPayload
import com.vultisig.wallet.tss.getSignatureWithRecoveryID
import wallet.core.jni.CoinType
import wallet.core.jni.DataVector
import wallet.core.jni.PublicKey
import wallet.core.jni.PublicKeyType
import wallet.core.jni.proto.Cosmos

@OptIn(ExperimentalStdlibApi::class)
internal class AtomHelper(
    private val vaultHexPublicKey: String,
    private val vaultHexChainCode: String,
) {
    val coinType = CoinType.COSMOS
    val ATOMGasLimit = 200000
    fun getCoin(): Coin? {
        val derivedPublicKey = PublicKeyHelper.getDerivedPublicKey(
            vaultHexPublicKey,
            vaultHexChainCode,
            coinType.derivationPath()
        )
        val publicKey = PublicKey(derivedPublicKey.hexToByteArray(), PublicKeyType.SECP256K1)
        val address = coinType.deriveAddressFromPublicKey(publicKey)
        return Coins.getCoin("ATOM", address, derivedPublicKey, coinType)
    }

    fun getSwapPreSignedInputData(
        keysignPayload: KeysignPayload,
        input: Cosmos.SigningInput.Builder,
    ): ByteArray {
        val atomData = keysignPayload.blockChainSpecific as? BlockChainSpecific.Cosmos
            ?: throw Exception("Invalid blockChainSpecific")

        val publicKey =
            PublicKey(keysignPayload.coin.hexPublicKey.hexToByteArray(), PublicKeyType.SECP256K1)
        val inputData = input
            .setPublicKey(ByteString.copyFrom(publicKey.data()))
            .setAccountNumber(atomData.accountNumber.toLong())
            .setSequence(atomData.sequence.toLong())
            .setMode(Cosmos.BroadcastMode.SYNC)
            .setFee(
                Cosmos.Fee.newBuilder()
                    .setGas(ATOMGasLimit.toLong())
                    .addAllAmounts(
                        listOf(
                            Cosmos.Amount.newBuilder()
                                .setDenom("uatom")
                                .setAmount(atomData.gas.toString())
                                .build()
                        )
                    )

            ).build()
        return inputData.toByteArray()
    }

    fun getPreSignedInputData(keysignPayload: KeysignPayload): ByteArray {
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
                                            .setDenom("uatom")
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
                    .setGas(ATOMGasLimit.toLong())
                    .addAllAmounts(
                        listOf(
                            Cosmos.Amount.newBuilder()
                                .setDenom("uatom")
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