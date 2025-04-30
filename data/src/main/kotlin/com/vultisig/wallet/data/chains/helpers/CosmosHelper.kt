package com.vultisig.wallet.data.chains.helpers

import com.google.protobuf.ByteString
import com.vultisig.wallet.data.crypto.checkError
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
import vultisig.keysign.v1.TransactionType
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
            Chain.Terra, Chain.TerraClassic, Chain.Osmosis -> 300000L
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
                .checkError()
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

        return when (atomData.transactionType) {
            TransactionType.TRANSACTION_TYPE_IBC_TRANSFER -> {
                val memoParts = keysignPayload.memo?.split(":")
                    ?: throw Exception("To send IBC transaction, memo should be specified")
                val sourceChannel = memoParts.getOrNull(1) ?: ""

                val memo = if (memoParts.size == 4) memoParts[3] else ""

                val timeouts = atomData.ibcDenomTraces?.latestBlock?.split("_") ?: emptyList()
                val timeout = timeouts.lastOrNull()?.toLongOrNull() ?: 0L

                val transferMessage = Cosmos.Message.Transfer.newBuilder()
                    .setSourcePort("transfer")
                    .setSourceChannel(sourceChannel)
                    .setSender(keysignPayload.coin.address)
                    .setReceiver(keysignPayload.toAddress)
                    .setToken(
                        Cosmos.Amount.newBuilder()
                            .setDenom(if (keysignPayload.coin.isNativeToken) denom else keysignPayload.coin.contractAddress)
                            .setAmount(keysignPayload.toAmount.toString())
                            .build()
                    )
                    .setTimeoutHeight(
                        Cosmos.Height.newBuilder()
                            .setRevisionNumber(0)
                            .setRevisionHeight(0)
                            .build()
                    )
                    .setTimeoutTimestamp(timeout)
                    .build()

                val input = Cosmos.SigningInput.newBuilder()
                    .setPublicKey(ByteString.copyFrom(publicKey.data()))
                    .setSigningMode(Cosmos.SigningMode.Protobuf)
                    .setChainId(coinType.chainId())
                    .setAccountNumber(atomData.accountNumber.toLong())
                    .setSequence(atomData.sequence.toLong())
                    .setMode(Cosmos.BroadcastMode.SYNC)
                    .addMessages(
                        Cosmos.Message.newBuilder()
                            .setTransferTokensMessage(transferMessage)
                            .build()
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
                    .apply {
                        if (memo.isNotEmpty()) {
                            this.memo = memo
                        }
                    }
                    .build()

                input.toByteArray()
            }
            else -> {
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

                input.build().toByteArray()
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
                .checkError()

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
            .checkError()
        val cosmosSig = Json.decodeFromString<CosmoSignature>(output.serialized)
        return SignedTransactionResult(
            output.serialized,
            cosmosSig.transactionHash(),
        )
    }

}