package com.vultisig.wallet.chains

import com.vultisig.wallet.common.Numeric
import com.vultisig.wallet.common.toByteString
import com.vultisig.wallet.common.toKeccak256
import com.vultisig.wallet.models.SignedTransactionResult
import com.vultisig.wallet.presenter.keysign.BlockChainSpecific
import com.vultisig.wallet.presenter.keysign.KeysignPayload
import com.vultisig.wallet.tss.getSignatureWithRecoveryID
import wallet.core.jni.CoinType
import wallet.core.jni.DataVector
import wallet.core.jni.PublicKey
import wallet.core.jni.PublicKeyType
import wallet.core.jni.TransactionCompiler
import wallet.core.jni.proto.Ethereum

internal class ERC20Helper(
    private val coinType: CoinType,
    private val vaultHexPublicKey: String,
    private val vaultHexChainCode: String,
) {
    fun getPreSignedInputData(keysignPayload: KeysignPayload): ByteArray {
        val ethSpecific = keysignPayload.blockChainSpecific as? BlockChainSpecific.Ethereum
            ?: throw IllegalArgumentException("Invalid blockChainSpecific")
        val input = Ethereum.SigningInput.newBuilder()
            .setChainId(coinType.chainId().toByteString())
            .setNonce(ethSpecific.nonce.toString().toByteString())
            .setGasLimit(ethSpecific.gasLimit.toString().toByteString())
            .setMaxFeePerGas(ethSpecific.maxFeePerGasWei.toString().toByteString())
            .setMaxInclusionFeePerGas(ethSpecific.priorityFeeWei.toString().toByteString())
            .setToAddress(keysignPayload.coin.contractAddress)
            .setTxMode(Ethereum.TransactionMode.Enveloped)
            .setTransaction(
                Ethereum.Transaction.newBuilder()
                    .setErc20Transfer(
                        Ethereum.Transaction.ERC20Transfer.newBuilder()
                            .setAmount(keysignPayload.toAmount.toString().toByteString())
                            .setTo(keysignPayload.toAddress)
                            .build()
                    )
                    .build()
            )
            .build()
        return input.toByteArray()
    }

    fun getPreSignedImageHash(keysignPayload: KeysignPayload): List<String> {
        val result = getPreSignedInputData(keysignPayload)
        val hashes = TransactionCompiler.preImageHashes(coinType, result)
        val preSigningOutput =
            wallet.core.jni.proto.TransactionCompiler.PreSigningOutput.parseFrom(hashes)
        return listOf(Numeric.toHexStringNoPrefix(preSigningOutput.dataHash.toByteArray()))
    }

    @OptIn(ExperimentalStdlibApi::class)
    fun getSignedTransaction(
        keysignPayload: KeysignPayload,
        signatures: Map<String, tss.KeysignResponse>,
    ): SignedTransactionResult {
        val ethPublicKey = PublicKeyHelper.getDerivedPublicKey(
            vaultHexPublicKey,
            vaultHexChainCode,
            coinType.derivationPath()
        )
        val inputData = getPreSignedInputData(keysignPayload)
        val publicKey = PublicKey(ethPublicKey.hexToByteArray(), PublicKeyType.SECP256K1)
        val preHashes = TransactionCompiler.preImageHashes(coinType, inputData)
        val preSigningOutput =
            wallet.core.jni.proto.TransactionCompiler.PreSigningOutput.parseFrom(preHashes)
        val allSignatures = DataVector()
        val allPublicKeys = DataVector()
        val key = Numeric.toHexStringNoPrefix(preSigningOutput.dataHash.toByteArray())
        val signature = signatures[key]?.getSignatureWithRecoveryID()
            ?: throw Exception("Signature not found")
        if (!publicKey.verify(signature, preSigningOutput.dataHash.toByteArray())) {
            throw Exception("Signature verification failed")
        }
        allSignatures.add(signature)

        val compileWithSignature = TransactionCompiler.compileWithSignatures(
            coinType,
            inputData,
            allSignatures,
            allPublicKeys
        )
        val output = Ethereum.SigningOutput.parseFrom(compileWithSignature)
        return SignedTransactionResult(
            rawTransaction = Numeric.toHexStringNoPrefix(output.encoded.toByteArray()),
            transactionHash = output.encoded.toByteArray().toKeccak256()
        )
    }
}