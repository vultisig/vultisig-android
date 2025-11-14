package com.vultisig.wallet.data.api.swapAggregators

import com.vultisig.wallet.data.api.models.quotes.EVMSwapQuoteJson
import com.vultisig.wallet.data.chains.helpers.EthereumGasHelper
import com.vultisig.wallet.data.chains.helpers.EthereumGasHelper.requireEthereumSpec
import com.vultisig.wallet.data.chains.helpers.EvmHelper
import com.vultisig.wallet.data.common.toByteString
import com.vultisig.wallet.data.common.toHexBytesInByteString
import com.vultisig.wallet.data.models.EVMSwapPayloadJson
import com.vultisig.wallet.data.models.SignedTransactionResult
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.wallet.Swaps
import tss.KeysignResponse
import wallet.core.jni.proto.Ethereum.SigningInput
import wallet.core.jni.proto.Ethereum.Transaction
import java.math.BigInteger

class OneInchSwap(
    private val vaultHexPublicKey: String,
    private val vaultHexChainCode: String,
) {

    fun getPreSignedImageHash(
        swapPayload: EVMSwapPayloadJson,
        keysignPayload: KeysignPayload,
        nonceIncrement: BigInteger,
    ): List<String> {
        val inputData = getPreSignedInputData(swapPayload.quote, keysignPayload, nonceIncrement)

        val chain = swapPayload.fromCoin.chain
        val coinType = keysignPayload.coin.coinType

        return Swaps.getPreSignedImageHash(inputData, coinType, chain)
    }

    fun getSignedTransaction(
        swapPayload: EVMSwapPayloadJson,
        keysignPayload: KeysignPayload,
        signatures: Map<String, KeysignResponse>,
        nonceIncrement: BigInteger,
    ): SignedTransactionResult {
        val inputData = getPreSignedInputData(swapPayload.quote, keysignPayload, nonceIncrement)
        val helper =
            EvmHelper(keysignPayload.coin.coinType, vaultHexPublicKey, vaultHexChainCode)
        return helper.getSignedTransaction(inputData, signatures)
    }

    private fun getPreSignedInputData(
        quote: EVMSwapQuoteJson,
        keysignPayload: KeysignPayload,
        nonceIncrement: BigInteger,
    ): ByteArray {
        val input = SigningInput.newBuilder()
            .setToAddress(quote.tx.to)
            .setTransaction(
                Transaction.newBuilder()
                    .setContractGeneric(
                        Transaction.ContractGeneric.newBuilder()
                            .setAmount(
                                quote.tx.value.toBigIntegerOrNull()?.toByteArray()?.toByteString()
                                    ?: BigInteger.ZERO.toByteArray().toByteString()
                            )
                            .setData(quote.tx.data.removePrefix("0x").toHexBytesInByteString())
                    )
            )

        val gasPrice = maxOf(quote.tx.gasPrice.toBigIntegerOrNull() ?: BigInteger.ZERO,
            requireEthereumSpec(keysignPayload.blockChainSpecific).maxFeePerGasWei)
        val gas = maxOf(
            (quote.tx.gas.takeIf { it != 0L } ?: EvmHelper.DEFAULT_ETH_SWAP_GAS_UNIT).toBigInteger(),
            requireEthereumSpec(keysignPayload.blockChainSpecific).gasLimit
        )
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