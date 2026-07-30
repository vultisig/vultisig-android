package com.vultisig.wallet.data.models

/**
 * Lifecycle of a THORChain limit (`=<`) order as this device understands it.
 *
 * Persisted as a raw string and read back through [fromRaw], which falls back to [Pending]. That
 * fallback is what makes adding a case here a non-breaking change: a build that predates a new
 * status reads it as resting, non-terminal and still polled, rather than as an unknown terminal
 * state it would never revisit.
 */
enum class LimitOrderStatus(val raw: String) {
    /** Resting in THORChain's advanced swap queue, waiting on its price. */
    Pending("pending"),

    /**
     * A cancel transaction for this order has BROADCAST — a confirmed, non-empty hash — and the
     * order has not yet left the queue.
     *
     * **Not terminal, and not a claim about the order.** It describes OUR transaction, the one
     * thing actually confirmed, never the order's fate. THORChain accepts a cancel that addresses
     * the wrong ratio bucket, charges for it, and closes nothing; the order is still resting and
     * can still fill. So an order in this state keeps its place in the resting list, keeps its
     * expiry countdown, and keeps being polled. It must never be styled as terminal or as success —
     * the moment it reads as "done" it reintroduces the false success this whole feature exists to
     * prevent, just sourced from our own optimism instead of the chain's.
     */
    Cancelling("cancelling"),

    /** The order executed. Never relabelled afterwards. */
    Filled("filled"),

    /**
     * The order closed and the funds came back — the observable fact, with no cause attached.
     *
     * Distinct from [Expired] and [Cancelled], which are claims about WHY. THORChain normally
     * answers that question (Midgard's refund action carries `limit swap expired` / `limit swap
     * cancelled` verbatim), so this is what is left when it does not. An order rejected at
     * placement (halted pool, bad memo) also refunds, seconds in, with no TTL elapsed — so
     * inventing a cause here would be inventing it for that user too.
     */
    Refunded("refunded"),

    /**
     * The order's TTL elapsed — THORChain's own account of the closure, taken from the refund
     * action's reason. Never inferred: a client cannot corroborate an expiry on its own, since a
     * closure is only ever observed somewhere inside the window between two polls and a TTL falling
     * inside that window is indistinguishable from a cancellation.
     */
    Expired("expired"),

    /**
     * A cancel matched the order and closed it — again THORChain's own account. Independent of
     * whether THIS device sent the cancel: an order cancelled from another device or another wallet
     * lands here too, because the label describes the order, not our bookkeeping.
     */
    Cancelled("cancelled");

    /** Terminal statuses are never revisited, so nothing may write one on a guess. */
    val isTerminal: Boolean
        get() = this == Filled || this == Refunded || this == Expired || this == Cancelled

    companion object {
        fun fromRaw(raw: String?): LimitOrderStatus =
            entries.firstOrNull { it.raw == raw } ?: Pending
    }
}
