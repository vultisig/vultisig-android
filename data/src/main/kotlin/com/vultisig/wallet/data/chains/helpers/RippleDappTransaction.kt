package com.vultisig.wallet.data.chains.helpers

import java.math.BigDecimal
import java.math.BigInteger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import timber.log.Timber

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
    FEE,
}

/** A single decoded key/value row of a dApp-supplied XRPL transaction, for the verify screen. */
data class RippleDappTxField(val key: RippleDappTxFieldKey, val value: String)

/**
 * Human-readable decode of a dApp-supplied XRPL transaction ([SignRipple.rawJson]).
 *
 * [fields] is empty when the JSON can't be decoded into known terms — the verify screen then falls
 * back to showing [rawJson] verbatim so a co-signer is never left with a blank/misleading screen.
 */
data class RippleDappTx(
    val transactionType: String?,
    val fields: List<RippleDappTxField>,
    val rawJson: String,
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
            // The Fee that is actually signed (drops). Surfaced so the verify screen shows the real
            // network fee baked into the JSON rather than a live re-estimate (a malicious inflated
            // Fee must be visible, not masked by a normal-looking estimate).
            obj.stringOrNull("Fee")?.let {
                add(RippleDappTxField(RippleDappTxFieldKey.FEE, formatXrpDrops(it)))
            }
        }

        return RippleDappTx(transactionType = transactionType, fields = fields, rawJson = rawJson)
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
     * Examples: `OfferCreate: 1 XRP → 2.5 USD`, `Payment: 1 XRP`, `TrustSet`.
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
            "Payment" -> tx.value(RippleDappTxFieldKey.AMOUNT)?.let { "$type: $it" } ?: type
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
