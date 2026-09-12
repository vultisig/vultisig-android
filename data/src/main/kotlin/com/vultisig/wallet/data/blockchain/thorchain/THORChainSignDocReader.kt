package com.vultisig.wallet.data.blockchain.thorchain

import com.vultisig.wallet.data.models.transaction_decoding.DecodedAmount
import com.vultisig.wallet.data.models.transaction_decoding.DecodedAsset
import com.vultisig.wallet.data.usecases.ThorChainAsset
import com.vultisig.wallet.data.usecases.ThorMsgDepositBody
import com.vultisig.wallet.data.usecases.TxBody
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.protobuf.ProtoBuf

/**
 * Reads the one THORChain message a dApp-built SignDoc carries a memo in: `/types.MsgDeposit`.
 *
 * THORChain's own messages are not Cosmos SDK ones. A deposit keeps its memo *inside* the message
 * rather than on the `TxBody`, its coin names an `Asset` rather than a denom, and its type URL is
 * one `CosmosSignDocReader` deliberately refuses — so a THORChain body reached no reader at all,
 * and a co-signer shown a dApp's `TCY-:5000` saw whatever screen it already had. This is the reader
 * that gap was missing.
 *
 * The body is decoded through the same serializable models the joining device renders the request
 * from, so the verb read here and the message shown beside it come from one parse of one set of
 * bytes and cannot disagree. The refusals are this reader's own: a body it cannot name completely
 * is refused as a whole, for the reason `CosmosSignDocReader` gives — a confident wrong verb serves
 * a co-signer worse than the screen it already had.
 */
internal object THORChainSignDocReader {

    /** A deposit that read completely: the memo THORNode will execute, and what it carries. */
    data class Deposit(val memo: String, val carried: DecodedAmount)

    /** Refuses a body larger than any real deposit. */
    private const val MAX_BODY_BYTES = 64 * 1024

    private const val MSG_DEPOSIT_TYPE_URL = "/types.MsgDeposit"

    /** THORChain's native chain, as the `Asset` spells it. */
    private const val NATIVE_CHAIN = "THOR"
    private const val NATIVE_TICKER = "RUNE"

    /** THORChain addresses are 20 raw bytes on the wire; anything else is not a signer. */
    private const val ADDRESS_BYTES = 20

    /**
     * Reads one complete deposit, or refuses the body.
     *
     * Exactly one message is accepted. A batch has no single reading, and THORNode executes one
     * memo per deposit anyway; any other message type is not this grammar's to name.
     */
    fun read(body: ByteArray): Deposit? {
        if (body.isEmpty() || body.size > MAX_BODY_BYTES) return null

        val txBody = runCatching { ProtoBuf.Default.decodeFromByteArray<TxBody>(body) }.getOrNull()
        val message = txBody?.messages?.singleOrNull() ?: return null
        if (message.typeUrl != MSG_DEPOSIT_TYPE_URL) return null

        val deposit =
            runCatching { ProtoBuf.Default.decodeFromByteArray<ThorMsgDepositBody>(message.value) }
                .getOrNull() ?: return null
        if (deposit.signer.size != ADDRESS_BYTES) return null

        // An empty memo is a deposit THORNode would reject; there is nothing to read.
        val memo = deposit.memo.takeIf { it.isNotEmpty() } ?: return null

        return Deposit(memo = memo, carried = carried(deposit))
    }

    /**
     * What the deposit carries, in the units it states them.
     *
     * Only a plain THORChain-native asset can be named: RUNE is the chain's own unit, and every
     * other native asset is keyed by the denom its ticker spells. A synth, trade or secured asset
     * is a different denom altogether, and several coins have no single figure — both state no
     * amount rather than a wrong one. A zero is what a share-based memo such as `TCY-` carries, and
     * is not the amount either.
     */
    private fun carried(deposit: ThorMsgDepositBody): DecodedAmount {
        val coin = deposit.coins.singleOrNull() ?: return DecodedAmount.Unstated
        val units = coin.amount.toBigIntegerOrNull() ?: return DecodedAmount.Unstated
        if (units.signum() <= 0) return DecodedAmount.Unstated

        val asset = coin.asset.takeIf { it.isPlainNative() } ?: return DecodedAmount.Unstated
        return DecodedAmount.Units(
            units,
            if (asset.ticker.equals(NATIVE_TICKER, ignoreCase = true)) DecodedAsset.ChainNative
            else DecodedAsset.Denom(asset.ticker.lowercase()),
        )
    }

    private fun ThorChainAsset.isPlainNative(): Boolean =
        chain.equals(NATIVE_CHAIN, ignoreCase = true) &&
            ticker.isNotEmpty() &&
            !synth &&
            !trade &&
            !secured
}
