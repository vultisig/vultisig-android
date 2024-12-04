package com.vultisig.wallet.data.chains.helpers

import com.google.protobuf.ByteString
import com.vultisig.wallet.data.models.CosmoSignature
import com.vultisig.wallet.data.models.SignedTransactionResult
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.models.transactionHash
import com.vultisig.wallet.data.tss.getSignatureWithRecoveryID
import com.vultisig.wallet.data.utils.Numeric
import kotlinx.serialization.json.Json
import tss.KeysignResponse
import wallet.core.jni.AnyAddress
import wallet.core.jni.CoinType
import wallet.core.jni.DataVector
import wallet.core.jni.PublicKey
import wallet.core.jni.PublicKeyType
import wallet.core.jni.TransactionCompiler.compileWithSignatures
import wallet.core.jni.TransactionCompiler.preImageHashes
import wallet.core.jni.proto.Cosmos
import wallet.core.jni.proto.TransactionCompiler

@OptIn(ExperimentalStdlibApi::class)
class TerraHelper(
    private val coinType: CoinType,
    private val denom: String,
    private val gasLimit: Long,
) {

    private val cosmos = CosmosHelper(coinType, denom, gasLimit)

    fun getSwapPreSignedInputData(
        keysignPayload: KeysignPayload,
        input: Cosmos.SigningInput.Builder,
    ): ByteArray = cosmos.getSwapPreSignedInputData(keysignPayload, input)

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

        if (keysignPayload.coin.isNativeToken
            || keysignPayload.coin.contractAddress.startsWith("ibc/", ignoreCase = true)
            || keysignPayload.coin.contractAddress.startsWith("factory/", ignoreCase = true)
        ) {
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
                                                    if (keysignPayload.coin.isNativeToken)
                                                        denom
                                                    else keysignPayload.coin.contractAddress
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
        } else {
            if (!keysignPayload.coin.contractAddress.contains("terra1")
                && !keysignPayload.coin.contractAddress.contains("ibc/")
            ) {
                val input = Cosmos.SigningInput.newBuilder()
                    .setPublicKey(ByteString.copyFrom(publicKey.data()))
                    .setSigningMode(Cosmos.SigningMode.Protobuf)
                    .setChainId(coinType.chainId())
                    .setAccountNumber(atomData.accountNumber.toLong())
                    .setSequence(atomData.sequence.toLong())
                    .setMode(Cosmos.BroadcastMode.SYNC)
                    .let { builder ->
                        keysignPayload.memo?.let {
                            builder.setMemo(it)
                        } ?: builder
                    }
                    .addMessages(
                        Cosmos.Message.newBuilder()
                            .setSendCoinsMessage(
                                Cosmos.Message.Send.newBuilder()
                                    .setFromAddress(keysignPayload.coin.address)
                                    .addAmounts(
                                        Cosmos.Amount.newBuilder()
                                            .setDenom(keysignPayload.coin.contractAddress)
                                            .setAmount(keysignPayload.toAmount.toString())
                                            .build()
                                    )
                                    .setToAddress(keysignPayload.toAddress)
                                    .build()
                            )
                            .build()
                    )
                    .setFee(
                        Cosmos.Fee.newBuilder()
                            .setGas(1000000)
                            .addAmounts(
                                Cosmos.Amount.newBuilder()
                                    .setDenom("uluna")
                                    .setAmount(atomData.gas.toString())
                                    .build()
                            )
                            .addAmounts(
                                Cosmos.Amount.newBuilder()
                                    .setDenom("uusd")
                                    .setAmount("1000000")
                                    .build()
                            )
                            .build()
                    )
                    .build()

                return input.toByteArray()
            } else {
                val fromAddr = AnyAddress(keysignPayload.coin.address, coinType)
                    ?: error("${keysignPayload.coin.address} is invalid")

                val wasmGenericMessage = Cosmos.Message.WasmExecuteContractGeneric.newBuilder()
                    .setSenderAddress(fromAddr.description())
                    .setContractAddress(keysignPayload.coin.contractAddress)
                    .setExecuteMsg(
                        """
                        {"transfer": { "amount": "${keysignPayload.toAmount}", "recipient": "${keysignPayload.toAddress}" } }
                        """.trimIndent()
                    )
                    .build()

                val message = Cosmos.Message.newBuilder()
                    .setWasmExecuteContractGeneric(wasmGenericMessage)
                    .build()

                val fee = Cosmos.Fee.newBuilder()
                    .setGas(gasLimit)
                    .addAmounts(
                        Cosmos.Amount.newBuilder()
                            .setAmount(atomData.gas.toString())
                            .setDenom(denom)
                            .build()
                    )
                    .build()

                val input = Cosmos.SigningInput.newBuilder()
                    .setSigningMode(Cosmos.SigningMode.Protobuf)
                    .setAccountNumber(atomData.accountNumber.toLong())
                    .setChainId(coinType.chainId())
                    .let { builder ->
                        keysignPayload.memo?.let { builder.setMemo(it) } ?: builder
                    }
                    .setSequence(atomData.sequence.toLong())
                    .addMessages(message)
                    .setFee(fee)
                    .setPublicKey(ByteString.copyFrom(publicKey.data()))
                    .setMode(Cosmos.BroadcastMode.SYNC)
                    .build()

                return input.toByteArray()
            }
        }
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