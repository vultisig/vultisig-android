package com.vultisig.wallet.data.api.models.thorchain

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Wire models for THORNode's advanced-swap-queue route, `/thorchain/queue/limit_swaps?sender=`.
 *
 * Two shapes worth knowing before reading:
 * - the route returns an OBJECT (`{"limit_swaps":[…]}`), not a bare array;
 * - every numeric field arrives as a STRING, including the fill amounts. They are 1e8 fixed-point
 *   integers, kept as strings here so the caller widens them deliberately.
 */
@Serializable
data class ThorchainLimitSwapQueueResponse(
    /**
     * Null when the `limit_swaps` key was ABSENT — which is not the same as an empty queue, and
     * must never be flattened into one.
     *
     * An order's DISAPPEARANCE from this list is what marks it terminal, so "the queue is empty" is
     * a load-bearing claim: if an unrecognised 200 envelope silently decoded as "no resting
     * orders", every tracked order would be closed at once on the strength of a response we did not
     * actually understand. Callers must treat null as "no information" and leave orders resting;
     * only an explicit `[]` means the sender has none.
     */
    @SerialName("limit_swaps") val limitSwaps: List<ThorchainLimitSwapQueueEntry>? = null
)

@Serializable
data class ThorchainLimitSwapQueueEntry(
    /** Blocks remaining before the order expires. THORChain blocks are ~6s. */
    @SerialName("time_to_expiry_blocks") val timeToExpiryBlocks: String? = null,
    /**
     * Blocks elapsed since the order was placed. Preferred over `created_timestamp`, which THORNode
     * hardcodes to `0`.
     */
    @SerialName("blocks_since_created") val blocksSinceCreated: String? = null,
    @SerialName("swap") val swap: ThorchainQueuedSwap,
)

@Serializable
data class ThorchainQueuedSwap(
    @SerialName("tx") val tx: ThorchainQueuedSwapTx,
    @SerialName("state") val state: ThorchainQueuedSwapState? = null,
    /**
     * The order's TARGET asset as THORChain itself holds it — i.e. AFTER fuzzy matching expanded
     * whatever abbreviation the placement memo carried. The only place the full identifier can be
     * read back for an order placed before the app recorded it.
     */
    @SerialName("target_asset") val targetAsset: ThorchainWireAsset? = null,
    /**
     * The order's trade target (the LIM its placement memo encoded), in the target asset's 1e8
     * fixed point — `MsgSwap.TradeTarget` verbatim. Read for one reason: it is half of the pair
     * THORChain addresses a resting order by, so it cross-checks the value recorded at signing
     * before a cancel is built from it.
     */
    @SerialName("trade_target") val tradeTarget: String? = null,
)

@Serializable
data class ThorchainQueuedSwapTx(
    /** The original inbound tx hash — the identity orders are matched on. */
    @SerialName("id") val id: String,
    @SerialName("from_address") val fromAddress: String? = null,
    @SerialName("memo") val memo: String? = null,
    /**
     * What was actually deposited, as THORChain resolved it. `coins[0]` is the swap's source coin —
     * `state.deposit` is this entry's amount, assigned verbatim — so `coins[0].asset` is the SOURCE
     * half of the pair a cancel memo has to name in full.
     */
    @SerialName("coins") val coins: List<ThorchainQueuedCoin>? = null,
)

@Serializable
data class ThorchainQueuedCoin(
    @SerialName("asset") val asset: ThorchainWireAsset? = null,
    @SerialName("amount") val amount: String? = null,
)

/**
 * The order's fill accounting, in 1e8 fixed-point source/target units.
 *
 * `deposit` is what went in; `in` is how much of it has been swapped so far; `out` is what has been
 * paid out. An order fills via streaming sub-swaps, so `0 < in < deposit` is a real, stable state.
 */
@Serializable
data class ThorchainQueuedSwapState(
    @SerialName("deposit") val deposit: String? = null,
    @SerialName("in") val inAmount: String? = null,
    @SerialName("out") val outAmount: String? = null,
    /**
     * Present on orders that TRIED to execute and missed. This does NOT mean the order failed — it
     * is still resting — so it is never surfaced as an error.
     */
    @SerialName("failed_swap_reasons") val failedSwapReasons: List<String>? = null,
)

/**
 * A `common.Asset` off the wire, reduced to the string a memo spells it with.
 *
 * Decodes BOTH shapes deliberately. THORNode's queriers render assets through `Asset.MarshalJSON`,
 * i.e. as the flat string `ETH.USDC-0XA0B8…`; the same message marshalled by protobuf-JSON comes
 * out as an object of its chain/symbol/flags fields. Which one a given route uses is a property of
 * that route's marshaller, not of the type — and this is the only reader of an asset whose exact
 * spelling is then SIGNED, so accepting both costs a few lines while guessing wrong costs a cancel
 * that silently matches nothing.
 */
@Serializable(with = ThorchainWireAssetSerializer::class)
data class ThorchainWireAsset(
    /**
     * The asset as a memo spells it — `THOR.RUNE`, `ETH.USDC-0XA0B8…`, `eth-usdc-0x…` (secured).
     */
    val memoForm: String
)

internal object ThorchainWireAssetSerializer : KSerializer<ThorchainWireAsset> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("ThorchainWireAsset", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): ThorchainWireAsset {
        val input = decoder as? JsonDecoder ?: return ThorchainWireAsset(decoder.decodeString())
        val element = input.decodeJsonElement()
        if (element is JsonPrimitive && element.isString) {
            return ThorchainWireAsset(element.content)
        }
        val obj = element as? JsonObject ?: return ThorchainWireAsset("")
        val chain = obj["chain"]?.jsonPrimitive?.content.orEmpty()
        val symbol = obj["symbol"]?.jsonPrimitive?.content.orEmpty()
        // Mirrors `common.Asset.String()`: one separator per flavour, and the layer-1 `.` when none
        // of the flags is set.
        val separator =
            when {
                obj["synth"]?.jsonPrimitive?.booleanOrNull == true -> "/"
                obj["trade"]?.jsonPrimitive?.booleanOrNull == true -> "~"
                obj["secured"]?.jsonPrimitive?.booleanOrNull == true -> "-"
                else -> "."
            }
        return ThorchainWireAsset(if (chain.isEmpty()) symbol else "$chain$separator$symbol")
    }

    override fun serialize(encoder: Encoder, value: ThorchainWireAsset) {
        encoder.encodeString(value.memoForm)
    }
}
