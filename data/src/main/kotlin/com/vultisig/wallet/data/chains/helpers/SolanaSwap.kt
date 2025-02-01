package com.vultisig.wallet.data.chains.helpers

import com.vultisig.wallet.data.api.models.OneInchSwapQuoteJson
import com.vultisig.wallet.data.crypto.checkError
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.OneInchSwapPayloadJson
import com.vultisig.wallet.data.models.SignedTransactionResult
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.wallet.Swaps
import tss.KeysignResponse
import wallet.core.jni.Base64
import wallet.core.jni.TransactionDecoder
import wallet.core.jni.proto.Solana
import wallet.core.jni.CoinType.SOLANA

class SolanaSwap(
    private val vaultHexPublicKey: String,
) {

    fun getPreSignedImageHash(
        swapPayload: OneInchSwapPayloadJson,
        keysignPayload: KeysignPayload,
    ): List<String> {
        val inputData = getPreSignedInputData(swapPayload.quote, keysignPayload)
        val chain = swapPayload.fromCoin.chain
        val coinType = keysignPayload.coin.coinType
        return Swaps.getPreSignedImageHash(inputData, coinType, chain)
    }

    fun getSignedTransaction(
        swapPayload: OneInchSwapPayloadJson,
        keysignPayload: KeysignPayload,
        signatures: Map<String, KeysignResponse>,
    ): SignedTransactionResult {

        val inputData =
            getPreSignedInputData(swapPayload.quote, keysignPayload)
        val helper = SolanaHelper(vaultHexPublicKey)
        return helper.getSwapSignedTransaction(inputData, signatures)
    }


    private fun getPreSignedInputData(
        quote: OneInchSwapQuoteJson,
        keysignPayload: KeysignPayload,
    ): ByteArray {

        val solanaSpecific = keysignPayload.blockChainSpecific as? BlockChainSpecific.Solana
            ?: error("Invalid blockChainSpecific")
        if (keysignPayload.coin.chain != Chain.Solana)
            error("Chain is not Solana")

        val updatedTxData = Base64.decode(quote.tx.data)
        val decodedData = TransactionDecoder.decode(SOLANA, updatedTxData)

        val decodedOutput = Solana.DecodingTransactionOutput.parseFrom(decodedData).checkError()
        val input = Solana.SigningInput.newBuilder()
            .setRawMessage(decodedOutput.transaction)

        return input.build().toByteArray()
    }
}