package com.vultisig.wallet.data.chains.helpers

import com.google.protobuf.ByteString
import com.vultisig.wallet.data.crypto.checkError
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.CosmoSignature
import com.vultisig.wallet.data.models.SignedTransactionResult
import com.vultisig.wallet.data.models.coinType
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.models.payload.SwapPayload
import com.vultisig.wallet.data.models.transactionHash
import com.vultisig.wallet.data.tss.getSignatureWithRecoveryID
import com.vultisig.wallet.data.utils.Numeric
import kotlinx.serialization.json.Json
import tss.KeysignResponse
import vultisig.keysign.v1.SignAmino
import vultisig.keysign.v1.SignDirect
import vultisig.keysign.v1.TransactionType
import wallet.core.jni.Base64
import wallet.core.jni.CoinType
import wallet.core.jni.DataVector
import wallet.core.jni.PublicKey
import wallet.core.jni.PublicKeyType
import wallet.core.jni.TransactionCompiler.compileWithSignatures
import wallet.core.jni.TransactionCompiler.preImageHashes
import wallet.core.jni.proto.Cosmos
import wallet.core.jni.proto.Cosmos.Amount
import wallet.core.jni.proto.TransactionCompiler

@OptIn(ExperimentalStdlibApi::class)
class CosmosHelper(
    private val coinType: CoinType,
    private val denom: String,
    private val gasLimit: Long = DEFAULT_COSMOS_GAS_LIMIT,
) {

    companion object {
        const val DEFAULT_COSMOS_GAS_LIMIT = 200000L

        fun getChainGasLimit(chain: Chain): Long = when (chain) {
            Chain.Terra, Chain.TerraClassic, Chain.Osmosis -> 300000L
            else -> DEFAULT_COSMOS_GAS_LIMIT
        }

        const val ATOM_DENOM = "uatom"
    }

    fun getSwapPreSignedInputData(
        keysignPayload: KeysignPayload
    ): ByteArray {
        val cosmosSpecific = keysignPayload.blockChainSpecific as? BlockChainSpecific.Cosmos
            ?: throw Exception("Invalid blockChainSpecific")
        val thorChainSwapPayload = keysignPayload.swapPayload as? SwapPayload.ThorChain
            ?: throw Exception("Invalid swap payload for THORChain")
        require(!keysignPayload.memo.isNullOrEmpty()) {
            "Memo is required for THORChain swap"
        }
        val publicKey =
            PublicKey(
                keysignPayload.coin.hexPublicKey.hexToByteArray(),
                PublicKeyType.SECP256K1
            )
        keysignPayload.signAmino?.let { signAmino ->
            return buildAminoSigningInput(
                signAmino = signAmino,
                cosmosSpecific = cosmosSpecific,
                publicKey = publicKey,
                memo = keysignPayload.memo
            )
        }
        val inputData = Cosmos.SigningInput.newBuilder()
            .setChainId(coinType.chainId())
            .setMemo(keysignPayload.memo)
            .setPublicKey(ByteString.copyFrom(publicKey.data()))
            .setAccountNumber(cosmosSpecific.accountNumber.toLong())
            .setSequence(cosmosSpecific.sequence.toLong())
            .setMode(Cosmos.BroadcastMode.SYNC)
            .setSigningMode(Cosmos.SigningMode.Protobuf)
            .addAllMessages(
                listOf(
                    Cosmos.Message.newBuilder()
                        .setSendCoinsMessage(
                            Cosmos.Message.Send.newBuilder()
                                .setFromAddress(thorChainSwapPayload.data.fromAddress)
                                .setToAddress(thorChainSwapPayload.data.vaultAddress)
                                .addAllAmounts(
                                    listOf(
                                        Cosmos.Amount.newBuilder()
                                            .setDenom(denom)
                                            .setAmount(thorChainSwapPayload.data.fromAmount.toString())
                                            .build()
                                    )
                                )
                                .build()
                        )
                        .build()
                )
            )
            .setFee(
                buildCosmosFee(cosmosSpecific)

            ).build()

        return inputData.toByteArray()
    }

    fun getPreSignedImageHash(keysignPayload: KeysignPayload): List<String> {
        val result = getPreSignedInputData(keysignPayload)
        val hashes = preImageHashes(
            coinType,
            result
        )
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
            PublicKey(
                keysignPayload.coin.hexPublicKey.hexToByteArray(),
                PublicKeyType.SECP256K1
            )
        keysignPayload.signAmino?.let { signAmino ->
            return buildAminoSigningInput(
                signAmino = signAmino,
                cosmosSpecific = atomData,
                publicKey = publicKey,
                memo = keysignPayload.memo,
            )
        }
        keysignPayload.signDirect?.let { signDirect ->
            return buildSignDirectSigningInput(
                signDirect = signDirect,
                cosmosData = atomData,
                publicKey = publicKey,
                memo = keysignPayload.memo,

                )
        }
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
                    .setFee(buildCosmosFee(atomData))
                    .apply {
                        if (memo.isNotEmpty()) {
                            this.memo = memo
                        }
                    }
                    .build()

                input.toByteArray()
            }

            TransactionType.TRANSACTION_TYPE_GENERIC_CONTRACT -> {
                val input = Cosmos.SigningInput.newBuilder()
                    .setPublicKey(ByteString.copyFrom(publicKey.data()))
                    .setSigningMode(Cosmos.SigningMode.Protobuf)
                    .setChainId(coinType.chainId())
                    .setAccountNumber(atomData.accountNumber.toLong())
                    .setSequence(atomData.sequence.toLong())
                    .setMode(Cosmos.BroadcastMode.SYNC)
                    .addAllMessages(listOf(buildCosmosWasmGenericMsg(keysignPayload)))
                    .setFee(buildCosmosFee(atomData))
                    .build()

                return input.toByteArray()
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
                    .setFee(buildCosmosFee(atomData))
                keysignPayload.memo?.let {
                    input = input.setMemo(it)
                }

                input.build().toByteArray()
            }
        }
    }

    private fun buildCosmosWasmGenericMsg(keysignPayload: KeysignPayload): Cosmos.Message {
        val coinType = keysignPayload.coin.chain.coinType
        require(coinType.validate(keysignPayload.coin.address)) {
            "Invalid Address type: ${keysignPayload.coin.address}"
        }
        requireNotNull(keysignPayload.wasmExecuteContractPayload) {
            "Invalid empty WasmExecuteContractPayload"
        }
        val contractPayload = keysignPayload.wasmExecuteContractPayload

        val coins = contractPayload.coins.filterNotNull().map { coin ->
            Amount.newBuilder().apply {
                denom = coin.denom.lowercase()
                amount = coin.amount
            }.build()
        }

        return Cosmos.Message.newBuilder().apply {
            wasmExecuteContractGeneric =
                Cosmos.Message.WasmExecuteContractGeneric.newBuilder().apply {
                    senderAddress = keysignPayload.coin.address
                    contractAddress = keysignPayload.toAddress
                    executeMsg = contractPayload.executeMsg
                    addAllCoins(coins)
                }.build()
        }.build()
    }

    private fun buildCosmosFee(atomData: BlockChainSpecific.Cosmos): Cosmos.Fee.Builder? =
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

    fun getSignedTransaction(
        input: ByteArray,
        keysignPayload: KeysignPayload,
        signatures: Map<String, KeysignResponse>,
    ): SignedTransactionResult {
        val publicKey =
            PublicKey(
                keysignPayload.coin.hexPublicKey.hexToByteArray(),
                PublicKeyType.SECP256K1
            )
        val hashes = preImageHashes(
            coinType,
            input
        )
        val preSigningOutput =
            TransactionCompiler.PreSigningOutput.parseFrom(hashes)
                .checkError()

        val key = Numeric.toHexStringNoPrefix(preSigningOutput.dataHash.toByteArray())
        val signature = signatures[key]?.getSignatureWithRecoveryID()
            ?: throw Exception("Invalid signature")
        if (!publicKey.verify(
                signature,
                preSigningOutput.dataHash.toByteArray()
            )
        ) {
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

    private fun buildSignDirectSigningInput(
        signDirect: SignDirect,
        cosmosData: BlockChainSpecific.Cosmos,
        memo: String?,
        publicKey: PublicKey
    ): ByteArray {
        val bodyBytes = Base64.decode(signDirect.bodyBytes)
        val txBody = Cosmos.SigningInput.parseFrom(bodyBytes)

        val message = Cosmos.Message.newBuilder()
            .setSignDirectMessage(
                Cosmos.Message.SignDirect.newBuilder()
                    .setBodyBytes(ByteString.copyFrom(bodyBytes))
                    .setAuthInfoBytes(
                        ByteString.copyFrom(
                            java.util.Base64.getDecoder().decode(signDirect.authInfoBytes)
                        )
                    )
                    .build()
            )
            .build()

        val inputBuilder = Cosmos.SigningInput.newBuilder()
            .setPublicKey(ByteString.copyFrom(publicKey.data()))
            .setSigningMode(Cosmos.SigningMode.Protobuf)
            .setChainId(coinType.chainId())
            .setAccountNumber(cosmosData.accountNumber.toLong())
            .setSequence(cosmosData.sequence.toLong())
            .setMode(Cosmos.BroadcastMode.SYNC)
            .addMessages(message)
            .setFee(buildCosmosFee(cosmosData))

        if (txBody.memo.isNotEmpty()) {
            inputBuilder.memo = txBody.memo
        } else {
            memo?.let { inputBuilder.memo = it }
        }

        return inputBuilder.build().toByteArray()
    }

    private fun buildAminoSigningInput(
        signAmino: SignAmino,
        cosmosSpecific: BlockChainSpecific.Cosmos,
        publicKey: PublicKey,
        memo: String?
    ): ByteArray {
        val inputBuilder = Cosmos.SigningInput.newBuilder()
            .setPublicKey(ByteString.copyFrom(publicKey.data()))
            .setSigningMode(Cosmos.SigningMode.JSON)  // Use JSON mode for Amino
            .setChainId(coinType.chainId())
            .setAccountNumber(cosmosSpecific.accountNumber.toLong())
            .setSequence(cosmosSpecific.sequence.toLong())
            .setMode(Cosmos.BroadcastMode.SYNC)

        signAmino.msgs.forEach { cosmosMsg ->
            val message = Cosmos.Message.newBuilder()
                .setRawJsonMessage(
                    Cosmos.Message.RawJSON.newBuilder()
                        .setType(cosmosMsg?.type)
                        .setValue(cosmosMsg?.value)
                        .build()
                )
                .build()
            inputBuilder.addMessages(message)
        }
        val feeBuilder = Cosmos.Fee.newBuilder()
            .setGas(signAmino.fee?.gas?.toLongOrNull() ?: gasLimit)

        signAmino.fee?.amount?.forEach { amount ->
            feeBuilder.addAmounts(
                Cosmos.Amount.newBuilder()
                    .setDenom(amount?.denom)
                    .setAmount(amount?.amount)
                    .build()
            )
        }
        inputBuilder.setFee(feeBuilder)

        memo?.let { inputBuilder.memo = it }

        return inputBuilder.build().toByteArray()
    }
}