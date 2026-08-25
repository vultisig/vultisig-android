package com.vultisig.wallet.data.models.transaction_decoding

import com.vultisig.wallet.data.models.Chain

/**
 * A reader for one chain family's signed grammar. `handles == null` requires the reader to
 * establish its own provenance from the transaction itself.
 */
interface TransactionContentDecoder {

    /**
     * The chains this decoder's grammar is meaningful on, or `null` for one that establishes its
     * own provenance from the transaction.
     */
    val handles: Set<Chain>?

    fun decode(tx: SignedTransactionContent): DecodedTransaction?
}

/**
 * Reads operations from content that will actually be signed. The foundation registers no chain
 * readers, so every transaction remains unreadable.
 */
object SignedTransactionDecoder {

    /** Registered readers in precedence order. Empty in the foundation. */
    private val decoders: MutableList<TransactionContentDecoder> = mutableListOf()

    /** Register a decoder to the registry. */
    fun register(decoder: TransactionContentDecoder) {
        decoders.add(decoder)
    }

    /** Unregister a decoder from the registry. */
    fun unregister(decoder: TransactionContentDecoder) {
        decoders.remove(decoder)
    }

    /** Get all registered decoders. */
    fun getDecoders(): List<TransactionContentDecoder> = decoders.toList()

    /** Clear all registered decoders. */
    fun clear() {
        decoders.clear()
    }

    /** Returns `.unknown` when no reader can prove an operation. */
    fun decode(tx: SignedTransactionContent): DecodedTransaction {
        for (decoder in decoders) {
            val handles = decoder.handles
            if (handles == null || handles.contains(tx.chain)) {
                decoder.decode(tx)?.let {
                    return it
                }
            }
        }

        return DecodedTransaction.unreadable
    }
}
