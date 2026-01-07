package com.vultisig.wallet.data.chains.helpers

import com.vultisig.wallet.data.crypto.checkError
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.EVMSwapPayloadJson
import com.vultisig.wallet.data.models.SignedTransactionResult
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.wallet.Swaps
import tss.KeysignResponse
import wallet.core.jni.Base64
import wallet.core.jni.CoinType.SOLANA
import wallet.core.jni.TransactionDecoder
import wallet.core.jni.proto.Solana
import wallet.core.jni.proto.Solana.SigningInput

class SolanaSwap(
    private val vaultHexPublicKey: String,
) {

    fun getPreSignedImageHash(
        swapPayloadData: EVMSwapPayloadJson,
        keysignPayload: KeysignPayload,
    ): List<String> {
        val inputData = getPreSignedInputData(swapPayloadData.quote.tx.data, keysignPayload)
        val chain = swapPayloadData.fromCoin.chain
        val coinType = keysignPayload.coin.coinType
        return Swaps.getPreSignedImageHash(inputData, coinType, chain)
    }

    fun getSignedTransaction(
        swapQuoteTxData: String,
        keysignPayload: KeysignPayload,
        signatures: Map<String, KeysignResponse>,
    ): SignedTransactionResult {

        val inputData =
            getPreSignedInputData(swapQuoteTxData, keysignPayload)
        val helper = SolanaHelper(vaultHexPublicKey)
        return helper.getSwapSignedTransaction(inputData, signatures)
    }

    private fun getPreSignedInputData(
        swapQuoteTxData: String,
        keysignPayload: KeysignPayload,
    ): ByteArray {
        if (keysignPayload.coin.chain != Chain.Solana)
            error("Chain is not Solana")

        val solanaSpecific = keysignPayload.blockChainSpecific as? BlockChainSpecific.Solana
            ?: error("Invalid blockChainSpecific")

        val recentBlockHash = solanaSpecific.recentBlockHash
        val updatedTxData = Base64.decode(swapQuoteTxData)

        val decodedData = TransactionDecoder.decode(SOLANA, updatedTxData)
        val decodedOutput = Solana.DecodingTransactionOutput.parseFrom(decodedData).checkError()

        val rawMessage = when {
            decodedOutput.transaction.hasLegacy() -> {
                decodedOutput.transaction.toBuilder().apply {
                    legacy = decodedOutput.transaction.legacy
                        .toBuilder()
                        .setRecentBlockhash(recentBlockHash)
                        .build()
                }.build()
            }

            decodedOutput.transaction.hasV0() -> {
                decodedOutput.transaction.toBuilder().apply {
                    v0 = decodedOutput.transaction.v0
                        .toBuilder()
                        .setRecentBlockhash(recentBlockHash)
                        .build()
                }.build()
            }

            else -> error("Can't decode swap transact")
        }

        return SigningInput.newBuilder().setRawMessage(rawMessage).build().toByteArray()
    }
}