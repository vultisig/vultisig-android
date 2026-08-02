package com.vultisig.wallet.ui.models.limitorder

import androidx.compose.runtime.Immutable
import com.vultisig.wallet.R
import com.vultisig.wallet.data.db.models.PendingLimitOrderEntity
import com.vultisig.wallet.data.models.LimitOrderStatus
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.nativeToken
import com.vultisig.wallet.data.swap.limit.LimitOrderCancelBlocker
import com.vultisig.wallet.data.swap.limit.LimitOrderCancelEligibility
import com.vultisig.wallet.data.swap.limit.duplicateRestingLimitOrders
import com.vultisig.wallet.data.swap.limit.limitOrderCancelEligibility
import com.vultisig.wallet.data.swap.limit.thorchainAssetPrefixToChain
import com.vultisig.wallet.ui.models.mappers.TokenValueToDecimalUiStringMapper
import com.vultisig.wallet.ui.utils.UiText
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject
import kotlin.math.roundToInt

/** How a limit order's state reads on a card. */
enum class LimitOrderHistoryStatus {
    /** Resting in the queue, waiting on its price. */
    Resting,

    /**
     * A cancel has broadcast and the order has not yet left the queue. Deliberately styled as
     * in-flight rather than as success: THORChain accepts a cancel that matches nothing, and the
     * order is still live until the queue says otherwise.
     */
    Cancelling,
    Filled,
    Expired,
    Cancelled,
    Refunded,
}

@Immutable
data class LimitOrderHistoryUiModel(
    /** The inbound deposit's tx hash — the only identity THORChain exposes for a resting order. */
    val id: String,
    val sellTicker: String,
    val buyTicker: String,
    /**
     * Deposited amount in the source coin's natural units, or null when the order's precision was
     * never recorded and cannot be recovered. Null renders as no amount at all — an order whose
     * scale is unknown says `BTC → ETH`, never `50000000 BTC`.
     */
    val sellAmount: String?,
    /** Buy-asset units per 1 sell unit, as the memo's LIM encodes it. */
    val targetPrice: String,
    val status: LimitOrderHistoryStatus,
    val createdAt: Long,
    /** Time left before the order expires, or null until the queue has been polled once. */
    val expiry: UiText? = null,
    /** How much of the order has been swapped so far, when partially filled. */
    val fillPercent: Int? = null,
    val isCancellable: Boolean = false,
    /** Why the Cancel button is unavailable. Null when it is available or the order is closed. */
    val cancelBlockedReason: UiText? = null,
    /**
     * True when another resting order would be addressed by the identical cancel memo. THORChain
     * modifies the FIRST match in the bucket, so the order the user taps may not be the one that
     * closes — and they have to be told before they sign.
     */
    val hasCancelDuplicate: Boolean = false,
)

/**
 * Turns the stored orders into what the Limit Orders tab renders.
 *
 * Every derived value comes from the same record the cancel path reads, so a card can never claim
 * an order is cancellable on grounds the cancel builder would then reject.
 */
internal class LimitOrderToUiModelMapper
@Inject
constructor(private val mapTokenValueToDecimalUiString: TokenValueToDecimalUiStringMapper) {

    fun map(orders: List<PendingLimitOrderEntity>, nowMs: Long): List<LimitOrderHistoryUiModel> =
        orders.map { order -> map(order, orders, nowMs) }

    private fun map(
        order: PendingLimitOrderEntity,
        all: List<PendingLimitOrderEntity>,
        nowMs: Long,
    ): LimitOrderHistoryUiModel {
        val status = LimitOrderStatus.fromRaw(order.status)
        val eligibility = limitOrderCancelEligibility(order)
        val isCancellable = eligibility is LimitOrderCancelEligibility.Cancellable

        return LimitOrderHistoryUiModel(
            id = order.inboundTxHash,
            sellTicker = order.sourceTicker ?: order.sourceAsset.tickerFromMemoAsset(),
            buyTicker = order.targetTicker ?: order.targetAsset.tickerFromMemoAsset(),
            sellAmount = formatSourceAmount(order),
            targetPrice = order.targetPrice,
            status = status.toUiModel(),
            createdAt = order.createdAt,
            expiry = order.expiryText(nowMs),
            fillPercent = order.fillPercent(),
            isCancellable = isCancellable,
            cancelBlockedReason =
                (eligibility as? LimitOrderCancelEligibility.Blocked)
                    ?.blocker
                    ?.takeUnless { it == LimitOrderCancelBlocker.Terminal }
                    ?.toUiText(),
            hasCancelDuplicate =
                isCancellable && duplicateRestingLimitOrders(order, all).isNotEmpty(),
        )
    }

    /**
     * The deposit in the source coin's natural units, or null when its precision is unknowable.
     *
     * `source_decimals` arrived with the tracking work, so an order placed by an earlier build has
     * none — and [PendingLimitOrderEntity.sourceAmount] is in the coin's SMALLEST units, which
     * printed unscaled turns half a bitcoin into "50000000 BTC" beside a correct target price.
     * Where the placement memo names a chain's own native asset the precision is still recoverable
     * from [Chain.nativeToken]; a token leg is not, and is rendered without an amount rather than
     * with a wrong one.
     */
    private fun formatSourceAmount(order: PendingLimitOrderEntity): String? {
        val amount = order.sourceAmount.toBigIntegerOrNull() ?: return null
        val decimals = order.sourceDecimals ?: order.sourceAsset.nativeDecimalsFromMemoAsset()
        if (decimals == null) return null
        return mapTokenValueToDecimalUiString(
            TokenValue(value = amount, unit = order.sourceTicker.orEmpty(), decimals = decimals)
        )
    }
}

/**
 * Precision of the chain's NATIVE coin when a memo asset names it — `BTC.BTC` → 8, `THOR.RUNE` → 8.
 *
 * Null for anything else: a token leg (`ETH.USDC-0X…`) carries its own precision, which the memo
 * does not spell, and a bare denom says nothing about the chain it came from.
 */
private fun String.nativeDecimalsFromMemoAsset(): Int? {
    val chainEnd = indexOfFirst { it == '.' }
    if (chainEnd < 0) return null
    val chain = thorchainAssetPrefixToChain[substring(0, chainEnd).uppercase()] ?: return null
    val nativeToken = chain.nativeToken
    val symbol = substring(chainEnd + 1)
    return if (symbol.equals(nativeToken.ticker, ignoreCase = true)) nativeToken.decimal else null
}

/**
 * Ticker recovered from a THORChain memo asset — `ETH.USDC-0X…` → `USDC`, `btc-btc` → `BTC`.
 *
 * A fallback only, for orders placed before the ticker was recorded alongside them. The memo asset
 * is what those rows have, and a chain-qualified string reads worse on a card than the ticker it
 * contains.
 */
private fun String.tickerFromMemoAsset(): String {
    val separators = setOf('.', '/', '~', '-')
    val chainEnd = indexOfFirst { it in separators }
    val symbol = if (chainEnd >= 0) substring(chainEnd + 1) else this
    return symbol.substringBefore('-').uppercase()
}

private fun LimitOrderStatus.toUiModel(): LimitOrderHistoryStatus =
    when (this) {
        LimitOrderStatus.Pending -> LimitOrderHistoryStatus.Resting
        LimitOrderStatus.Cancelling -> LimitOrderHistoryStatus.Cancelling
        LimitOrderStatus.Filled -> LimitOrderHistoryStatus.Filled
        LimitOrderStatus.Expired -> LimitOrderHistoryStatus.Expired
        LimitOrderStatus.Cancelled -> LimitOrderHistoryStatus.Cancelled
        LimitOrderStatus.Refunded -> LimitOrderHistoryStatus.Refunded
    }

private fun LimitOrderCancelBlocker.toUiText(): UiText =
    UiText.StringResource(
        when (this) {
            LimitOrderCancelBlocker.Terminal -> R.string.limit_order_cancel_blocked_closed
            LimitOrderCancelBlocker.MissingSignedData ->
                R.string.limit_order_cancel_blocked_missing_data
            LimitOrderCancelBlocker.CancelAlreadyBroadcast ->
                R.string.limit_order_cancel_blocked_already_sent
            LimitOrderCancelBlocker.UnsupportedSourceChain ->
                R.string.limit_order_cancel_blocked_unsupported_chain
            LimitOrderCancelBlocker.LegacyOrderNotTrackable ->
                R.string.limit_order_cancel_blocked_legacy
            LimitOrderCancelBlocker.PlacementNotObserved ->
                R.string.limit_order_cancel_blocked_not_resting_yet
            LimitOrderCancelBlocker.MemoTooLongForSourceChain ->
                R.string.limit_order_cancel_blocked_memo_too_long
            LimitOrderCancelBlocker.SignedDataDisagreesWithChain ->
                R.string.limit_order_cancel_blocked_disagrees
        }
    )

/**
 * Time left before the order expires, counted from the queue's own block countdown and the moment
 * it was observed.
 *
 * Deliberately not derived from `created_at` + TTL, which would assume the deposit was queued the
 * instant it was signed. Null until the first poll — an unknown countdown is left blank rather than
 * shown as zero, which would read as "expired" on an order that is resting fine.
 */
private fun PendingLimitOrderEntity.expiryText(nowMs: Long): UiText? {
    if (LimitOrderStatus.fromRaw(status).isTerminal) return null
    val blocks = timeToExpiryBlocks ?: return null
    val observedAt = expiryObservedAt ?: return null
    val remainingMs = blocks.toLong() * THORCHAIN_BLOCK_MS - (nowMs - observedAt)
    if (remainingMs <= 0) return UiText.StringResource(R.string.limit_order_expiry_imminent)
    val totalMinutes = remainingMs / 60_000L
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) {
        UiText.FormattedText(R.string.limit_order_expires_in_hours, listOf(hours, minutes))
    } else {
        UiText.FormattedText(R.string.limit_order_expires_in_minutes, listOf(minutes))
    }
}

/**
 * How much of the deposit has been swapped, as a whole percentage — or null when nothing has been
 * observed or nothing has filled.
 *
 * An order fills via streaming sub-swaps, so a partial fill is a real, stable state and the split
 * is what explains a two-leg settlement: part paid out in the target asset, the remainder refunded.
 *
 * Suppressed for a FILLED order, and only for that one. Its stored split is the last RESTING
 * observation, taken before the fill that closed it, and `recordStatus` does not revise the fill
 * columns — so the card would contradict itself, reading "Filled" beside "Filled 40%". Every other
 * terminal keeps it: an order that expired 40% filled paid out that part and refunded the rest, and
 * that split is the only explanation the user gets for a two-leg settlement.
 */
private fun PendingLimitOrderEntity.fillPercent(): Int? {
    if (LimitOrderStatus.fromRaw(status) == LimitOrderStatus.Filled) return null
    val deposit = depositAmount?.toBigIntegerOrNull()?.takeIf { it.signum() > 0 } ?: return null
    val filled = filledInAmount?.toBigIntegerOrNull()?.takeIf { it.signum() > 0 } ?: return null
    if (filled >= deposit) return 100
    return BigDecimal(filled)
        .divide(BigDecimal(deposit), 4, RoundingMode.DOWN)
        .toFloat()
        .times(100)
        .roundToInt()
        .coerceIn(0, 100)
}

private const val THORCHAIN_BLOCK_MS = 6_000L
