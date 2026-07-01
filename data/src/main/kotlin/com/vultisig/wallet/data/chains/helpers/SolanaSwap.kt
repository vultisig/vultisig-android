package com.vultisig.wallet.data.chains.helpers

import com.vultisig.wallet.data.api.models.quotes.EVMSwapQuoteJson
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

class SolanaSwap(private val vaultHexPublicKey: String) {

    fun getPreSignedImageHash(
        swapPayload: EVMSwapPayloadJson,
        keysignPayload: KeysignPayload,
    ): List<String> {
        val inputData = getPreSignedInputData(swapPayload.quote, keysignPayload)
        val chain = swapPayload.fromCoin.chain
        val coinType = keysignPayload.coin.coinType
        return Swaps.getPreSignedImageHash(inputData, coinType, chain)
    }

    fun getSignedTransaction(
        swapPayload: EVMSwapPayloadJson,
        keysignPayload: KeysignPayload,
        signatures: Map<String, KeysignResponse>,
    ): SignedTransactionResult {

        val inputData = getPreSignedInputData(swapPayload.quote, keysignPayload)
        val helper = SolanaHelper(vaultHexPublicKey)
        return helper.getSwapSignedTransaction(inputData, signatures)
    }

    private fun getPreSignedInputData(
        quote: EVMSwapQuoteJson,
        keysignPayload: KeysignPayload,
    ): ByteArray {
        if (keysignPayload.coin.chain != Chain.Solana) error("Chain is not Solana")

        val solanaSpecific =
            keysignPayload.blockChainSpecific as? BlockChainSpecific.Solana
                ?: error("Invalid blockChainSpecific")

        val recentBlockHash = solanaSpecific.recentBlockHash
        val updatedTxData = Base64.decode(quote.tx.data)

        val decodedData = TransactionDecoder.decode(SOLANA, updatedTxData)
        val decodedOutput = Solana.DecodingTransactionOutput.parseFrom(decodedData).checkError()

        val rawMessage =
            when {
                decodedOutput.transaction.hasLegacy() -> {
                    decodedOutput.transaction
                        .toBuilder()
                        .apply {
                            legacy =
                                decodedOutput.transaction.legacy
                                    .toBuilder()
                                    .setRecentBlockhash(recentBlockHash)
                                    .build()
                        }
                        .build()
                }

                decodedOutput.transaction.hasV0() -> {
                    decodedOutput.transaction
                        .toBuilder()
                        .apply {
                            v0 =
                                decodedOutput.transaction.v0
                                    .toBuilder()
                                    .setRecentBlockhash(recentBlockHash)
                                    .build()
                        }
                        .build()
                }

                else -> error("Can't decode swap transact")
            }

        return SigningInput.newBuilder().setRawMessage(rawMessage).build().toByteArray()
    }

    companion object {
        /**
         * Solana's runtime cap on the number of distinct accounts a single transaction may lock. A
         * transaction locking more than this fails sanitization at the leader with
         * `TooManyAccountLocks` before it is ever simulated, so it can never land — no provider
         * reports it in a `simulationError` field.
         */
        const val MAX_TX_ACCOUNT_LOCKS = 64

        /**
         * Decodes a built Solana swap transaction ([transactionData], base64) and returns how many
         * accounts it locks: the static message keys plus every writable and readonly
         * address-table-lookup reference. Provider-agnostic — it inspects the decoded transaction,
         * not any aggregator-specific field — so it back-stops Jupiter, LiFi/Titan, and any other
         * aggregator that builds the tx.
         *
         * @param transactionData the base64-encoded transaction as returned by the aggregator.
         * @return the total account-lock count; compare against [MAX_TX_ACCOUNT_LOCKS].
         */
        fun countAccountLocks(transactionData: String): Int {
            val decodedData = TransactionDecoder.decode(SOLANA, Base64.decode(transactionData))
            val decodedOutput = Solana.DecodingTransactionOutput.parseFrom(decodedData).checkError()
            return countAccountLocks(decodedOutput.transaction)
        }

        /**
         * Counts the accounts a decoded [rawMessage] locks. A v0 message locks its static account
         * keys plus every writable/readonly index across its address-table lookups; a legacy message
         * has no lookups, so it locks only its static account keys.
         */
        fun countAccountLocks(rawMessage: Solana.RawMessage): Int =
            when {
                rawMessage.hasV0() -> {
                    val v0 = rawMessage.v0
                    v0.accountKeysCount +
                        v0.addressTableLookupsList.sumOf {
                            it.writableIndexesCount + it.readonlyIndexesCount
                        }
                }

                rawMessage.hasLegacy() -> rawMessage.legacy.accountKeysCount

                else -> error("Can't decode swap transaction")
            }
    }
}
