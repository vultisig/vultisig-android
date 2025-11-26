package com.vultisig.wallet.data.common

import com.google.protobuf.ByteString
import com.vultisig.wallet.data.utils.Numeric
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Type
import timber.log.Timber
import java.math.BigInteger
import org.web3j.abi.FunctionReturnDecoder
import org.web3j.abi.datatypes.*
import org.web3j.abi.datatypes.generated.Bytes32
import org.web3j.abi.datatypes.generated.Uint128
import org.web3j.abi.datatypes.generated.Uint256


fun String.toHexBytes(): ByteArray {
    return Numeric.hexStringToByteArray(this)
}

fun String.toHexByteArray(): ByteArray {
    return Numeric.hexStringToByteArray(this)
}

fun String.toByteString(): ByteString {
    return ByteString.copyFrom(
        this,
        Charsets.UTF_8
    )
}

fun String.toHexBytesInByteString(): ByteString {
    return ByteString.copyFrom(this.toHexBytes())
}

fun String.isHex(): Boolean {
    return this.matches(Regex("^(0x)?[0-9A-Fa-f]+$"))
}

fun String.toByteStringOrHex(): ByteString {
    return if (this.isHex()) {
        this.toHexBytesInByteString()
    } else {
        this.toByteString()
    }
}

fun String.normalizeMessageFormat(): String {
    return try {
        if (this.isHex()) {
            val hex = this.remove0x()
            if (hex.length % 2 != 0) {
                return this
            }
            val bytes = this.toHexBytes()
            String(
                bytes,
                Charsets.UTF_8
            ).replace(
                // Remove leading/trailing control characters
                "^\\p{C}+|\\p{C}+$".toRegex(),
                ""
            )
        } else {
            this
        }
    } catch (e: Exception) {
        Timber.e(
            e,
            "failed to decode"
        )
        this
    }
}

internal fun String.stripHexPrefix(): String {
    return if (startsWith("0x")) {
        substring(2)
    } else {
        this
    }
}

fun String.add0x(): String {
    if (startsWith("0x")) {
        return this
    }
    return "0x$this"
}

fun String.remove0x(): String {
    if (startsWith("0x")) {
        return removePrefix("0x")
    }
    return this
}

fun String.isNotEmptyContract(): Boolean {
    val zeroAddress = "0x0000000000000000000000000000000000000000"
    return isNotEmpty() && !equals(
        zeroAddress,
        ignoreCase = true
    )
}

fun String?.convertToBigIntegerOrZero(): BigInteger {
    val cleanedInput = this?.removePrefix("0x")
    return if (cleanedInput.isNullOrEmpty()) {
        BigInteger.ZERO
    } else {
        try {
            BigInteger(
                cleanedInput,
                16
            )
        } catch (e: NumberFormatException) {
            BigInteger.ZERO
        }
    }
}


fun String.decodeFunctionArgs(memo: String): String? {
    try {

        val paramsStrings = parseTupleString(this)
        val paramsStrings_1 = this.parseNestedSignature()

        //collect((uint256,address,uint128,uint128))
        //0xfc6f786500000000000000000000000000000000000000000000000000000000000000010000000000000000000000002731cf69f28a2eed9d0d0e1a3b6399e61be446b000000000000000000000000000000000000000000000000000000000000000010000000000000000000000000000000000000000000000000000000000000001
        val json = Json


        val decodedValues = memo.getTypedReference(
            paramsStrings,
        )
        decodedValues.decodeTypedReferences()?.let {
            return json.encodeToString(
                JsonElement.serializer(),
                JsonArray(it)
            )
        }
        return null

    } catch (e: Exception) {
        Timber.e(
            e,
            "decode function args error"
        )
        return null
    }

}

internal fun String.getTypedReference(
    paramTypes: List<Any>,
): MutableList<Type<Any>>? {
    try {

        val test = paramTypes.map {param->

                for(item in param){

                }

                it.map {type ->
                    when (type) {
                        "bytes" -> object : TypeReference<DynamicBytes>() {}
                        "bytes[]" -> object : TypeReference<DynamicArray<DynamicBytes>>() {}
                        "address" -> object : TypeReference<Address>() {}
                        "uint256" -> object : TypeReference<Uint256>() {}
                        "string" -> object : TypeReference<Utf8String>() {}
                        "string[]" -> object : TypeReference<DynamicArray<Utf8String>>() {}
                        "bool" -> object : TypeReference<Bool>() {}
                        "bytes32" -> object : TypeReference<Bytes32>() {}
                        "uint128" -> object : TypeReference<Uint128>() {}
                        "uint256[]" -> object : TypeReference<DynamicArray<Uint256>>() {}
                        "address[]" -> object : TypeReference<DynamicArray<Address>>() {}
                        else -> null
                    }

                }
        }

        val encodedData = if (this.length >= 10) this.substring(10) else ""

        //convert test to  DynamicArray
//        var test_2 = test.flatten()

        val decodedValues = if (encodedData.isNotEmpty()) {
            FunctionReturnDecoder.decode(
                encodedData,
                test as MutableList<TypeReference<Type<Any>>>?
            )
        } else {
            null
        }
        return decodedValues
    } catch (e: Exception) {
        return null
    }
}

fun String.parseNestedSignature(): List<List<String>> {


    val parts = mutableListOf<String>()
    var currentPart = StringBuilder()
    var parenthesesLevel = 0
//mintstadd((address,address,uint256,address,uint256,string,uint256,uint256,address,uint128,uint128,bytes32),bytes)
    val cleanedSignature = this.substring(this.indexOf('('))
    val Extraparrt = this.substringBeforeLast("(").replace(
        "(",
        ""
    )


    for (char in cleanedSignature) {
        when (char) {
            '(' -> parenthesesLevel++
            ')' -> parenthesesLevel--
            ',' -> {
                if (parenthesesLevel == 0) {
                    parts.add(currentPart.toString().trim())
                    currentPart = StringBuilder()
                    continue
                }
            }
        }
        currentPart.append(char)
    }

    if (currentPart.isNotEmpty()) {
        parts.add(currentPart.toString().trim())
    }

    return parts.map {
        it.split(",").map {
            it.trim().replace(
                "(",
                ""
            ).replace(
                ")",
                ""
            )
        }
    }
}

internal fun MutableList<Type<Any>>?.decodeTypedReferences(
): List<JsonElement>? =
    this?.map {
        when (it) {
            is Uint256 -> JsonPrimitive(it.value.toString())
            is Address -> JsonPrimitive(it.value)
            is Utf8String -> JsonPrimitive(it.value)
            is Bool -> JsonPrimitive(it.value.toString())
            is Bytes32 -> JsonPrimitive(Numeric.toHexString(it.value))
            is Uint128 -> JsonPrimitive(it.value.toString())
            is DynamicBytes -> JsonPrimitive(Numeric.toHexString(it.value as ByteArray))
            is DynamicArray<*> -> JsonArray(it.value.map { v ->
                when (v) {
                    is Uint256 -> JsonPrimitive(v.value.toString())
                    is Uint128 -> JsonPrimitive(v.value.toString())
                    is DynamicBytes -> JsonPrimitive(Numeric.toHexString(v.value as ByteArray))
                    is Address -> JsonPrimitive(v.value)
                    is Utf8String -> JsonPrimitive(v.value)
                    else -> JsonPrimitive(v.toString())
                }
            })

            else -> {
                JsonPrimitive(it.toString())
            }
        }
    }


/** Parse a tuple string into a nested List<Any> where elements are String or List<Any> */
fun parseTupleString(s: String): List<Any> {
    val stack = ArrayDeque<MutableList<Any>>()
    var current = mutableListOf<Any>()
    val token = StringBuilder()
    fun flushTokenToCurrent() {
        val t = token.toString().trim()
        if (t.isNotEmpty()) {
            current.add(t)
            token.setLength(0)
        } else {
            token.setLength(0)
        }
    }

    for (ch in s) {
        when (ch) {
            '(' -> {
                // start new level: push current, new current
                stack.addLast(current)
                current = mutableListOf()
                token.setLength(0)
            }

            ')' -> {
                // finish token before closing
                flushTokenToCurrent()
                val completed = current.toList() // completed group
                // pop previous level and append completed group
                current = if (stack.isNotEmpty()) stack.removeLast() else mutableListOf()
                current.add(completed)
                token.setLength(0)
            }

            ',' -> {
                // separator: flush token as an element in current level
                flushTokenToCurrent()
            }

            else -> {
                // accumulate characters into token (ignore newlines)
                token.append(ch)
            }
        }
    }
    // flush any trailing token (for inputs without outer parentheses)
    val trailing = token.toString().trim()
    if (trailing.isNotEmpty()) current.add(trailing)

    return current.toList()
}

/** Convert parsed nested List<Any> into List<List<String>> where each element becomes a list of strings.
 *  - If an element is String -> becomes [string]
 *  - If an element is List -> its string elements are preserved (nested lists are flattened one level)
 */
fun normalizeToListOfLists(parsed: List<Any>): List<List<String>> {
    fun toStringList(obj: Any): List<String> = when (obj) {
        is String -> listOf(obj)
        is List<*> -> obj.map {
            when (it) {
                is String -> it
                is List<*> -> it.joinToString(",") { inner -> inner.toString() } // fallback join nested lists
                else -> it.toString()
            }
        }

        else -> listOf(obj.toString())
    }
    return parsed.map { toStringList(it) }
}

/** ------- Example usage ------- */
fun main() {
    val input =
        "((address,address,uint256,address,uint256,string,uint256,uint256,address,uint128,uint128,bytes32),bytes)"
    val parsed = parseTupleString(input)
    println("Raw parsed structure:")
    println(parsed) // debug - shows nested lists and strings

    val normalized = normalizeToListOfLists(parsed)
    println("\nNormalized List<List<String>> (desired shape):")
    println(normalized)
    // Print nicely
    normalized.forEachIndexed { i, list ->
        println("element[$i] = ${list}")
    }
}

