package com.vultisig.wallet.chains

import com.vultisig.wallet.BuildConfig
import com.vultisig.wallet.common.Numeric
import com.vultisig.wallet.common.toByteString
import com.vultisig.wallet.models.ERC20ApprovePayload
import com.vultisig.wallet.models.SignedTransactionResult
import com.vultisig.wallet.models.THORChainSwapPayload
import com.vultisig.wallet.presenter.keysign.KeysignPayload
import tss.KeysignResponse
import wallet.core.jni.TransactionCompiler
import wallet.core.jni.proto.Bitcoin
import wallet.core.jni.proto.THORChainSwap

internal class THORChainSwaps(
    private val vaultHexPublicKey: String,
    private val vaultHexChainCode: String,
) {
    companion object {
        private const val AFFILIATE_FEE_ADDRESS = "vi"
        private const val AFFILIATE_FEE_RATE = "50" // 50 BP
    }

    fun getPreSignedInputData(
        swapPayload: THORChainSwapPayload,
        keysignPayload: KeysignPayload,
    ): ByteArray {
        val input = THORChainSwap.SwapInput.newBuilder()
            .setFromAsset(swapPayload.fromAsset)
            .setFromAddress(swapPayload.fromAddress)
            .setToAsset(swapPayload.toAsset)
            .setToAddress(swapPayload.toAddress)
            .setVaultAddress(swapPayload.vaultAddress)
            .setRouterAddress(swapPayload.routerAddress ?: "")
            .setFromAmount(swapPayload.fromAmount.toString())
            .setToAmountLimit(swapPayload.toAmountLimit)
            .setExpirationTime(swapPayload.expirationTime.toLong())
            .setStreamParams(
                THORChainSwap.StreamParams.newBuilder()
                    .setInterval(swapPayload.steamingInterval)
                    .setQuantity(swapPayload.streamingQuantity)
            ).let {
                if (swapPayload.isAffiliate) {
                    it.setAffiliateFeeAddress(AFFILIATE_FEE_ADDRESS)
                        .setAffiliateFeeRateBp(
                            if (BuildConfig.DEBUG) "0" else AFFILIATE_FEE_RATE
                        )
                } else {
                    it
                }
            }
            .build()
        val inputData = input.toByteArray()
        val outputData = wallet.core.jni.THORChainSwap.buildSwap(inputData)
        val output = THORChainSwap.SwapOutput.parseFrom(outputData)
        when (swapPayload.fromAsset.chain) {
            THORChainSwap.Chain.THOR -> {
                return THORCHainHelper(
                    vaultHexPublicKey,
                    vaultHexChainCode
                ).getSwapPreSignedInputData(
                    keysignPayload,
                    output.cosmos.toBuilder()
                )
            }

            THORChainSwap.Chain.BTC, THORChainSwap.Chain.LTC, THORChainSwap.Chain.DOGE, THORChainSwap.Chain.BCH -> {
                val helper =
                    utxoHelper(keysignPayload.coin.coinType, vaultHexPublicKey, vaultHexChainCode)
                return helper.getSigningInputData(
                    keysignPayload,
                    output.bitcoin.toBuilder()
                )
            }

            THORChainSwap.Chain.ETH, THORChainSwap.Chain.BSC, THORChainSwap.Chain.AVAX -> {
                val helper =
                    EvmHelper(keysignPayload.coin.coinType, vaultHexPublicKey, vaultHexChainCode)
                return helper.getPreSignedInputData(
                    keysignPayload = keysignPayload,
                    signingInput = output.ethereum,
                )
            }

            THORChainSwap.Chain.BNB -> {
                throw Exception("Unsupported chain")
            }


            THORChainSwap.Chain.ATOM -> {
                val helper = AtomHelper(vaultHexPublicKey, vaultHexChainCode)
                return helper.getSwapPreSignedInputData(
                    keysignPayload = keysignPayload,
                    input = output.cosmos.toBuilder()
                )
            }


            THORChainSwap.Chain.UNRECOGNIZED -> {
                throw Exception("Unsupported chain")
            }
        }
    }

    fun getPreSignedImageHash(
        swapPayload: THORChainSwapPayload,
        keysignPayload: KeysignPayload,
    ): List<String> {
        val inputData = getPreSignedInputData(swapPayload, keysignPayload)
        when (swapPayload.fromAsset.chain) {

            THORChainSwap.Chain.BTC, THORChainSwap.Chain.LTC, THORChainSwap.Chain.DOGE, THORChainSwap.Chain.BCH -> {
                val hashes =
                    TransactionCompiler.preImageHashes(keysignPayload.coin.coinType, inputData)
                val preSigningOutput =
                    Bitcoin.PreSigningOutput.parseFrom(hashes)
                return preSigningOutput.hashPublicKeysList.map { Numeric.toHexStringNoPrefix(it.toByteArray()) }
            }

            THORChainSwap.Chain.THOR, THORChainSwap.Chain.ATOM, THORChainSwap.Chain.ETH, THORChainSwap.Chain.BSC, THORChainSwap.Chain.AVAX -> {
                val hashes =
                    TransactionCompiler.preImageHashes(keysignPayload.coin.coinType, inputData)
                val preSigningOutput =
                    wallet.core.jni.proto.TransactionCompiler.PreSigningOutput.parseFrom(hashes)
                return listOf(Numeric.toHexStringNoPrefix(preSigningOutput.dataHash.toByteArray()))
            }

            THORChainSwap.Chain.BNB -> {
                throw Exception("Unsupported chain")
            }


            THORChainSwap.Chain.UNRECOGNIZED -> {
                throw Exception("Unsupported chain")
            }
        }
    }

    fun getPreSignedApproveInputData(
        approvePayload: ERC20ApprovePayload,
        keysignPayload: KeysignPayload,
    ): ByteArray {
        val approveInput = wallet.core.jni.proto.Ethereum.SigningInput.newBuilder().setTransaction(
            wallet.core.jni.proto.Ethereum.Transaction.newBuilder().setErc20Approve(
                wallet.core.jni.proto.Ethereum.Transaction.ERC20Approve.newBuilder().apply {
                    this.spender = approvePayload.spender
                    this.amount = approvePayload.amount.toString(10).toByteString()
                }.build()
            ).build()
        ).build()
        val result = EvmHelper(
            keysignPayload.coin.coinType,
            vaultHexPublicKey,
            vaultHexChainCode
        ).getPreSignedInputData(signingInput = approveInput, keysignPayload = keysignPayload)
        return result
    }

    fun getPreSignedApproveImageHash(
        approvePayload: ERC20ApprovePayload,
        keysignPayload: KeysignPayload,
    ): List<String> {
        val result = getPreSignedApproveInputData(approvePayload, keysignPayload)
        val hashes = TransactionCompiler.preImageHashes(keysignPayload.coin.coinType, result)
        val preSigningOutput =
            wallet.core.jni.proto.TransactionCompiler.PreSigningOutput.parseFrom(hashes)
        return listOf(Numeric.toHexStringNoPrefix(preSigningOutput.dataHash.toByteArray()))
    }

    fun getSignedApproveTransaction(
        approvePayload: ERC20ApprovePayload,
        keysignPayload: KeysignPayload,
        signatures: Map<String, KeysignResponse>,
    ): SignedTransactionResult {
        val inputData = getPreSignedApproveInputData(approvePayload, keysignPayload)
        return EvmHelper(
            keysignPayload.coin.coinType,
            vaultHexPublicKey,
            vaultHexChainCode
        ).getSignedTransaction(inputData, signatures)
    }

    fun getSignedTransaction(
        swapPayload: THORChainSwapPayload,
        keysignPayload: KeysignPayload,
        signatures: Map<String, KeysignResponse>,
    ): SignedTransactionResult {
        val inputData = getPreSignedInputData(swapPayload, keysignPayload)
        when (swapPayload.fromAsset.chain) {
            THORChainSwap.Chain.THOR -> {
                return THORCHainHelper(vaultHexPublicKey, vaultHexChainCode).getSignedTransaction(
                    inputData,
                    signatures
                )
            }

            THORChainSwap.Chain.BTC, THORChainSwap.Chain.DOGE, THORChainSwap.Chain.BCH, THORChainSwap.Chain.LTC -> {
                val helper =
                    utxoHelper(keysignPayload.coin.coinType, vaultHexPublicKey, vaultHexChainCode)
                return helper.getSignedTransaction(inputData, signatures)
            }

            THORChainSwap.Chain.ETH, THORChainSwap.Chain.AVAX, THORChainSwap.Chain.BSC -> {
                val helper =
                    EvmHelper(keysignPayload.coin.coinType, vaultHexPublicKey, vaultHexChainCode)
                return helper.getSignedTransaction(inputData, signatures)
            }

            THORChainSwap.Chain.BNB -> {
                throw Exception("Unsupported chain")
            }

            THORChainSwap.Chain.ATOM -> {
                val helper = AtomHelper(vaultHexPublicKey, vaultHexChainCode)
                return helper.getSignedTransaction(
                    input = inputData,
                    keysignPayload = keysignPayload,
                    signatures = signatures
                )
            }

            THORChainSwap.Chain.UNRECOGNIZED -> TODO()
        }
    }
}