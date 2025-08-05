package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.api.FourByteApi
import com.vultisig.wallet.data.common.stripHexPrefix
import com.vultisig.wallet.data.utils.Numeric
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Test
import javax.inject.Inject
import java.math.BigInteger


internal class FourByteRepositoryImpl {

    val s = "send((uint32,bytes32,uint256,uint256,bytes,bytes,bytes),(uint256,uint256),address)"

    val memo = "0xc7c7f5b30000000000000000000000000000000000000000000000000000000000000080000000000000000000000000000000000000000000000000000027a886629924000000000000000000000000000000000000000000000000000000000000000000000000000000000000000014f6ed6cbb27b607b0e2a48551a988f1a19c89b600000000000000000000000000000000000000000000000000000000000075e800000000000000000000000014f6ed6cbb27b607b0e2a48551a988f1a19c89b60000000000000000000000000000000000000000000000000de0b6b3a76400000000000000000000000000000000000000000000000000000de0b6b3a764000000000000000000000000000000000000000000000000000000000000000000e000000000000000000000000000000000000000000000000000000000000001200000000000000000000000000000000000000000000000000000000000000140000000000000000000000000000000000000000000000000000000000000001600030100110100000000000000000000000000030d400000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000"

    @Test
    fun x(){
        val z = decodeFunctionArgs(
            s,memo
        )

        println(z)
    }


    fun decodeFunctionArgs(functionSignature: String, memo: String): String? {
        return try {
            val paramsString = extractParametersFromSignature(functionSignature)
            val paramTypes = parseParameterTypes(paramsString)
            val encodedData = memo.substring(10) // Remove function selector

            // Use improved decoder based on actual ABI analysis
            val decodedValues = decodeWithCorrectLayout(paramTypes, encodedData)
            decodedValues?.let {
                Json{prettyPrint = true}.encodeToString(JsonElement.serializer(), JsonArray(it))
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun decodeWithCorrectLayout(paramTypes: List<String>, encodedData: String): List<JsonElement>? {
        return try {
            val chunks = encodedData.chunked(64)
            if (chunks.isEmpty()) return null

            val result = mutableListOf<JsonElement>()
            var currentChunkIndex = 0

            for (paramType in paramTypes) {
                val (decodedValue, consumedChunks) = decodeParameter(paramType, chunks, currentChunkIndex)
                result.add(decodedValue)
                currentChunkIndex += consumedChunks
            }

            result
        } catch (e: Exception) {
            null
        }
    }

    private fun decodeParameter(paramType: String, chunks: List<String>, startIndex: Int): Pair<JsonElement, Int> {
        return when {
            paramType.startsWith("(") && paramType.endsWith(")") -> {
                // Handle tuple - determine if it's static or dynamic
                val tupleContent = paramType.substring(1, paramType.length - 1)
                val tupleComponents = parseParameterTypes(tupleContent)

                if (isTupleDynamic(tupleComponents)) {
                    // Dynamic tuple - read offset and decode from there
                    val offset = BigInteger(chunks[startIndex], 16).toLong() / 32
                    val tupleElements = mutableListOf<JsonElement>()
                    var tupleChunkIndex = offset.toInt()

                    for (component in tupleComponents) {
                        val (decodedValue, consumedChunks) = decodeParameter(component, chunks, tupleChunkIndex)
                        tupleElements.add(decodedValue)
                        tupleChunkIndex += consumedChunks
                    }

                    Pair(JsonArray(tupleElements), 1) // Consumed 1 chunk for offset
                } else {
                    // Static tuple - decode inline
                    val tupleElements = mutableListOf<JsonElement>()
                    var tupleChunkIndex = startIndex

                    for (component in tupleComponents) {
                        val (decodedValue, consumedChunks) = decodeParameter(component, chunks, tupleChunkIndex)
                        tupleElements.add(decodedValue)
                        tupleChunkIndex += consumedChunks
                    }

                    Pair(JsonArray(tupleElements), tupleChunkIndex - startIndex)
                }
            }

            paramType == "uint32" -> {
                val value = BigInteger(chunks[startIndex], 16)
                Pair(JsonPrimitive(value.toString()), 1)
            }

            paramType == "uint256" -> {
                val value = BigInteger(chunks[startIndex], 16)
                Pair(JsonPrimitive(value.toString()), 1)
            }

            paramType.startsWith("uint") -> {
                // Handle other uint types
                val value = BigInteger(chunks[startIndex], 16)
                Pair(JsonPrimitive(value.toString()), 1)
            }

            paramType == "bytes32" -> {
                val value = "0x" + chunks[startIndex]
                Pair(JsonPrimitive(value), 1)
            }

            paramType.startsWith("bytes") && paramType.length > 5 -> {
                // Fixed-size bytes (e.g., bytes4, bytes8, etc.)
                val value = "0x" + chunks[startIndex]
                Pair(JsonPrimitive(value), 1)
            }

            paramType == "address" -> {
                // Address is in the last 20 bytes of the 32-byte chunk
                val chunk = chunks[startIndex]
                val address = "0x" + chunk.substring(24) // Last 40 chars (20 bytes)
                Pair(JsonPrimitive(address), 1)
            }

            paramType == "bytes" -> {
                // Dynamic bytes - read offset, then length, then data
                val offset = BigInteger(chunks[startIndex], 16).toInt() / 32
                if (offset >= chunks.size) {
                    return Pair(JsonPrimitive("0x"), 1)
                }

                val length = BigInteger(chunks[offset], 16).toInt()
                if (length == 0) {
                    Pair(JsonPrimitive("0x"), 1)
                } else {
                    val dataChunks = (length + 31) / 32
                    val data = StringBuilder()

                    for (i in 1..dataChunks) {
                        val dataChunkIndex = offset + i
                        if (dataChunkIndex < chunks.size) {
                            data.append(chunks[dataChunkIndex])
                        }
                    }

                    val hexLength = length * 2
                    val actualData = if (data.length >= hexLength) {
                        data.toString().substring(0, hexLength)
                    } else {
                        data.toString()
                    }

                    Pair(JsonPrimitive("0x$actualData"), 1) // Consumed 1 chunk for offset
                }
            }

            paramType == "string" -> {
                // String is encoded like dynamic bytes
                val offset = BigInteger(chunks[startIndex], 16).toInt() / 32
                if (offset >= chunks.size) {
                    return Pair(JsonPrimitive(""), 1)
                }

                val length = BigInteger(chunks[offset], 16).toInt()
                if (length == 0) {
                    Pair(JsonPrimitive(""), 1)
                } else {
                    val dataChunks = (length + 31) / 32
                    val data = StringBuilder()

                    for (i in 1..dataChunks) {
                        val dataChunkIndex = offset + i
                        if (dataChunkIndex < chunks.size) {
                            data.append(chunks[dataChunkIndex])
                        }
                    }

                    // Convert hex to string
                    val hexLength = length * 2
                    val hexData = if (data.length >= hexLength) {
                        data.toString().substring(0, hexLength)
                    } else {
                        data.toString()
                    }

                    val stringValue = try {
                        String(hexData.chunked(2).map { it.toInt(16).toByte() }.toByteArray())
                    } catch (e: Exception) {
                        ""
                    }

                    Pair(JsonPrimitive(stringValue), 1)
                }
            }

            paramType == "bool" -> {
                val value = BigInteger(chunks[startIndex], 16)
                Pair(JsonPrimitive(value != BigInteger.ZERO), 1)
            }

            paramType.endsWith("[]") -> {
                // Dynamic array - read offset, then length, then elements
                val elementType = paramType.substring(0, paramType.length - 2)
                val offset = BigInteger(chunks[startIndex], 16).toInt() / 32
                if (offset >= chunks.size) {
                    return Pair(JsonArray(emptyList()), 1)
                }

                val arrayLength = BigInteger(chunks[offset], 16).toInt()
                val arrayElements = mutableListOf<JsonElement>()
                var arrayChunkIndex = offset + 1

                repeat(arrayLength) {
                    val (element, consumed) = decodeParameter(elementType, chunks, arrayChunkIndex)
                    arrayElements.add(element)
                    arrayChunkIndex += consumed
                }

                Pair(JsonArray(arrayElements), 1) // Consumed 1 chunk for offset
            }

            else -> {
                // Fallback for unknown types - treat as raw hex
                val value = "0x" + chunks[startIndex]
                Pair(JsonPrimitive(value), 1)
            }
        }
    }



    private fun isTupleDynamic(tupleComponents: List<String>): Boolean {
        // A tuple is dynamic if it contains any dynamic types
        return tupleComponents.any { component ->
            when {
                component == "bytes" -> true
                component == "string" -> true
                component.endsWith("[]") -> true
                component.startsWith("(") && component.endsWith(")") -> {
                    // Recursively check nested tuples
                    val nestedContent = component.substring(1, component.length - 1)
                    val nestedComponents = parseParameterTypes(nestedContent)
                    isTupleDynamic(nestedComponents)
                }
                else -> false
            }
        }
    }

    private fun decodeBytesFromOffset(chunks: List<String>, offsetChunkIndex: Int): JsonElement {
        return try {
            // Get the offset value
            val offset = BigInteger(chunks[offsetChunkIndex], 16).toInt() / 32

            if (offset >= chunks.size) {
                return JsonPrimitive("0x")
            }

            // Get length from the offset location
            val length = BigInteger(chunks[offset], 16).toInt()

            if (length == 0) {
                JsonPrimitive("0x")
            } else {
                // Calculate number of chunks needed for the data
                val dataChunks = (length + 31) / 32
                val data = StringBuilder()

                // Collect data from subsequent chunks
                for (i in 1..dataChunks) {
                    val dataChunkIndex = offset + i
                    if (dataChunkIndex < chunks.size) {
                        data.append(chunks[dataChunkIndex])
                    }
                }

                // Trim to actual length (length is in bytes, so * 2 for hex chars)
                val hexLength = length * 2
                val actualData = if (data.length >= hexLength) {
                    data.toString().substring(0, hexLength)
                } else {
                    data.toString()
                }

                JsonPrimitive("0x$actualData")
            }
        } catch (e: Exception) {
            JsonPrimitive("0x")
        }
    }

    private fun extractParametersFromSignature(functionSignature: String): String {
        val startIndex = functionSignature.indexOf('(')
        if (startIndex == -1) return ""

        var parenCount = 0
        var endIndex = startIndex

        for (i in startIndex until functionSignature.length) {
            when (functionSignature[i]) {
                '(' -> parenCount++
                ')' -> {
                    parenCount--
                    if (parenCount == 0) {
                        endIndex = i
                        break
                    }
                }
            }
        }

        return functionSignature.substring(startIndex + 1, endIndex)
    }

    private fun parseParameterTypes(paramsString: String): List<String> {
        if (paramsString.isEmpty()) return emptyList()

        val result = mutableListOf<String>()
        var current = ""
        var parenCount = 0

        for (char in paramsString) {
            when (char) {
                '(' -> {
                    parenCount++
                    current += char
                }
                ')' -> {
                    parenCount--
                    current += char
                }
                ',' -> {
                    if (parenCount == 0) {
                        result.add(current.trim())
                        current = ""
                    } else {
                        current += char
                    }
                }
                else -> current += char
            }
        }

        if (current.isNotEmpty()) {
            result.add(current.trim())
        }

        return result
    }
}