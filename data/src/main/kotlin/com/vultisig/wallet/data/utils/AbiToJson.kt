package com.vultisig.wallet.data.utils

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.collections.map
import kotlin.collections.orEmpty


/**
 * Convert a single parameter object:
 * {
 *   "name": "...",
 *   "type": "...",
 *   "value": "...",
 *   "components": [...]
 * }
 */
internal fun convertParameter(obj: JsonObject): JsonElement {
    val type = obj["type"]?.jsonPrimitive?.content ?: ""

    // --- STRUCT / TUPLE ---
    if (type == "tuple" || type == "tuple[]") {
        return convertTuple(obj)
    }

    // --- ARRAY ---
    if (type.endsWith("[]")) {
        val baseType = type.removeSuffix("[]")
        val arr = obj["value"]?.jsonArray.orEmpty()
        return JsonArray(arr.map { elem ->
            when {
                elem is JsonObject && (elem["components"] != null) ->
                    convertTuple(elem.jsonObject)

                elem is JsonObject && elem["value"] != null ->
                    convertSimpleValue(baseType, elem["value"]!!)

                else -> convertSimpleValue(baseType, elem)
            }
        })
    }

    // --- SIMPLE PARAMETER ---
    val valueElem = obj["value"] ?: return JsonNull
    return convertSimpleValue(type, valueElem)
}

/**
 * Convert a tuple (object containing "components": [...] )
 */
private fun convertTuple(obj: JsonObject): JsonElement {
    val components = obj["components"]?.jsonArray.orEmpty()
    val items = components.map { comp ->
        convertParameter(comp.jsonObject)
    }
    return JsonArray(items)
}

/**
 * Convert a simple ABI type
 */
private fun convertSimpleValue(type: String, value: JsonElement): JsonElement {
    if (value is JsonNull) return JsonNull

    val text = value.jsonPrimitive.content

    return when {
        // ---------------- HEX TYPES ----------------
        type.equals("address", true) ||
                type.startsWith("bytes", ignoreCase = true) -> JsonPrimitive(normalizeHex(text))

        // ---------------- BOOLEAN ----------------
        type.equals("bool", true) -> JsonPrimitive(text.equals("true", true))

        // ---------------- STRING ----------------
        type.equals("string", true) -> JsonPrimitive(text)

        // ---------------- FIXED / DYNAMIC INT ----------------
        type.startsWith("uint") || type.startsWith("int") -> {
            parseBigIntAsJsonNumber(text)
        }

        // ---------------- FIXED-SIZE BYTES (bytes32 etc) ----------------
        type.matches(Regex("bytes\\d+", RegexOption.IGNORE_CASE)) ->
            JsonPrimitive(normalizeHex(text))

        // ---------------- FALLBACK ----------------
        else -> JsonPrimitive(text)
    }
}

/**
 * Convert decimal string to JSON number using BigDecimal
 */
private fun parseBigIntAsJsonNumber(value: String): JsonElement {
    return try {
        val big = BigInteger(value)
        JsonPrimitive(BigDecimal(big))
    } catch (e: Exception) {
        JsonPrimitive(value) // fallback as string
    }
}

/**
 * Format hex cleanly
 */
private fun normalizeHex(s: String): String {
    var t = s.trim().lowercase()
    if (!t.startsWith("0x")) t = "0x$t"
    return t
}
