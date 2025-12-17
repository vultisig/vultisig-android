package com.vultisig.wallet.data.chains.helpers

import com.google.protobuf.ByteString
import com.vultisig.wallet.data.common.toByteStringOrHex
import com.vultisig.wallet.data.common.toKeccak256
import com.vultisig.wallet.data.crypto.checkError
import com.vultisig.wallet.data.models.SignedTransactionResult
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.DeFiAction
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.tss.getSignatureWithRecoveryID
import com.vultisig.wallet.data.utils.Numeric
import wallet.core.jni.CoinType
import wallet.core.jni.DataVector
import wallet.core.jni.PublicKey
import wallet.core.jni.PublicKeyType
import wallet.core.jni.TransactionCompiler
import wallet.core.jni.proto.Ethereum
import java.math.BigInteger

class ERC20Helper(
    private val coinType: CoinType,
    private val vaultHexPublicKey: String,
    private val vaultHexChainCode: String,
) {
    private fun getPreSignedInputData(keysignPayload: KeysignPayload): ByteArray {
        val ethSpecific = keysignPayload.blockChainSpecific as? BlockChainSpecific.Ethereum
            ?: throw IllegalArgumentException("Invalid blockChainSpecific")

        val defiAction = keysignPayload.defiAction
        val input = when (defiAction) {
            DeFiAction.CIRCLE_USDC_WITHDRAW -> buildUsdcWithdraw(keysignPayload)
            DeFiAction.NONE -> buildErc20TokenTransfer(keysignPayload)
        }

        return EthereumGasHelper.setGasParameters(
            ethSpecific.gasLimit,
            ethSpecific.maxFeePerGasWei,
            input,
            keysignPayload,
            nonceIncrement = BigInteger.ZERO,
            coinType = coinType
        ).build().toByteArray()
    }

    private fun buildErc20TokenTransfer(keysignPayload: KeysignPayload): Ethereum.SigningInput.Builder =
        Ethereum.SigningInput.newBuilder()
            .setToAddress(keysignPayload.coin.contractAddress)
            .setTransaction(
                Ethereum.Transaction.newBuilder()
                    .setErc20Transfer(
                        Ethereum.Transaction.ERC20Transfer.newBuilder()
                            .setAmount(ByteString.copyFrom(keysignPayload.toAmount.toByteArray()))
                            .setTo(keysignPayload.toAddress)
                            .build()
                    )
                    .build()
            )

    private fun buildUsdcWithdraw(keysignPayload: KeysignPayload) : Ethereum.SigningInput.Builder {
        require(!keysignPayload.memo.isNullOrBlank()) {
            "Empty memo for usdc withdraw"
        }

        return Ethereum.SigningInput.newBuilder().apply {
            toAddress = keysignPayload.coin.contractAddress
            transaction = Ethereum.Transaction.newBuilder().apply {
                transfer = Ethereum.Transaction.Transfer.newBuilder().apply {
                    amount = ByteString.copyFrom(BigInteger.ZERO.toByteArray())
                    data = keysignPayload.memo.toByteStringOrHex()
                }.build()
            }.build()
        }
    }

    fun getPreSignedImageHash(keysignPayload: KeysignPayload): List<String> {
        val result = getPreSignedInputData(keysignPayload)
        val hashes = TransactionCompiler.preImageHashes(coinType, result)
        val preSigningOutput =
            wallet.core.jni.proto.TransactionCompiler.PreSigningOutput.parseFrom(hashes)
                .checkError()
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
                .checkError()
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
            .checkError()
        return SignedTransactionResult(
            rawTransaction = Numeric.toHexStringNoPrefix(output.encoded.toByteArray()),
            transactionHash = output.encoded.toByteArray().toKeccak256()
        )
    }
}