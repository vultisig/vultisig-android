package com.vultisig.wallet.data.swap.limit

import com.vultisig.wallet.data.db.models.PendingLimitOrderEntity
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.LimitOrderStatus
import java.math.BigInteger

/** Why an order cannot be cancelled from this app. Each case maps to its own user-facing reason. */
enum class LimitOrderCancelBlocker {
    /** The order has already closed. Nothing left to cancel. */
    Terminal,

    /**
     * The order predates the fields cancelling needs, or its source chain was never recorded. Fails
     * closed: better a greyed-out button than a guess at the amounts the matcher keys on.
     */
    MissingSignedData,

    /**
     * The order was placed by a build that recorded neither the source chain nor the sender
     * address, so it can be neither polled (the queue endpoint is scoped by sender) nor cancelled.
     *
     * Distinct from [PlacementNotObserved], and that distinction is the whole point: an unobserved
     * order becomes cancellable on the next poll, and telling one of these to wait for a poll that
     * will never come is a promise the app cannot keep. Nothing is lost — the order still refunds
     * automatically at expiry.
     */
    LegacyOrderNotTrackable,

    /**
     * A cancel for this order has already broadcast and is waiting to be observed in the queue.
     *
     * The order stays NON-terminal on purpose — `cancelling` — because that is what keeps a cancel
     * that silently matched nothing visible rather than papered over. But a live button would let
     * the user pay the fee (and on L1 donate the dust) again for an identical memo landing in the
     * identical ratio bucket. Self-resolving in both directions: the order leaves `cancelling` when
     * it closes, and drops back to `pending` if the cancel record is withdrawn.
     */
    CancelAlreadyBroadcast,

    /** The order was funded from a chain THORChain cannot route, so there is no inbound to use. */
    UnsupportedSourceChain,

    /**
     * The order's own inbound deposit has not been seen resting in THORChain's queue yet.
     *
     * Cancelling now cannot work and can actively do harm. THORChain has not observed the deposit,
     * so there is no resting order for a `m=<` to match — and on a UTXO source the cancel is built
     * from a UTXO set that still lists the inputs the unconfirmed placement is spending, so the two
     * transactions conflict: at an equal fee rate the node rejects the cancel outright
     * (`insufficient fee, rejecting replacement`), and at a higher one it would REPLACE the
     * placement, destroying the order by a completely different mechanism than the user asked for.
     *
     * Self-resolving: the first queue poll that sees the order resting clears this.
     */
    PlacementNotObserved,

    /**
     * The cancel memo does not fit the source chain's per-transaction budget — in practice an ERC20
     * target from a UTXO source, where two full contract-suffixed assets plus two exact amounts
     * overflow the 80-byte `OP_RETURN` cap. Nothing in a cancel memo can be shortened. The order
     * still refunds automatically at expiry.
     */
    MemoTooLongForSourceChain,

    /**
     * What was recorded at signing and what the queue reports disagree. One of the two is wrong and
     * there is no way to tell which; signing either would be a guess whose failure mode is silent.
     */
    SignedDataDisagreesWithChain,
}

/** Whether an order can be cancelled, and if so with exactly which inputs. */
sealed interface LimitOrderCancelEligibility {
    data class Cancellable(val inputs: LimitSwapCancelMemo.Inputs, val memo: String) :
        LimitOrderCancelEligibility

    data class Blocked(val blocker: LimitOrderCancelBlocker) : LimitOrderCancelEligibility

    val isCancellable: Boolean
        get() = this is Cancellable
}

/** Which spelling of one of the order's assets a cancel memo may use, or why there isn't one. */
internal sealed interface CancelAssetResolution {
    data class Resolved(val asset: String) : CancelAssetResolution

    /** What we hold and what the chain reports are not the same asset. */
    data object Disagrees : CancelAssetResolution

    /** No source can supply a full spelling. */
    data object Unknown : CancelAssetResolution
}

/**
 * Which spelling of an asset a cancel memo may use.
 *
 * Three sources, in decreasing order of how much they prove:
 * 1. **The queue's own report** ([observed]) — the string THORChain built this order's index entry
 *    from, after fuzzy matching resolved whatever the placement memo abbreviated. Authoritative by
 *    construction, and the only source for an order placed before the full form was recorded.
 * 2. **The full form captured at signing** ([signed]) — derived from the coin's own contract
 *    address, so it is exact whenever it exists.
 * 3. **The stored placement spelling** ([stored]) — usable ONLY when it carries no truncated token
 *    identifier, which makes it full by construction. That covers every native leg (`BTC.BTC`,
 *    `THOR.RUNE`) and every secured denom.
 *
 * [CancelAssetResolution.Disagrees] when a local spelling and the chain's own differ: one of the
 * two is wrong and there is no way to tell which, so neither is signed.
 *
 * Compared case-insensitively because case carries no meaning here and the two sources disagree on
 * it by convention — this app emits a secured denom lower-case, THORChain reports it upper-case,
 * and `common.ParseCoin` upper-cases whatever it is given. Anything beyond case is a real
 * difference.
 */
internal fun resolveCancelMemoAsset(
    stored: String,
    signed: String?,
    observed: String?,
): CancelAssetResolution {
    val local =
        signed?.trim()?.takeIf { it.isNotEmpty() }
            ?: stored.trim().takeIf { it.isNotEmpty() && !LimitSwapCancelMemo.isAbbreviated(it) }
    val chainReported = observed?.trim()?.takeIf { it.isNotEmpty() }

    if (chainReported == null) {
        return local?.let(CancelAssetResolution::Resolved) ?: CancelAssetResolution.Unknown
    }
    // No local spelling to check it against — the legacy EVM-token case, rescued by the only source
    // that still holds the full contract.
    if (local == null) return CancelAssetResolution.Resolved(chainReported)
    if (!local.equals(chainReported, ignoreCase = true)) return CancelAssetResolution.Disagrees
    // Proven equal bar case, so the local spelling is kept: it is the exact byte form this app
    // derived and its tests pin.
    return CancelAssetResolution.Resolved(local)
}

/**
 * Decide whether [order] describes something this app can cancel, and if so with which exact
 * amounts and memo.
 *
 * **Fails closed at every unknown.** The failure this guards against is not a crash or an error
 * dialog — it is a cancel that is accepted by the chain, costs a fee, and silently matches no order
 * at all. Every branch that cannot prove the amounts are the ones THORChain holds returns
 * [LimitOrderCancelEligibility.Blocked].
 */
fun limitOrderCancelEligibility(order: PendingLimitOrderEntity): LimitOrderCancelEligibility {
    if (LimitOrderStatus.fromRaw(order.status).isTerminal) {
        return LimitOrderCancelEligibility.Blocked(LimitOrderCancelBlocker.Terminal)
    }
    if (order.cancelBroadcastHash != null) {
        return LimitOrderCancelEligibility.Blocked(LimitOrderCancelBlocker.CancelAlreadyBroadcast)
    }
    // Checked BEFORE the resting gate below, because an order missing either of these is never
    // polled at all: the queue endpoint is scoped by sender, so it can never become observed and
    // the "wait for the deposit to confirm" reason would sit on the card for the order's whole
    // life. Both columns arrived with the tracking work, so this is exactly the set of orders
    // placed by an earlier build.
    if (order.sourceChain == null || order.sourceAddress.isNullOrBlank()) {
        return LimitOrderCancelEligibility.Blocked(LimitOrderCancelBlocker.LegacyOrderNotTrackable)
    }

    // The source chain is recorded explicitly at placement because `sourceAsset` cannot stand in
    // for
    // it: a SECURED asset source is THORChain-placed but its memo asset is a bare denom with no
    // `THOR.` prefix, while a `THOR.`-prefixed string says nothing about where the deposit came
    // from.
    val sourceChain =
        Chain.entries.firstOrNull { it.raw == order.sourceChain }
            ?: return LimitOrderCancelEligibility.Blocked(LimitOrderCancelBlocker.MissingSignedData)

    // Both routes are supported: a THORChain-sourced order cancels via MsgDeposit from the vault's
    // THOR address, and an L1-sourced order cancels by sending the same memo from the chain that
    // funded it — THORNode dispatches `m=<` from the Bifrost observed-tx path too. What matters is
    // only that THORChain can route the source chain at all. Ahead of the resting gate, because an
    // unroutable source has no cancel route however well observed the order is.
    if (!isThorchainRoutable(sourceChain)) {
        return LimitOrderCancelEligibility.Blocked(LimitOrderCancelBlocker.UnsupportedSourceChain)
    }

    // The order has to be RESTING before it can be cancelled — see [PlacementNotObserved]. Any
    // field the queue writes proves it was there; `expiryObservedAt` is the one written on every
    // resting poll, so it is the honest witness rather than a field that only some responses carry.
    if (order.expiryObservedAt == null && order.depositAmount == null) {
        return LimitOrderCancelEligibility.Blocked(LimitOrderCancelBlocker.PlacementNotObserved)
    }

    val signedSourceAmount = order.sourceAmount1e8?.toBigIntegerOrNull()
    val signedTradeTarget = order.tradeTarget?.toBigIntegerOrNull()
    if (
        signedSourceAmount == null ||
            signedTradeTarget == null ||
            signedSourceAmount.signum() <= 0 ||
            signedTradeTarget.signum() <= 0
    ) {
        return LimitOrderCancelEligibility.Blocked(LimitOrderCancelBlocker.MissingSignedData)
    }

    // Cross-check against what THORChain itself reports, when it has reported anything.
    // `state.deposit` IS the swap's `Tx.Coins[0].Amount` and `trade_target` IS `msg.TradeTarget` —
    // the exact pair the matcher's ratio is computed from. Absence is NOT disagreement: an order
    // placed seconds ago has not been polled yet, and refusing to cancel it until the first poll
    // lands would be a worse failure than the one this check prevents. An observation that is
    // PRESENT but unparseable blocks exactly as a mismatch does: that means the wire carried
    // something this code does not model, and proceeding would sign amounts we failed to verify.
    if (!agreesWithChain(order.depositAmount, signedSourceAmount)) {
        return LimitOrderCancelEligibility.Blocked(
            LimitOrderCancelBlocker.SignedDataDisagreesWithChain
        )
    }
    if (!agreesWithChain(order.observedTradeTarget, signedTradeTarget)) {
        return LimitOrderCancelEligibility.Blocked(
            LimitOrderCancelBlocker.SignedDataDisagreesWithChain
        )
    }

    // The ASSETS get the same treatment as the amounts. The stored spellings are the PLACEMENT
    // strings and are lossy — a 6-character contract suffix cannot be expanded back — so they are
    // usable only when they carry no truncated identifier at all.
    val sourceResolution =
        resolveCancelMemoAsset(order.sourceAsset, order.sourceAssetFull, order.observedSourceAsset)
    val targetResolution =
        resolveCancelMemoAsset(order.targetAsset, order.targetAssetFull, order.observedTargetAsset)
    if (
        sourceResolution !is CancelAssetResolution.Resolved ||
            targetResolution !is CancelAssetResolution.Resolved
    ) {
        val disagrees =
            sourceResolution is CancelAssetResolution.Disagrees ||
                targetResolution is CancelAssetResolution.Disagrees
        return LimitOrderCancelEligibility.Blocked(
            if (disagrees) LimitOrderCancelBlocker.SignedDataDisagreesWithChain
            else LimitOrderCancelBlocker.MissingSignedData
        )
    }

    val inputs =
        LimitSwapCancelMemo.Inputs(
            sourceAsset = sourceResolution.asset,
            sourceAmount1e8 = signedSourceAmount,
            targetAsset = targetResolution.asset,
            tradeTarget = signedTradeTarget,
        )

    // The memo has to be buildable AND fit the chain it will be sent from. Checked here rather than
    // at signing so the button is never offered for an order that cannot actually be cancelled.
    val memo =
        try {
            LimitSwapCancelMemo.build(inputs)
        } catch (_: IllegalArgumentException) {
            return LimitOrderCancelEligibility.Blocked(LimitOrderCancelBlocker.MissingSignedData)
        }
    if (!LimitSwapCancelMemo.memoFits(memo, sourceChain)) {
        return LimitOrderCancelEligibility.Blocked(
            LimitOrderCancelBlocker.MemoTooLongForSourceChain
        )
    }

    return LimitOrderCancelEligibility.Cancellable(inputs, memo)
}

/** True when the chain has said nothing, or has said exactly what we recorded. */
private fun agreesWithChain(observed: String?, signed: BigInteger): Boolean {
    if (observed == null) return true
    return observed.trim().toBigIntegerOrNull() == signed
}

/**
 * The order a just-broadcast cancel memo addresses, or null when none of [orders] matches.
 *
 * Matched on the memo itself rather than on an id threaded through the signing flow, because the
 * memo IS the addressing: THORChain resolves a cancel by the assets and amounts it spells, so an
 * order whose memo differs by a byte is a different order however the app labelled it.
 *
 * Ties go to the OLDEST, mirroring THORNode: two orders that produce an identical memo are
 * indistinguishable on-chain and it modifies the first match in the bucket.
 * `duplicateRestingLimitOrders` is what warns the user before they get here.
 */
fun findOrderAddressedByCancelMemo(
    memo: String,
    orders: List<PendingLimitOrderEntity>,
): PendingLimitOrderEntity? =
    orders
        .sortedBy { it.createdAt }
        .firstOrNull { order ->
            when (val eligibility = limitOrderCancelEligibility(order)) {
                // Compares the memo an ELIGIBLE order would be cancelled with. An order that has
                // just had its own cancel recorded is blocked by `CancelAlreadyBroadcast` and so
                // cannot absorb a second cancel's hash.
                is LimitOrderCancelEligibility.Cancellable -> eligibility.memo == memo
                is LimitOrderCancelEligibility.Blocked -> false
            }
        }

/**
 * Other RESTING orders the same cancel memo would also address.
 *
 * THORNode addresses orders by (layer-1 source asset, layer-1 target asset, ratio) plus the sender
 * — never by tx hash — and takes the FIRST match in the bucket. So two orders that reduce to the
 * same inputs are not independently cancellable, and the user has to be told that the one they
 * tapped may not be the one that closes.
 *
 * Compared on THORNode's actual bucket key, NOT on equal amounts: two orders with different
 * deposits and different trade targets collide whenever their ratio is the same (selling 1 and
 * selling 2 at the same price land in one bucket). Comparing amounts for equality would silently
 * under-report exactly the duplicates the user most needs warning about.
 */
fun duplicateRestingLimitOrders(
    target: PendingLimitOrderEntity,
    among: List<PendingLimitOrderEntity>,
): List<PendingLimitOrderEntity> {
    val targetEligibility = limitOrderCancelEligibility(target)
    if (targetEligibility !is LimitOrderCancelEligibility.Cancellable) return emptyList()
    val targetKey = LimitSwapCancelMemo.bucketKey(targetEligibility.inputs)
    return among
        .filter { it.inboundTxHash != target.inboundTxHash }
        .filter { candidate ->
            val eligibility = limitOrderCancelEligibility(candidate)
            eligibility is LimitOrderCancelEligibility.Cancellable &&
                LimitSwapCancelMemo.bucketKey(eligibility.inputs) == targetKey
        }
        .sortedBy { it.createdAt }
}
