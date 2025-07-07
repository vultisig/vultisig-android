package com.vultisig.wallet.data.api.swapAggregators

import com.google.protobuf.ByteString
import com.vultisig.wallet.data.api.models.quotes.KyberSwapQuoteResponse
import com.vultisig.wallet.data.chains.helpers.EvmHelper
import com.vultisig.wallet.data.common.toByteStringOrHex
import com.vultisig.wallet.data.crypto.checkError
import com.vultisig.wallet.data.models.SignedTransactionResult
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.ERC20ApprovePayload
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.utils.Numeric
import tss.KeysignResponse
import wallet.core.jni.TransactionCompiler
import wallet.core.jni.proto.Ethereum
import java.math.BigInteger
import  com.vultisig.wallet.data.common.toByteString
import com.vultisig.wallet.data.models.payload.KyberSwapPayloadJson
import com.vultisig.wallet.data.wallet.Swaps

class KyberSwap(
    private val vaultHexPublicKey: String,
    private val vaultHexChainCode: String,

    ) {

    @Throws(Exception::class)
    fun getPreSignedImageHash(
        swapPayload: KyberSwapPayloadJson,
        keysignPayload: KeysignPayload,
        nonceIncrement: BigInteger,
    ): List<String> {
        val inputData = getPreSignedInputData(
            swapPayload.quote,
            keysignPayload,
            nonceIncrement
        )


        val chain = swapPayload.fromCoin.chain
        val coinType = keysignPayload.coin.coinType

        return Swaps.getPreSignedImageHash(inputData, coinType, chain)
    }

    @Throws(Exception::class)
    fun getSignedTransaction(
        swapPayload: KyberSwapPayloadJson,
        keysignPayload: KeysignPayload,
        signatures: Map<String, KeysignResponse>,
        incrementNonce: BigInteger
    ): SignedTransactionResult {
        val inputData = getPreSignedInputData(
            swapPayload.quote,
            keysignPayload,
            incrementNonce
        )

        val helper = EvmHelper(
            keysignPayload.coin.coinType,
            vaultHexPublicKey,
            vaultHexChainCode
        )

        return helper.getSignedTransaction(
            inputData,
            signatures
        )

    }

    @Throws(Exception::class)
    fun getPreSignedApproveInputData(
        approvePayload: ERC20ApprovePayload, keysignPayload: KeysignPayload
    ): ByteArray {
        val approveInput = Ethereum.SigningInput.newBuilder()

            .setToAddress(keysignPayload.coin.contractAddress).setTransaction(
                Ethereum.Transaction.newBuilder().setErc20Approve(
                    Ethereum.Transaction.ERC20Approve.newBuilder().setAmount(
                        ByteString.copyFrom(
                            approvePayload.amount.abs().toByteArray()
                        )
                    ).setSpender(approvePayload.spender).build()
                ).build()
            ).build()


        return EvmHelper(
            keysignPayload.coin.coinType,
            vaultHexPublicKey,
            vaultHexChainCode
        ).getPreSignedInputData(
            signingInput = approveInput,
            keysignPayload = keysignPayload
        )

//        return EvmHelper.getHelper(keysignPayload.coin)
//            .getPreSignedInputData(
//                approveInput,
//                keysignPayload
//            )
    }

    @Throws(Exception::class)
    fun getPreSignedApproveImageHash(
        approvePayload: ERC20ApprovePayload, keysignPayload: KeysignPayload
    ): List<String> {
        val inputData = getPreSignedApproveInputData(
            approvePayload,
            keysignPayload
        )
        val hashes = TransactionCompiler.preImageHashes(
            keysignPayload.coin.coinType,
            inputData
        )
        val preSigningOutput =
            wallet.core.jni.proto.TransactionCompiler.PreSigningOutput.parseFrom(hashes)
                .checkError()

        return listOf(Numeric.toHexStringNoPrefix(preSigningOutput.dataHash.toByteArray()))
    }

    @Throws(Exception::class)
    fun getSignedApproveTransaction(
        approvePayload: ERC20ApprovePayload, keysignPayload: KeysignPayload,
        signatures: Map<String, KeysignResponse>
    ): SignedTransactionResult {
        val inputData = getPreSignedApproveInputData(
            approvePayload,
            keysignPayload
        )

        return EvmHelper(
            keysignPayload.coin.coinType,
            vaultHexPublicKey,
            vaultHexChainCode
        ).getSignedTransaction(
            inputData,
            signatures
        )


    }

    @Throws(Exception::class)
    fun getPreSignedInputData(
        quote: KyberSwapQuoteResponse?,
        keysignPayload: KeysignPayload,
        incrementNonce: BigInteger
    ): ByteArray {

        val input = Ethereum.SigningInput.newBuilder()

            .setToAddress(quote?.tx?.to).setTransaction(
                Ethereum.Transaction.newBuilder().setContractGeneric(
                    Ethereum.Transaction.ContractGeneric.newBuilder().setAmount(
                        ByteString.copyFrom(
                            quote?.tx?.value?.toBigInteger()?.toByteArray()
                                ?: BigInteger.ZERO.toByteArray()
                        )
                    ).setData(quote?.tx?.data?.removePrefix("0x")?.toByteStringOrHex())
                ).build()
            ).build()

        val gas =
            quote?.gasForChain(keysignPayload.coin.chain) ?: error("fail to get gas for chain")
        return getPreSignedInputDataWithCustomGasLimit(
            input,
            keysignPayload,
            gas.toBigInteger(),
            incrementNonce
        )
    }

    @Throws(Exception::class)
    fun getPreSignedInputDataWithCustomGasLimit(
        input: Ethereum.SigningInput, keysignPayload: KeysignPayload, customGasLimit: BigInteger,
        incrementNonce: BigInteger
    ): ByteArray {
        val chainSpecific = keysignPayload.blockChainSpecific as? BlockChainSpecific.Ethereum
            ?: error("fail to get Ethereum chain specific")

        val oneGweiInWei = BigInteger("1000000000")
        val correctedPriorityFee = chainSpecific.priorityFeeWei.max(oneGweiInWei)
        val correctedMaxFeePerGas = listOf(
            chainSpecific.maxFeePerGasWei,
            correctedPriorityFee,
            oneGweiInWei
        ).maxOrNull() ?: oneGweiInWei


        //Todo add ETHEREUM_SEPOLIA test chain
//        val chainIdString = if (keysignPayload.coin.chain == Chain.ETHEREUM_SEPOLIA)
//            "11155111"
//        else
//            keysignPayload.coin.coinType.chainId

        val chainIdString = keysignPayload.coin.coinType.chainId()

        val intChainID = chainIdString.toBigIntegerOrNull() ?: error("fail to get chainID")

//        val incrementNonceValue = if (incrementNonce) 1L else 0L
        val inputBuilder = input.toBuilder()


        inputBuilder.chainId = intChainID.toByteString()
//        inputBuilder.nonce = (chainSpecific.nonce + incrementNonceValue.toBigInteger()).toByteString()
        inputBuilder.nonce = (chainSpecific.nonce + incrementNonce).toByteString()
        inputBuilder.gasLimit = customGasLimit.toByteString()
        inputBuilder.maxFeePerGas = correctedMaxFeePerGas.abs().toByteString()
        inputBuilder.maxInclusionFeePerGas = correctedPriorityFee.abs().toByteString()
        inputBuilder.txMode = Ethereum.TransactionMode.Enveloped


        return inputBuilder.build().toByteArray()
    }
}