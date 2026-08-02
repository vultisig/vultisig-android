package com.vultisig.wallet.data.db.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A THORChain limit order the vault has placed, recorded locally on successful broadcast and kept
 * up to date by polling THORChain's advanced swap queue. Keyed by the inbound deposit tx hash,
 * which is the only identity THORChain exposes for a resting order.
 *
 * All amounts and prices are stored as strings to avoid precision loss.
 *
 * The columns below the placement block are nullable on purpose: they are either observations that
 * have not happened yet, or fields added after the first orders were already stored. A null there
 * means "unknown", never zero — and for the cancel inputs specifically, unknown means the order is
 * not cancellable rather than cancellable with guessed values.
 */
@Entity(tableName = "pending_limit_order")
data class PendingLimitOrderEntity(
    @PrimaryKey @ColumnInfo(name = "inbound_tx_hash") val inboundTxHash: String,
    @ColumnInfo(name = "vault_id") val vaultId: String,
    /** Source asset as the PLACEMENT memo spelled it — an EVM token is abbreviated here. */
    @ColumnInfo(name = "source_asset") val sourceAsset: String,
    /** Deposited amount in the source coin's own smallest units. */
    @ColumnInfo(name = "source_amount") val sourceAmount: String,
    @ColumnInfo(name = "target_asset") val targetAsset: String,
    @ColumnInfo(name = "dest_addr") val destAddr: String,
    /** Target price as buy-asset units per 1 sell-asset unit. */
    @ColumnInfo(name = "target_price") val targetPrice: String,
    @ColumnInfo(name = "expiry_blocks") val expiryBlocks: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "status") val status: String,

    // --- Identity needed to track and cancel the order --------------------------------------

    /**
     * [com.vultisig.wallet.data.models.Chain.raw] of the coin the order was funded with.
     *
     * [sourceAsset] cannot stand in for it: a THORChain SECURED asset source is THORChain-placed
     * yet carries a bare denom with no `THOR.` prefix, and a `THOR.`-prefixed string says nothing
     * about where the deposit came from. Null means the order predates this column and is treated
     * as not cancellable.
     */
    @ColumnInfo(name = "source_chain") val sourceChain: String? = null,
    /** Precision of the source coin, needed to render the deposited amount in natural units. */
    @ColumnInfo(name = "source_decimals") val sourceDecimals: Int? = null,
    /**
     * The address the deposit was sent FROM. THORChain's queue endpoint is scoped by sender, so
     * without this there is nothing to poll.
     */
    @ColumnInfo(name = "source_address") val sourceAddress: String? = null,
    /** Ticker of the source coin, for display. Derived from the coin, not from the memo asset. */
    @ColumnInfo(name = "source_ticker") val sourceTicker: String? = null,
    /** Ticker of the target coin, for display. */
    @ColumnInfo(name = "target_ticker") val targetTicker: String? = null,

    // --- The exact pair a CANCEL memo must reproduce, captured at signing -------------------

    /**
     * THORChain addresses a resting order by a bucket key derived from `(sourceAmount × 1e8) /
     * tradeTarget`, so a cancel must reproduce both integers exactly or it lands in a different
     * bucket and silently matches nothing. Neither is recoverable after the fact: [sourceAmount] is
     * in the source coin's NATIVE decimals, and the effective LIM exists only in the placement
     * memo. Null keeps the order uncancellable rather than guessed at.
     */
    @ColumnInfo(name = "source_amount_1e8") val sourceAmount1e8: String? = null,
    @ColumnInfo(name = "trade_target") val tradeTarget: String? = null,
    /**
     * The order's assets spelled the way a CANCEL memo must spell them: EVM tokens with their FULL
     * contract address. [sourceAsset] / [targetAsset] hold the placement spelling, whose
     * abbreviation is not reversible — so the long form has to be recorded while the contract
     * address is still in hand. See [com.vultisig.wallet.data.swap.limit.thorchainCancelMemoAsset].
     */
    @ColumnInfo(name = "source_asset_full") val sourceAssetFull: String? = null,
    @ColumnInfo(name = "target_asset_full") val targetAssetFull: String? = null,

    // --- What THORChain itself reports, for cross-checking ----------------------------------

    /**
     * The queue's own `swap.trade_target` and resolved assets — i.e. AFTER fuzzy matching expanded
     * whatever the placement memo abbreviated. Authoritative by construction: these are the strings
     * the order's index entry was built from. They both rescue orders placed before the `_full`
     * columns existed and cross-check the ones that have them. A disagreement disables cancelling
     * rather than signing a guess.
     */
    @ColumnInfo(name = "observed_trade_target") val observedTradeTarget: String? = null,
    @ColumnInfo(name = "observed_source_asset") val observedSourceAsset: String? = null,
    @ColumnInfo(name = "observed_target_asset") val observedTargetAsset: String? = null,

    // --- Fill accounting, in 1e8 fixed point ------------------------------------------------

    /**
     * The order's fill accounting as last observed on-chain. Persisted rather than read live
     * because a terminal order DISAPPEARS from the queue, taking its fill state with it — without a
     * stored copy, an order that expired 40% filled could never say so again, and that split is
     * exactly what explains a two-leg settlement (part paid out, the remainder refunded).
     *
     * `deposit` is what went in, `in` how much has been swapped, `out` what has been paid out. Null
     * means never observed, which is not the same as zero.
     */
    @ColumnInfo(name = "deposit_amount") val depositAmount: String? = null,
    @ColumnInfo(name = "filled_in_amount") val filledInAmount: String? = null,
    @ColumnInfo(name = "filled_out_amount") val filledOutAmount: String? = null,

    // --- Expiry countdown --------------------------------------------------------------------

    /**
     * Blocks left before expiry as the queue last reported them, and WHEN it reported them. Both
     * are needed together: a block count alone is a number with no meaning once a minute has
     * passed. Anchoring it to the observation time is what lets the expiry label tick between
     * polls, and is more honest than deriving it from `created_at` + TTL, which would assume the
     * deposit was queued the instant it was signed and that blocks are exactly 6s.
     */
    @ColumnInfo(name = "time_to_expiry_blocks") val timeToExpiryBlocks: Int? = null,
    @ColumnInfo(name = "expiry_observed_at") val expiryObservedAt: Long? = null,

    // --- Cancel bookkeeping -------------------------------------------------------------------

    /**
     * Hash of the `m=<` transaction broadcast to cancel this order.
     *
     * **An INTENT record, not a terminal outcome.** On broadcast the order moves to the
     * NON-terminal [com.vultisig.wallet.data.models.LimitOrderStatus.Cancelling] — a statement
     * about our transaction — never straight to `cancelled`. A cancel that addresses the wrong
     * ratio bucket is accepted by the chain, costs a fee, and cancels nothing; labelling the ORDER
     * cancelled on the strength of this hash would render that failure invisible. The terminal
     * label comes from THORChain's own reason via Midgard.
     */
    @ColumnInfo(name = "cancel_broadcast_hash") val cancelBroadcastHash: String? = null,
    /**
     * Whether the recorded cancel has been CONFIRMED on its own chain, as opposed to merely
     * broadcast. Gates the "closed with no reason we recognise, and the TTL demonstrably had not
     * elapsed" fallback that credits a closure to our cancel: a broadcast the chain later refuses
     * must never let an unrelated refund be relabelled "Cancelled".
     */
    @ColumnInfo(name = "cancel_confirmed") val cancelConfirmed: Boolean = false,
)
