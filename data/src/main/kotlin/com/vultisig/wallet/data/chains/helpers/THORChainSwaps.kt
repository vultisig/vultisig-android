package com.vultisig.wallet.data.chains.helpers

import com.google.protobuf.ByteString
import com.vultisig.wallet.data.crypto.ThorChainHelper
import com.vultisig.wallet.data.models.SignedTransactionResult
import com.vultisig.wallet.data.models.THORChainSwapPayload
import com.vultisig.wallet.data.models.payload.ERC20ApprovePayload
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.wallet.Swaps
import tss.KeysignResponse
import wallet.core.jni.CoinType
import wallet.core.jni.TransactionCompiler
import wallet.core.jni.proto.Ethereum.SigningInput
import wallet.core.jni.proto.Ethereum.Transaction
import wallet.core.jni.proto.THORChainSwap
import java.math.BigInteger

class THORChainSwaps(
    private val vaultHexPublicKey: String,
    private val vaultHexChainCode: String,
) {
    companion object {
        const val AFFILIATE_FEE_ADDRESS = "va"
        const val AFFILIATE_FEE_RATE = "50" // 50 BP
        const val TOLERANCE_BPS = "50"
    }

    private fun getPreSignedInputData(
        swapPayload: THORChainSwapPayload,
        keysignPayload: KeysignPayload,
        nonceIncrement: BigInteger,
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
                    .setInterval(swapPayload.streamingInterval)
                    .setQuantity(swapPayload.streamingQuantity)
            ).let {
                if (swapPayload.isAffiliate) {
                    it.setAffiliateFeeAddress(AFFILIATE_FEE_ADDRESS)
                        .setAffiliateFeeRateBp(AFFILIATE_FEE_RATE)
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
                return THORCHainHelper()
                    .getSwapPreSignedInputData(
                        keysignPayload,
                        output.cosmos.toBuilder()
                    )
            }

            THORChainSwap.Chain.BTC, THORChainSwap.Chain.LTC, THORChainSwap.Chain.DOGE, THORChainSwap.Chain.BCH -> {
                val helper =
                    UtxoHelper(keysignPayload.coin.coinType, vaultHexPublicKey, vaultHexChainCode)
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
                    nonceIncrement = nonceIncrement,
                )
            }

            THORChainSwap.Chain.ATOM -> {
                val helper = CosmosHelper(
                    coinType = CoinType.COSMOS,
                    denom = CosmosHelper.ATOM_DENOM,
                )
                return helper.getSwapPreSignedInputData(
                    keysignPayload = keysignPayload,
                    input = output.cosmos.toBuilder()
                )
            }


            THORChainSwap.Chain.BNB, THORChainSwap.Chain.UNRECOGNIZED, null -> {
                throw Exception("Unsupported chain")
            }
        }
    }

    fun getPreSignedImageHash(
        swapPayload: THORChainSwapPayload,
        keysignPayload: KeysignPayload,
        nonceIncrement: BigInteger,
    ): List<String> {
        val inputData = getPreSignedInputData(swapPayload, keysignPayload, nonceIncrement)

        val chain = swapPayload.fromCoin.chain
        val coinType = keysignPayload.coin.coinType

        return Swaps.getPreSignedImageHash(inputData, coinType, chain)
    }

    private fun getPreSigningOutput(coinType: CoinType, inputData: ByteArray): List<String> =
        Swaps.getPreSigningOutput(
            preImageHashes = TransactionCompiler.preImageHashes(coinType, inputData)
        )

    private fun getPreSignedApproveInputData(
        approvePayload: ERC20ApprovePayload,
        keysignPayload: KeysignPayload,
    ): ByteArray {
        val approveInput = SigningInput.newBuilder()
            .setTransaction(
                Transaction.newBuilder()
                    .setErc20Approve(
                        Transaction.ERC20Approve.newBuilder()
                            .setSpender(approvePayload.spender)
                            .setAmount(ByteString.copyFrom(approvePayload.amount.toByteArray()))
                    )
            )
            .setToAddress(keysignPayload.coin.contractAddress)
            .build()

        return EvmHelper(
            keysignPayload.coin.coinType,
            vaultHexPublicKey,
            vaultHexChainCode
        ).getPreSignedInputData(signingInput = approveInput, keysignPayload = keysignPayload)
    }

    fun getPreSignedApproveImageHash(
        approvePayload: ERC20ApprovePayload,
        keysignPayload: KeysignPayload,
    ): List<String> = getPreSigningOutput(
        coinType = keysignPayload.coin.coinType,
        inputData = getPreSignedApproveInputData(approvePayload, keysignPayload)
    )

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
        nonceIncrement: BigInteger,
    ): SignedTransactionResult {
        val inputData = getPreSignedInputData(swapPayload, keysignPayload, nonceIncrement)
        when (swapPayload.fromAsset.chain) {
            THORChainSwap.Chain.THOR -> {
                return ThorChainHelper.thor(vaultHexPublicKey, vaultHexChainCode)
                    .getSignedTransaction(
                        inputData,
                        signatures
                    )
            }

            THORChainSwap.Chain.BTC, THORChainSwap.Chain.DOGE, THORChainSwap.Chain.BCH, THORChainSwap.Chain.LTC -> {
                val helper =
                    UtxoHelper(keysignPayload.coin.coinType, vaultHexPublicKey, vaultHexChainCode)
                return helper.getSignedTransaction(inputData, signatures)
            }

            THORChainSwap.Chain.ETH, THORChainSwap.Chain.AVAX, THORChainSwap.Chain.BSC -> {
                val helper =
                    EvmHelper(keysignPayload.coin.coinType, vaultHexPublicKey, vaultHexChainCode)
                return helper.getSignedTransaction(inputData, signatures)
            }

            THORChainSwap.Chain.ATOM -> {
                val helper = CosmosHelper(
                    coinType = CoinType.COSMOS,
                    denom = CosmosHelper.ATOM_DENOM,
                )
                return helper.getSignedTransaction(
                    input = inputData,
                    keysignPayload = keysignPayload,
                    signatures = signatures
                )
            }

            THORChainSwap.Chain.UNRECOGNIZED, THORChainSwap.Chain.BNB, null -> {
                throw Exception("Unsupported chain")
            }

        }
    }
}