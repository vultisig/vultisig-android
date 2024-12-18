package com.vultisig.wallet.data.chains.helpers

import androidx.collection.emptyLongSet
import com.google.protobuf.ByteString
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.CosmoSignature
import com.vultisig.wallet.data.models.SignedTransactionResult
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.models.transactionHash
import com.vultisig.wallet.data.tss.getSignatureWithRecoveryID
import com.vultisig.wallet.data.utils.Numeric
import kotlinx.serialization.json.Json
import tss.KeysignResponse
import wallet.core.jni.CoinType
import wallet.core.jni.DataVector
import wallet.core.jni.PublicKey
import wallet.core.jni.PublicKeyType
import wallet.core.jni.TransactionCompiler.compileWithSignatures
import wallet.core.jni.TransactionCompiler.preImageHashes
import wallet.core.jni.proto.Cosmos
import wallet.core.jni.proto.TransactionCompiler

@OptIn(ExperimentalStdlibApi::class)
class CosmosHelper(
    private val coinType: CoinType,
    private val denom: String,
    private val gasLimit: Long = DEFAULT_GAS_LIMIT,
) {

    companion object {
        private const val DEFAULT_GAS_LIMIT = 200000L

        fun getChainGasLimit(chain: Chain): Long = when (chain) {
            Chain.Terra, Chain.TerraClassic -> 300000L
            else -> DEFAULT_GAS_LIMIT
        }

        const val ATOM_DENOM = "uatom"
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
                    .setGas(gasLimit)
                    .addAllAmounts(
                        listOf(
                            Cosmos.Amount.newBuilder()
                                .setDenom(denom)
                                .setAmount(atomData.gas.toString())
                                .build()
                        )
                    )

            ).build()
        return inputData.toByteArray()
    }

    fun getPreSignedImageHash(keysignPayload: KeysignPayload): List<String> {
        val result = getPreSignedInputData(keysignPayload)
        val hashes = preImageHashes(coinType, result)
        val preSigningOutput =
            TransactionCompiler.PreSigningOutput.parseFrom(hashes)
        if (!preSigningOutput.errorMessage.isNullOrEmpty()) {
            throw Exception(preSigningOutput.errorMessage)
        }
        return listOf(Numeric.toHexStringNoPrefix(preSigningOutput.dataHash.toByteArray()))
    }

    fun getSignedTransaction(
        keysignPayload: KeysignPayload,
        signatures: Map<String, KeysignResponse>,
    ): SignedTransactionResult {
        val inputData = getPreSignedInputData(keysignPayload)
        return getSignedTransaction(
            input = inputData,
            keysignPayload = keysignPayload,
            signatures = signatures
        )
    }

    private fun getPreSignedInputData(keysignPayload: KeysignPayload): ByteArray {
        val atomData = keysignPayload.blockChainSpecific as? BlockChainSpecific.Cosmos
            ?: error("Invalid blockChainSpecific for Cosmos")
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
                                            .setDenom(
                                                if (keysignPayload.coin.contractAddress.contains("factory/") || keysignPayload.coin.contractAddress.contains("ibc/"))
                                                    keysignPayload.coin.contractAddress
                                                else
                                                    denom
                                            )
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
                    .setGas(gasLimit)
                    .addAllAmounts(
                        listOf(
                            Cosmos.Amount.newBuilder()
                                .setDenom(denom)
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

    fun getSignedTransaction(
        input: ByteArray,
        keysignPayload: KeysignPayload,
        signatures: Map<String, KeysignResponse>,
    ): SignedTransactionResult {
        val publicKey =
            PublicKey(keysignPayload.coin.hexPublicKey.hexToByteArray(), PublicKeyType.SECP256K1)
        val hashes = preImageHashes(coinType, input)
        val preSigningOutput =
            TransactionCompiler.PreSigningOutput.parseFrom(hashes)
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
        val compileWithSignature = compileWithSignatures(
            coinType,
            input,
            allSignatures,
            allPublicKeys
        )
        val output = Cosmos.SigningOutput.parseFrom(compileWithSignature)
        val cosmosSig = Json.decodeFromString<CosmoSignature>(output.serialized)
        return SignedTransactionResult(
            output.serialized,
            cosmosSig.transactionHash(),
        )
    }

}