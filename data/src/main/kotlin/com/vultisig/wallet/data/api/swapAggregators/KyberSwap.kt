package com.vultisig.wallet.data.api.swapAggregators

import com.google.protobuf.ByteString
import com.vultisig.wallet.data.api.models.quotes.KyberSwapQuoteJson
import com.vultisig.wallet.data.chains.helpers.EthereumGasHelper
import com.vultisig.wallet.data.chains.helpers.EvmHelper
import com.vultisig.wallet.data.common.toByteStringOrHex
import com.vultisig.wallet.data.models.SignedTransactionResult
import com.vultisig.wallet.data.models.payload.ERC20ApprovePayload
import com.vultisig.wallet.data.models.payload.KeysignPayload
import tss.KeysignResponse
import wallet.core.jni.proto.Ethereum
import java.math.BigInteger
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.payload.KyberSwapPayloadJson
import com.vultisig.wallet.data.wallet.Swaps

class KyberSwap(
    private val vaultHexPublicKey: String,
    private val vaultHexChainCode: String,
) {

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

        return Swaps.getPreSignedImageHash(
            inputData,
            coinType,
            chain
        )
    }

    fun getSignedTransaction(
        swapPayload: KyberSwapPayloadJson, keysignPayload: KeysignPayload,
        signatures: Map<String, KeysignResponse>, nonceIncrement: BigInteger
    ): SignedTransactionResult {
        val inputData = getPreSignedInputData(
            swapPayload.quote,
            keysignPayload,
            nonceIncrement
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

    fun getPreSignedApproveInputData(
        approvePayload: ERC20ApprovePayload, keysignPayload: KeysignPayload
    ): ByteArray {
        val approveInput = Ethereum.SigningInput.newBuilder()
            .setToAddress(keysignPayload.coin.contractAddress).setTransaction(
                Ethereum.Transaction.newBuilder().setErc20Approve(
                    Ethereum.Transaction.ERC20Approve.newBuilder().setAmount(
                        ByteString.copyFrom(
                            approvePayload.amount.toByteArray()
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
    }

    fun getPreSignedInputData(
        quote: KyberSwapQuoteJson, keysignPayload: KeysignPayload, nonceIncrement: BigInteger
    ): ByteArray {
        val input = Ethereum.SigningInput.newBuilder()

            .setToAddress(quote.tx.to).setTransaction(
                Ethereum.Transaction.newBuilder().setContractGeneric(
                    Ethereum.Transaction.ContractGeneric.newBuilder().setAmount(
                        ByteString.copyFrom(
                            quote.tx.value.toBigInteger().toByteArray()
                                ?: BigInteger.ZERO.toByteArray()
                        )
                    ).setData(quote.tx.data.removePrefix("0x").toByteStringOrHex())
                ).build()
            )
        var gasPrice = quote.tx.gasPrice.toBigIntegerOrNull() ?: BigInteger.ZERO
        if (keysignPayload.coin.chain == Chain.Arbitrum) {
            // set gasPrice to null/zero for envelope transaction on arbitrum chain
            gasPrice = BigInteger.ZERO
        }
        val gas = (quote.tx.gas.takeIf { it != 0L }
            ?: EvmHelper.Companion.DEFAULT_ETH_SWAP_GAS_UNIT).toBigInteger()

        return EthereumGasHelper.setGasParameters(
            gas = gas,
            gasPrice = gasPrice,
            signingInput = input,
            keysignPayload = keysignPayload,
            nonceIncrement = nonceIncrement,
            coinType = keysignPayload.coin.coinType
        ).build().toByteArray()
    }
}