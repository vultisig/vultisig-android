package com.vultisig.wallet.data.chains.helpers

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.EVMSwapPayloadJson
import com.vultisig.wallet.data.models.SignedTransactionResult
import com.vultisig.wallet.data.models.payload.KeysignPayload
import java.util.Base64
import tss.KeysignResponse

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
        val txData = Base64.getDecoder().decode(swapPayload.quote.tx.data)
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
        val txData = Base64.getDecoder().decode(swapPayload.quote.tx.data)
        return SolanaHelper(vaultHexPublicKey)
            .signRawTransaction(
                coinHexPubKey = keysignPayload.coin.hexPublicKey,
                txData = txData,
                signatures = signatures,
            )
    }
}
