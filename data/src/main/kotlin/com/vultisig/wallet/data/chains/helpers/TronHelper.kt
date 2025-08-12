@file:OptIn(ExperimentalStdlibApi::class)

package com.vultisig.wallet.data.chains.helpers

import com.google.protobuf.ByteString
import com.vultisig.wallet.data.crypto.checkError
import com.vultisig.wallet.data.models.SignedTransactionResult
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.tss.getSignatureWithRecoveryID
import com.vultisig.wallet.data.utils.Numeric
import wallet.core.jni.CoinType
import wallet.core.jni.DataVector
import wallet.core.jni.PublicKey
import wallet.core.jni.PublicKeyType
import wallet.core.jni.TransactionCompiler
import wallet.core.jni.proto.Tron
import wallet.core.jni.proto.Tron.BlockHeader
import wallet.core.jni.proto.Tron.TransferTRC20Contract

class TronHelper(
    private val coinType: CoinType,
    private val vaultHexPublicKey: String,
    private val vaultHexChainCode: String,
) {

    private fun getPreSignedInputData(keysignPayload: KeysignPayload): ByteArray {
        val tronSpecific = keysignPayload.blockChainSpecific as? BlockChainSpecific.Tron
            ?: throw IllegalArgumentException("Invalid blockChainSpecific")
        if (keysignPayload.coin.isNativeToken) {
            val contract = Tron.TransferContract
                .newBuilder()
                .setOwnerAddress(keysignPayload.coin.address)
                .setToAddress(keysignPayload.toAddress)
                .setAmount(keysignPayload.toAmount.toLong())
                .build()
            val txBuild = Tron.Transaction.newBuilder()
                .setTransfer(contract)
                .setTimestamp(tronSpecific.timestamp.toLong())
                .setBlockHeader(
                    BlockHeader.newBuilder()
                        .setTimestamp(tronSpecific.blockHeaderTimestamp.toLong())
                        .setNumber(tronSpecific.blockHeaderNumber.toLong())
                        .setVersion(tronSpecific.blockHeaderVersion.toInt())
                        .setTxTrieRoot(
                            ByteString.copyFrom(
                                Numeric.hexStringToByteArray(
                                    tronSpecific.blockHeaderTxTrieRoot
                                ),
                            )
                        )
                        .setParentHash(
                            ByteString.copyFrom(
                                Numeric.hexStringToByteArray(
                                    tronSpecific.blockHeaderParentHash
                                )
                            )
                        )
                        .setWitnessAddress(
                            ByteString.copyFrom(
                                Numeric.hexStringToByteArray(
                                    tronSpecific.blockHeaderWitnessAddress
                                )
                            )
                        )
                        .build()
                )
                .setExpiration(tronSpecific.expiration.toLong())
            keysignPayload.memo?.let { memo ->
                txBuild.setMemo(memo)
            }
            val input = Tron.SigningInput.newBuilder()
                .setTransaction(txBuild.build())
                .build()
            return input.toByteArray()
        } else {
            val contract = TransferTRC20Contract.newBuilder()
                .setToAddress(keysignPayload.toAddress)
                .setContractAddress(keysignPayload.coin.contractAddress)
                .setOwnerAddress(keysignPayload.coin.address)
                .setAmount(ByteString.copyFrom(keysignPayload.toAmount.toByteArray()))
                .build()
            val txBuild = Tron.Transaction.newBuilder()
                .setFeeLimit(tronSpecific.gasFeeEstimation.toLong())
                .setTransferTrc20Contract(contract)
                .setTimestamp(tronSpecific.timestamp.toLong())
                .setBlockHeader(
                    BlockHeader.newBuilder()
                        .setTimestamp(tronSpecific.blockHeaderTimestamp.toLong())
                        .setNumber(tronSpecific.blockHeaderNumber.toLong())
                        .setVersion(tronSpecific.blockHeaderVersion.toInt())
                        .setTxTrieRoot(
                            ByteString.copyFrom(
                                Numeric.hexStringToByteArray(
                                    tronSpecific.blockHeaderTxTrieRoot
                                )
                            )
                        )
                        .setParentHash(
                            ByteString.copyFrom(
                                Numeric.hexStringToByteArray(
                                    tronSpecific.blockHeaderParentHash
                                )
                            )
                        )
                        .setWitnessAddress(
                            ByteString.copyFrom(
                                Numeric.hexStringToByteArray(
                                    tronSpecific.blockHeaderWitnessAddress
                                )
                            )
                        )
                        .build()
                )
                .setExpiration(tronSpecific.expiration.toLong())
            keysignPayload.memo?.let { memo ->
                txBuild.setMemo(memo)
            }
            val input = Tron.SigningInput.newBuilder()
                .setTransaction(txBuild.build())
                .build()
            return input.toByteArray()
        }
    }

    fun getPreSignedImageHash(keysignPayload: KeysignPayload): List<String> {
        val result = getPreSignedInputData(keysignPayload)
        val hashes = TransactionCompiler.preImageHashes(coinType, result)
        val preSigningOutput =
            wallet.core.jni.proto.TransactionCompiler.PreSigningOutput.parseFrom(hashes).checkError()
        return listOf(Numeric.toHexStringNoPrefix(preSigningOutput.dataHash.toByteArray()))
    }

    fun getSignedTransaction(
        keysignPayload: KeysignPayload,
        signatures: Map<String, tss.KeysignResponse>,
    ): SignedTransactionResult {
        val tronPublicKey = PublicKeyHelper.getDerivedPublicKey(
            vaultHexPublicKey,
            vaultHexChainCode,
            coinType.derivationPath()
        )
        val inputData = getPreSignedInputData(keysignPayload)
        val publicKey =
            PublicKey(tronPublicKey.hexToByteArray(), PublicKeyType.SECP256K1).uncompressed()
        val preHashes = TransactionCompiler.preImageHashes(coinType, inputData)
        val preSigningOutput =
            wallet.core.jni.proto.TransactionCompiler.PreSigningOutput.parseFrom(preHashes).checkError()
        val allSignatures = DataVector()
        val allPublicKeys = DataVector()
        val key = Numeric.toHexStringNoPrefix(preSigningOutput.dataHash.toByteArray())

        val signature = signatures[key]?.getSignatureWithRecoveryID()
            ?: error("Signature not found")
        if (!publicKey.verify(signature, preSigningOutput.dataHash.toByteArray())) {
            error("Signature verification failed")
        }
        allSignatures.add(signature)
        allPublicKeys.add(publicKey.data())

        val compileWithSignature = TransactionCompiler.compileWithSignatures(
            coinType,
            inputData,
            allSignatures,
            allPublicKeys
        )
        val output = Tron.SigningOutput.parseFrom(compileWithSignature).checkError()

        return SignedTransactionResult(
            rawTransaction = output.json,
            transactionHash = Numeric.toHexStringNoPrefix(output.id.toByteArray())
        )
    }

    companion object {
        internal const val TRON_DEFAULT_ESTIMATION_FEE = 800_000L
    }
}