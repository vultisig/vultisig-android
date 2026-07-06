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
import com.vultisig.wallet.data.models.proto.v1.SignDirectProto
import com.vultisig.wallet.data.models.transactionHash
import com.vultisig.wallet.data.tss.getSignatureWithRecoveryID
import com.vultisig.wallet.data.utils.Numeric
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import tss.KeysignResponse
import vultisig.keysign.v1.SignAmino
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

        fun getChainGasLimit(chain: Chain): Long =
            when (chain) {
                Chain.Qbtc, // ML-DSA-44 signatures are ~2.4 KB, needs more gas
                Chain.Terra,
                Chain.TerraClassic,
                Chain.Osmosis -> 300000L
                else -> DEFAULT_COSMOS_GAS_LIMIT
            }

        const val ATOM_DENOM = "uatom"

        // Cosmos `/simulate` skips signature verification, so a dummy r||s of the secp256k1
        // signature size is enough for the ante handler to meter gas.
        private const val SECP256K1_SIGNATURE_SIZE = 64
    }

    /**
     * Builds the base64 `tx_bytes` of a zero-signature Cosmos transaction for
     * `/cosmos/tx/v1beta1/simulate`. The node skips signature verification while simulating, so a
     * dummy [SECP256K1_SIGNATURE_SIZE]-byte signature is enough to let the ante handler meter gas.
     *
     * @return the `tx_bytes` field of WalletCore's broadcast payload.
     */
    fun getZeroSignedTransaction(keysignPayload: KeysignPayload): String {
        val input = getPreSignedInputData(keysignPayload)
        val publicKey =
            PublicKey(keysignPayload.coin.hexPublicKey.hexToByteArray(), PublicKeyType.SECP256K1)
        val allSignatures = DataVector().apply { add(ByteArray(SECP256K1_SIGNATURE_SIZE)) }
        val allPublicKeys = DataVector().apply { add(publicKey.data()) }
        val compiled = compileWithSignatures(coinType, input, allSignatures, allPublicKeys)
        val output = Cosmos.SigningOutput.parseFrom(compiled).checkError()
        return Json.parseToJsonElement(output.serialized)
            .jsonObject["tx_bytes"]
            ?.jsonPrimitive
            ?.content ?: error("Simulate payload missing tx_bytes")
    }

    fun getSwapPreSignedInputData(keysignPayload: KeysignPayload): ByteArray {
        val cosmosSpecific =
            keysignPayload.blockChainSpecific as? BlockChainSpecific.Cosmos
                ?: throw Exception("Invalid blockChainSpecific")
        val thorChainSwapPayload =
            keysignPayload.swapPayload as? SwapPayload.ThorChain
                ?: throw Exception("Invalid swap payload for THORChain")
        require(!keysignPayload.memo.isNullOrEmpty()) { "Memo is required for THORChain swap" }
        val publicKey =
            PublicKey(keysignPayload.coin.hexPublicKey.hexToByteArray(), PublicKeyType.SECP256K1)
        keysignPayload.signAmino?.let { signAmino ->
            return buildAminoSigningInput(
                signAmino = signAmino,
                cosmosSpecific = cosmosSpecific,
                publicKey = publicKey,
                memo = keysignPayload.memo,
            )
        }
        val inputData =
            Cosmos.SigningInput.newBuilder()
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
                                                .setAmount(
                                                    thorChainSwapPayload.data.fromAmount.toString()
                                                )
                                                .build()
                                        )
                                    )
                                    .build()
                            )
                            .build()
                    )
                )
                .setFee(buildCosmosFee(cosmosSpecific))
                .build()

        return inputData.toByteArray()
    }

    fun getPreSignedImageHash(keysignPayload: KeysignPayload): List<String> {
        val result = getPreSignedInputData(keysignPayload)
        val hashes = preImageHashes(coinType, result)
        val preSigningOutput = TransactionCompiler.PreSigningOutput.parseFrom(hashes).checkError()
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
            signatures = signatures,
        )
    }

    private fun getPreSignedInputData(keysignPayload: KeysignPayload): ByteArray {
        val atomData =
            keysignPayload.blockChainSpecific as? BlockChainSpecific.Cosmos
                ?: error("Invalid blockChainSpecific for Cosmos")
        val publicKey =
            PublicKey(keysignPayload.coin.hexPublicKey.hexToByteArray(), PublicKeyType.SECP256K1)
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
                val ibc =
                    parseIbcTransferParams(
                        memo = keysignPayload.memo,
                        latestBlock = atomData.ibcDenomTraces?.latestBlock,
                    )
                val sourceChannel = ibc.sourceChannel
                val memo = ibc.forwardMemo
                val timeout = ibc.timeoutTimestamp

                val transferMessage =
                    Cosmos.Message.Transfer.newBuilder()
                        .setSourcePort("transfer")
                        .setSourceChannel(sourceChannel)
                        .setSender(keysignPayload.coin.address)
                        .setReceiver(keysignPayload.toAddress)
                        .setToken(
                            Cosmos.Amount.newBuilder()
                                .setDenom(
                                    if (keysignPayload.coin.isNativeToken) denom
                                    else keysignPayload.coin.contractAddress
                                )
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

                val input =
                    Cosmos.SigningInput.newBuilder()
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
                val input =
                    Cosmos.SigningInput.newBuilder()
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
                var input =
                    Cosmos.SigningInput.newBuilder()
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
                                                            if (
                                                                keysignPayload.coin.contractAddress
                                                                    .contains("factory/") ||
                                                                    keysignPayload.coin
                                                                        .contractAddress
                                                                        .contains("ibc/")
                                                            )
                                                                keysignPayload.coin.contractAddress
                                                            else denom
                                                        )
                                                        .setAmount(
                                                            keysignPayload.toAmount.toString()
                                                        )
                                                        .build()
                                                )
                                            )
                                            .build()
                                    )
                                    .build()
                            )
                        )
                        .setFee(buildCosmosFee(atomData))
                keysignPayload.memo?.let { input = input.setMemo(it) }

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

        val coins =
            contractPayload.coins.filterNotNull().map { coin ->
                Amount.newBuilder()
                    .apply {
                        denom = coin.denom.lowercase()
                        amount = coin.amount
                    }
                    .build()
            }

        return Cosmos.Message.newBuilder()
            .apply {
                wasmExecuteContractGeneric =
                    Cosmos.Message.WasmExecuteContractGeneric.newBuilder()
                        .apply {
                            senderAddress = keysignPayload.coin.address
                            contractAddress = keysignPayload.toAddress
                            executeMsg = contractPayload.executeMsg
                            addAllCoins(coins)
                        }
                        .build()
            }
            .build()
    }

    private fun buildCosmosFee(atomData: BlockChainSpecific.Cosmos): Cosmos.Fee.Builder {
        // Honor the relayed dynamic gas limit when an initiator set one; otherwise fall back to the
        // static per-chain limit. Both co-signers hash this gas value into the SignDoc, so they
        // must
        // resolve to the identical limit or the MPC signature fails.
        val effectiveGasLimit = atomData.gasLimit?.takeIf { it.signum() > 0 }?.longValueExact() ?: gasLimit
        val fee = Cosmos.Fee.newBuilder().setGas(effectiveGasLimit)
        // A zero fee amount yields an empty fee: the `/simulate` tx must not declare a fee, or the
        // AnteHandler's DeductFee charges it and fails MAX / near-balance sends.
        if (atomData.gas.signum() > 0) {
            fee.addAmounts(
                Cosmos.Amount.newBuilder()
                    .setDenom(denom)
                    .setAmount(atomData.gas.toString())
                    .build()
            )
        }
        return fee
    }

    fun getSignedTransaction(
        input: ByteArray,
        keysignPayload: KeysignPayload,
        signatures: Map<String, KeysignResponse>,
    ): SignedTransactionResult {
        val publicKey =
            PublicKey(keysignPayload.coin.hexPublicKey.hexToByteArray(), PublicKeyType.SECP256K1)
        val hashes = preImageHashes(coinType, input)
        val preSigningOutput = TransactionCompiler.PreSigningOutput.parseFrom(hashes).checkError()

        val key = Numeric.toHexStringNoPrefix(preSigningOutput.dataHash.toByteArray())
        val signature =
            signatures[key]?.getSignatureWithRecoveryID() ?: throw Exception("Invalid signature")
        if (!publicKey.verify(signature, preSigningOutput.dataHash.toByteArray())) {
            throw Exception("Invalid signature")
        }
        val allSignatures = DataVector()
        val allPublicKeys = DataVector()
        allSignatures.add(signature)
        allPublicKeys.add(publicKey.data())
        val compileWithSignature =
            compileWithSignatures(coinType, input, allSignatures, allPublicKeys)
        val output = Cosmos.SigningOutput.parseFrom(compileWithSignature).checkError()
        val cosmosSig = Json.decodeFromString<CosmoSignature>(output.serialized)
        return SignedTransactionResult(output.serialized, cosmosSig.transactionHash())
    }

    private fun buildSignDirectSigningInput(
        signDirect: SignDirectProto,
        cosmosData: BlockChainSpecific.Cosmos,
        memo: String?,
        publicKey: PublicKey,
    ): ByteArray {
        val bodyBytes = Base64.decode(signDirect.bodyBytes)
        val txBody = Cosmos.SigningInput.parseFrom(bodyBytes)

        val message =
            Cosmos.Message.newBuilder()
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

        val inputBuilder =
            Cosmos.SigningInput.newBuilder()
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
        memo: String?,
    ): ByteArray {
        val inputBuilder =
            Cosmos.SigningInput.newBuilder()
                .setPublicKey(ByteString.copyFrom(publicKey.data()))
                .setSigningMode(Cosmos.SigningMode.JSON)
                .setChainId(coinType.chainId())
                .setAccountNumber(cosmosSpecific.accountNumber.toLong())
                .setSequence(cosmosSpecific.sequence.toLong())
                .setMode(Cosmos.BroadcastMode.SYNC)

        signAmino.msgs.forEach { cosmosMsg ->
            val message =
                Cosmos.Message.newBuilder()
                    .setRawJsonMessage(
                        Cosmos.Message.RawJSON.newBuilder()
                            .setType(cosmosMsg?.type)
                            .setValue(cosmosMsg?.value)
                            .build()
                    )
                    .build()
            inputBuilder.addMessages(message)
        }
        val feeBuilder =
            Cosmos.Fee.newBuilder().setGas(signAmino.fee?.gas?.toLongOrNull() ?: gasLimit)

        signAmino.fee?.amount?.forEach { amount ->
            feeBuilder.addAmounts(
                Cosmos.Amount.newBuilder().setDenom(amount?.denom).setAmount(amount?.amount).build()
            )
        }
        inputBuilder.setFee(feeBuilder)

        memo?.let { inputBuilder.memo = it }

        return inputBuilder.build().toByteArray()
    }
}

/**
 * Validated parameters parsed from an IBC-transfer keysign memo.
 *
 * The memo grammar (see [com.vultisig.wallet.data.models.DepositMemo.TransferIbc]) is
 * `dstChain:sourceChannel:dstAddress[:forwardMemo]`, and the timeout timestamp is the trailing
 * `_`-separated segment of `latestBlock` (`"<block>_<timeout>"`).
 */
internal data class IbcTransferParams(
    val sourceChannel: String,
    val forwardMemo: String,
    val timeoutTimestamp: Long,
)

/**
 * Parses and validates an IBC-transfer memo/timeout, failing loudly instead of signing a malformed
 * or never-expiring packet (issue #4849). Previously a missing source channel silently became `""`
 * (a packet on no channel) and an unparseable timeout silently became `0L` (no effective timeout —
 * an IBC packet with both timeout height and timestamp zero is invalid and hangs until manual
 * refund).
 */
internal fun parseIbcTransferParams(memo: String?, latestBlock: String?): IbcTransferParams {
    val memoParts = memo?.split(":") ?: error("To send IBC transaction, memo should be specified")

    val sourceChannel = memoParts.getOrNull(1).orEmpty()
    require(sourceChannel.isNotBlank()) {
        "IBC transfer requires a source channel in the memo (dstChain:sourceChannel:dstAddress)"
    }

    // Forward memo is everything past the dstAddress; rejoin so a memo containing ':' survives.
    val forwardMemo = if (memoParts.size > 3) memoParts.drop(3).joinToString(":") else ""

    val timeoutTimestamp = latestBlock?.substringAfterLast('_')?.toLongOrNull() ?: 0L
    require(timeoutTimestamp > 0L) {
        "IBC transfer requires a non-zero timeout timestamp; latestBlock was missing or unparseable"
    }

    return IbcTransferParams(
        sourceChannel = sourceChannel,
        forwardMemo = forwardMemo,
        timeoutTimestamp = timeoutTimestamp,
    )
}
