package com.vultisig.wallet.data.chains.helpers

import java.math.BigDecimal
import java.math.BigInteger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import timber.log.Timber

/**
 * `tfPartialPayment` (`0x00020000`). On a `Payment` it turns `Amount` from a guaranteed delivery
 * into a ceiling: the ledger decides the delivered value at execution, and without a `DeliverMin`
 * floor that value can be dust while the sender still spends up to `SendMax`. Other transaction
 * types reuse the same bit for unrelated meanings (`tfImmediateOrCancel` on an `OfferCreate`), so
 * it is only ever read for a `Payment`.
 */
internal const val TF_PARTIAL_PAYMENT = 0x00020000L

/**
 * The XRPL `Flags` bitfield as a uint32, or null when it is absent, explicitly null, or not a plain
 * integer. Callers that gate signing on a flag must treat null as "cannot rule the bit out" rather
 * than as "no flags set".
 */
internal fun JsonObject.flagsOrNull(): Long? =
    (this["Flags"] as? JsonPrimitive)?.contentOrNull?.toLongOrNull()

/**
 * The value of [key] when it is a non-blank JSON *string*, else null. XRPL encodes every amount as
 * a string, and a blank one names nothing: it must read the same as an absent field so a floor the
 * verify screen would not render cannot pass validation.
 */
private fun JsonObject.stringOrNull(key: String): String? =
    (this[key] as? JsonPrimitive)
        ?.takeIf { it.isString }
        ?.content
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

/**
 * True when [key] carries a floor a co-signer can actually rely on: a well-formed, strictly
 * positive XRPL amount. Native amounts are an integer count of drops; issued amounts are an object
 * carrying `currency`, `issuer` and a decimal `value`, all string-encoded.
 *
 * Anything else is no floor at all, and is deliberately not distinguished from an absent field: a
 * `DeliverMin` of `"0"`, `"-1"`, `"abc"` or `{"value":"25"}` with no issuer would read as a bound
 * on the verify screen while guaranteeing nothing.
 *
 * Kept in step with `isPositiveRippleAmount` in the extension's `sanitizeRippleDappTx`, which is
 * the initiator-side half of the same defense.
 */
internal fun JsonObject.hasPositiveAmount(key: String): Boolean =
    when (val element = this[key]) {
        null,
        is JsonNull -> false
        is JsonPrimitive -> {
            val drops = element.takeIf { it.isString }?.content?.trim()?.toBigIntegerOrNull()
            drops != null && drops > BigInteger.ZERO
        }
        is JsonObject -> {
            val isIssuedAmount =
                element.stringOrNull("currency") != null && element.stringOrNull("issuer") != null
            val value = element.stringOrNull("value")?.toBigDecimalOrNull()
            isIssuedAmount && value != null && value > BigDecimal.ZERO
        }
        else -> false
    }

/**
 * Semantic identifier of a decoded XRPL field row. The data layer stores this key (never an English
 * display label) so the Compose layer can map it to a localized string. Distinct issuer keys make
 * clear which amount an `Issuer` row belongs to (e.g. [SELLING_ISSUER] vs [BUYING_ISSUER]).
 */
enum class RippleDappTxFieldKey {
    TYPE,
    FROM,
    TO,
    DESTINATION_TAG,
    AMOUNT,
    AMOUNT_ISSUER,
    SEND_MAX,
    SEND_MAX_ISSUER,
    DELIVER_MIN,
    DELIVER_MIN_ISSUER,
    SELLING,
    SELLING_ISSUER,
    BUYING,
    BUYING_ISSUER,
    LIMIT,
    LIMIT_ISSUER,
    FLAGS,
    PATHS,
    FEE,
}

/** A single decoded key/value row of a dApp-supplied XRPL transaction, for the verify screen. */
data class RippleDappTxField(val key: RippleDappTxFieldKey, val value: String)

/**
 * Human-readable decode of a dApp-supplied XRPL transaction ([SignRipple.rawJson]).
 *
 * [fields] is empty when the JSON can't be decoded into known terms — the verify screen then falls
 * back to showing [rawJson] verbatim so a co-signer is never left with a blank/misleading screen.
 *
 * [isPartialPayment] drives the verify screen's warning: when it is set, the `Amount` row is a
 * ceiling rather than the value that will actually land.
 */
data class RippleDappTx(
    val transactionType: String?,
    val fields: List<RippleDappTxField>,
    val rawJson: String,
    val isPartialPayment: Boolean = false,
) {
    /** Value of the decoded field with [key], or null if absent. */
    fun value(key: RippleDappTxFieldKey): String? = fields.firstOrNull { it.key == key }?.value
}

/**
 * Decodes the raw XRPL transaction JSON a dApp hands the co-signer (via `SignRipple`) into readable
 * terms — type, source, destination, amounts (`Amount` / `SendMax` / `DeliverMin` / `TakerGets` /
 * `TakerPays`), issuer, destination tag and the signed `Fee`. Pure (no JNI, no Android), so the
 * verify screen renders decoded terms and it stays unit-testable.
 *
 * Never throws: on any parse failure it returns an empty [RippleDappTx.fields] carrying the
 * original [rawJson], so the UI can fall back to the raw JSON.
 */
object RippleDappTransactionDecoder {

    private val json = Json { ignoreUnknownKeys = true }

    // XRPL native amounts are integer drops; 1 XRP = 1_000_000 drops.
    private val DROPS_PER_XRP = BigDecimal(1_000_000)

    fun decode(rawJson: String): RippleDappTx {
        val obj =
            try {
                json.parseToJsonElement(rawJson).jsonObject
            } catch (e: Exception) {
                Timber.w("Failed to decode SignRipple rawJson for display: %s", e.message)
                return RippleDappTx(transactionType = null, fields = emptyList(), rawJson = rawJson)
            }

        val transactionType = obj.stringOrNull("TransactionType")
        val flags = obj.flagsOrNull()
        val isPartialPayment =
            transactionType == "Payment" && flags != null && (flags and TF_PARTIAL_PAYMENT) != 0L
        val fields = buildList {
            transactionType?.let { add(RippleDappTxField(RippleDappTxFieldKey.TYPE, it)) }
            obj.stringOrNull("Account")?.let {
                add(RippleDappTxField(RippleDappTxFieldKey.FROM, it))
            }
            obj.stringOrNull("Destination")?.let {
                add(RippleDappTxField(RippleDappTxFieldKey.TO, it))
            }
            obj.stringOrNull("DestinationTag")?.let {
                add(RippleDappTxField(RippleDappTxFieldKey.DESTINATION_TAG, it))
            }
            addAmount(
                key = "Amount",
                amountKey = RippleDappTxFieldKey.AMOUNT,
                issuerKey = RippleDappTxFieldKey.AMOUNT_ISSUER,
                obj = obj,
            )
            addAmount(
                key = "SendMax",
                amountKey = RippleDappTxFieldKey.SEND_MAX,
                issuerKey = RippleDappTxFieldKey.SEND_MAX_ISSUER,
                obj = obj,
            )
            addAmount(
                key = "DeliverMin",
                amountKey = RippleDappTxFieldKey.DELIVER_MIN,
                issuerKey = RippleDappTxFieldKey.DELIVER_MIN_ISSUER,
                obj = obj,
            )
            // OfferCreate: TakerGets is what the account sells, TakerPays what it buys. Match the
            // extension's Selling / Buying wording rather than the raw XRPL field names.
            addAmount(
                key = "TakerGets",
                amountKey = RippleDappTxFieldKey.SELLING,
                issuerKey = RippleDappTxFieldKey.SELLING_ISSUER,
                obj = obj,
            )
            addAmount(
                key = "TakerPays",
                amountKey = RippleDappTxFieldKey.BUYING,
                issuerKey = RippleDappTxFieldKey.BUYING_ISSUER,
                obj = obj,
            )
            // TrustSet's LimitAmount is an issued-currency object; handle it the same way.
            addAmount(
                key = "LimitAmount",
                amountKey = RippleDappTxFieldKey.LIMIT,
                issuerKey = RippleDappTxFieldKey.LIMIT_ISSUER,
                obj = obj,
            )
            // Flags and Paths change how a Payment settles — tfPartialPayment makes Amount a
            // ceiling, Paths steers the route — so they get their own rows instead of being left
            // for a co-signer to spot inside the raw JSON blob.
            flags
                ?.takeIf { it != 0L }
                ?.let { add(RippleDappTxField(RippleDappTxFieldKey.FLAGS, it.toString())) }
            (obj["Paths"] as? JsonArray)
                ?.takeIf { it.isNotEmpty() }
                ?.let { add(RippleDappTxField(RippleDappTxFieldKey.PATHS, it.size.toString())) }
            // The Fee that is actually signed (drops). Surfaced so the verify screen shows the real
            // network fee baked into the JSON rather than a live re-estimate (a malicious inflated
            // Fee must be visible, not masked by a normal-looking estimate).
            obj.stringOrNull("Fee")?.let {
                add(RippleDappTxField(RippleDappTxFieldKey.FEE, formatXrpDrops(it)))
            }
        }

        return RippleDappTx(
            transactionType = transactionType,
            fields = fields,
            rawJson = rawJson,
            isPartialPayment = isPartialPayment,
        )
    }

    /**
     * The signed `Fee` in drops, or null when absent/unparseable. This is the fee actually encoded
     * in the raw JSON that gets signed verbatim, so the verify screen surfaces it instead of a live
     * network re-estimate that could hide a dApp-inflated fee.
     */
    fun feeDrops(rawJson: String): BigInteger? {
        val obj =
            try {
                json.parseToJsonElement(rawJson).jsonObject
            } catch (e: Exception) {
                return null
            }
        return obj.stringOrNull("Fee")?.toBigIntegerOrNull()
    }

    /**
     * A one-line, human-readable summary of a dApp-supplied XRPL transaction, for the keysign
     * notification banner where the native `toAmount` is 0 (the real amounts live in the JSON).
     * Returns null when the JSON can't be decoded into a known type, so callers can fall back.
     *
     * A `tfPartialPayment` amount is a ceiling, so it is rendered with `≤` (bounded below by
     * `DeliverMin` when one is set) rather than as a plain figure the banner would imply is
     * guaranteed. Comparison symbols keep this locale-neutral in a data-layer string.
     *
     * Examples: `OfferCreate: 1 XRP → 2.5 USD`, `Payment: 1 XRP`, `Payment: ≤ 1 XRP`, `Payment: ≥
     * 0.5 XRP, ≤ 1 XRP`, `TrustSet`.
     */
    fun summarize(rawJson: String): String? {
        val tx = decode(rawJson)
        val type = tx.transactionType ?: return null
        return when (type) {
            "OfferCreate" -> {
                val gets = tx.value(RippleDappTxFieldKey.SELLING)
                val pays = tx.value(RippleDappTxFieldKey.BUYING)
                if (gets != null && pays != null) "$type: $gets → $pays" else type
            }
            "Payment" -> {
                val amount = tx.value(RippleDappTxFieldKey.AMOUNT)
                val deliverMin = tx.value(RippleDappTxFieldKey.DELIVER_MIN)
                when {
                    amount == null -> type
                    !tx.isPartialPayment -> "$type: $amount"
                    deliverMin != null -> "$type: ≥ $deliverMin, ≤ $amount"
                    else -> "$type: ≤ $amount"
                }
            }
            else -> type
        }
    }

    private fun MutableList<RippleDappTxField>.addAmount(
        key: String,
        amountKey: RippleDappTxFieldKey,
        issuerKey: RippleDappTxFieldKey,
        obj: JsonObject,
    ) {
        val element = obj[key] ?: return
        val primitive = element as? JsonPrimitive
        if (primitive != null) {
            // A bare string/number amount is XRP in drops.
            primitive.contentOrNull?.let { drops ->
                add(RippleDappTxField(amountKey, formatXrpDrops(drops)))
            }
            return
        }
        // An issued-currency amount is an object: { currency, issuer, value }.
        val amountObj = element as? JsonObject ?: return
        val value = amountObj.stringOrNull("value")
        val currency = amountObj.stringOrNull("currency")
        val issuer = amountObj.stringOrNull("issuer")
        if (value != null && currency != null) {
            add(RippleDappTxField(amountKey, "$value $currency"))
            issuer?.let { add(RippleDappTxField(issuerKey, it)) }
        }
    }

    private fun formatXrpDrops(drops: String): String =
        try {
            "${BigDecimal(drops).divide(DROPS_PER_XRP).stripTrailingZeros().toPlainString()} XRP"
        } catch (e: NumberFormatException) {
            // Not a plain drops integer — show the raw value rather than dropping the row.
            drops
        }

    private fun JsonObject.stringOrNull(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
}
