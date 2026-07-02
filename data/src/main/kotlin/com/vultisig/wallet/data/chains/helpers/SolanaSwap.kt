package com.vultisig.wallet.data.chains.helpers

import com.vultisig.wallet.data.crypto.checkError
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.EVMSwapPayloadJson
import com.vultisig.wallet.data.models.SignedTransactionResult
import com.vultisig.wallet.data.models.payload.KeysignPayload
import tss.KeysignResponse
import wallet.core.jni.Base64
import wallet.core.jni.CoinType.SOLANA
import wallet.core.jni.TransactionDecoder
import wallet.core.jni.proto.Solana

class SolanaSwap(private val vaultHexPublicKey: String) {

    /**
     * Computes the pre-image hash(es) for a Solana aggregator (e.g. Jupiter) swap transaction.
     *
     * The provider delivers a fully-built wire transaction; its message is extracted and signed
     * verbatim (matching iOS), rather than re-serializing through WalletCore's compiler, which
     * duplicates address-table accounts on v0 transactions and yields `AccountLoadedTwice`.
     *
     * @param swapPayload the swap payload carrying the provider's raw transaction
     * @param keysignPayload the keysign payload (must be on the Solana chain)
     * @return the message bytes to sign, hex-encoded without a `0x` prefix
     */
    fun getPreSignedImageHash(
        swapPayload: EVMSwapPayloadJson,
        keysignPayload: KeysignPayload,
    ): List<String> {
        require(keysignPayload.coin.chain == Chain.Solana) { "Chain is not Solana" }
        val txData = Base64.decode(swapPayload.quote.tx.data)
        return SolanaHelper(vaultHexPublicKey).getPreSignedImageHashForRaw(txData)
    }

    /**
     * Assembles the broadcastable transaction for a Solana aggregator swap by splicing the TSS
     * signature into the provider's original bytes verbatim (see [getPreSignedImageHash]).
     *
     * @param swapPayload the swap payload carrying the provider's raw transaction
     * @param keysignPayload the keysign payload (must be on the Solana chain)
     * @param signatures TSS signatures keyed by the hex-encoded message hash
     * @return the Base58-encoded signed transaction and its transaction hash
     */
    fun getSignedTransaction(
        swapPayload: EVMSwapPayloadJson,
        keysignPayload: KeysignPayload,
        signatures: Map<String, KeysignResponse>,
    ): SignedTransactionResult {
        require(keysignPayload.coin.chain == Chain.Solana) { "Chain is not Solana" }
        val txData = Base64.decode(swapPayload.quote.tx.data)
        return SolanaHelper(vaultHexPublicKey)
            .signRawTransaction(
                coinHexPubKey = keysignPayload.coin.hexPublicKey,
                txData = txData,
                signatures = signatures,
            )
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
         * keys plus every writable/readonly index across its address-table lookups; a legacy
         * message has no lookups, so it locks only its static account keys.
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
