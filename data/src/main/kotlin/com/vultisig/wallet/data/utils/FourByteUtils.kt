package com.vultisig.wallet.data.utils

import io.ethers.core.types.Address
import io.ethers.core.types.Bytes
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.math.BigInteger


internal fun Array<Any>.toJsonElement(): JsonElement {
    return buildJsonArray {
        this@toJsonElement.forEach { value ->
            add(convertValueToJsonElement(value))
        }
    }
}


private fun convertValueToJsonElement(value: Any): JsonElement {
    return when (value) {
        is Address -> JsonPrimitive(value.toString())

        is Bytes -> JsonPrimitive(value.toString())

        is BigInteger -> JsonPrimitive(value.toString())

        is Boolean -> JsonPrimitive(value)

        is String -> JsonPrimitive(value)

        is Array<*> -> buildJsonArray {
            value.forEach { item ->
                if (item != null) {
                    add(convertValueToJsonElement(item))
                } else {
                    add(JsonNull)
                }
            }
        }

        else -> {
            if (value.javaClass.isArray) {
                val array = value as? Array<*>
                if (array != null) {
                    buildJsonArray {
                        array.forEach { item ->
                            if (item != null) {
                                add(convertValueToJsonElement(item))
                            } else {
                                add(JsonNull)
                            }
                        }
                    }
                } else {
                    JsonPrimitive(value.toString())
                }
            }
            else if (hasToTupleMethod(value)) {
                try {
                    val tupleMethod = value.javaClass.getMethod("getTuple")
                    val tupleArray = tupleMethod.invoke(value) as Array<*>
                    buildJsonArray {
                        tupleArray.forEach { item ->
                            if (item != null) {
                                add(convertValueToJsonElement(item))
                            } else {
                                add(JsonNull)
                            }
                        }
                    }
                } catch (_: Exception) {
                    buildJsonObject {
                        put("contractStruct", value.toString())
                        put("note", "Could not extract tuple data")
                    }
                }
            }
            else {
                JsonPrimitive(value.toString())
            }
        }
    }
}


private fun hasToTupleMethod(value: Any): Boolean {
    return try {
        value.javaClass.getMethod("getTuple") != null
    } catch (e: NoSuchMethodException) {
        false
    }
}
