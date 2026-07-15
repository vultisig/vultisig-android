package com.vultisig.wallet.data.chains.helpers

import java.math.BigDecimal
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import timber.log.Timber

/** A single decoded label/value row of a dApp-supplied XRPL transaction, for the verify screen. */
data class RippleDappTxField(val label: String, val value: String)

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
    /** Value of the decoded field with [label], or null if absent. */
    fun value(label: String): String? = fields.firstOrNull { it.label == label }?.value
}

/**
 * Decodes the raw XRPL transaction JSON a dApp hands the co-signer (via `SignRipple`) into readable
 * terms — type, source, destination, amounts (`Amount` / `SendMax` / `DeliverMin` / `TakerGets` /
 * `TakerPays`), issuer and destination tag. Pure (no JNI, no Android), so the verify screen renders
 * decoded terms and it stays unit-testable.
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
            transactionType?.let { add(RippleDappTxField("Type", it)) }
            obj.stringOrNull("Account")?.let { add(RippleDappTxField("From", it)) }
            obj.stringOrNull("Destination")?.let { add(RippleDappTxField("To", it)) }
            obj.stringOrNull("DestinationTag")?.let {
                add(RippleDappTxField("Destination Tag", it))
            }
            addAmount(key = "Amount", label = "Amount", obj = obj)
            addAmount(key = "SendMax", label = "Send max", obj = obj)
            addAmount(key = "DeliverMin", label = "Deliver min", obj = obj)
            // OfferCreate: TakerGets is what the account sells, TakerPays what it buys. Match the
            // extension's Selling / Buying wording rather than the raw XRPL field names.
            addAmount(key = "TakerGets", label = "Selling", obj = obj)
            addAmount(key = "TakerPays", label = "Buying", obj = obj)
            obj.stringOrNull("LimitAmount")?.let { add(RippleDappTxField("Limit", it)) }
        }

        return RippleDappTx(transactionType = transactionType, fields = fields, rawJson = rawJson)
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
                val gets = tx.value("Selling")
                val pays = tx.value("Buying")
                if (gets != null && pays != null) "$type: $gets → $pays" else type
            }
            "Payment" -> tx.value("Amount")?.let { "$type: $it" } ?: type
            else -> type
        }
    }

    private fun MutableList<RippleDappTxField>.addAmount(
        key: String,
        label: String,
        obj: JsonObject,
    ) {
        val element = obj[key] ?: return
        val primitive = element as? JsonPrimitive
        if (primitive != null) {
            // A bare string/number amount is XRP in drops.
            primitive.contentOrNull?.let { drops ->
                add(RippleDappTxField(label, formatXrpDrops(drops)))
            }
            return
        }
        // An issued-currency amount is an object: { currency, issuer, value }.
        val amountObj = element as? JsonObject ?: return
        val value = amountObj.stringOrNull("value")
        val currency = amountObj.stringOrNull("currency")
        val issuer = amountObj.stringOrNull("issuer")
        if (value != null && currency != null) {
            add(RippleDappTxField(label, "$value $currency"))
            issuer?.let { add(RippleDappTxField("Issuer", it)) }
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
