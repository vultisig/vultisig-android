package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.api.FourByteApi
import com.vultisig.wallet.data.common.stripHexPrefix
import com.vultisig.wallet.data.utils.Numeric
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import timber.log.Timber
import javax.inject.Inject
import java.math.BigInteger

interface FourByteRepository {
    suspend fun decodeFunction(memo: String): String?
    fun decodeFunctionArgs(functionSignature: String, memo: String): String?
}

internal class FourByteRepositoryImpl @Inject constructor(
    private val fourByteApi: FourByteApi,
    @PrettyJson private val json: Json,
) : FourByteRepository {
    override suspend fun decodeFunction(memo: String): String? {
        if (memo.length < 8) return null
        try {
            val hash = memo.stripHexPrefix().substring(
                0,
                8
            )
            return fourByteApi.decodeFunction(hash)
        } catch (e: Exception) {
            Timber.e("Error decoding function: ${e.message}")
            return null
        }
    }

    override fun decodeFunctionArgs(functionSignature: String, memo: String): String? {
        return try {
            if (memo.length < 10) {
                return null
            }
            val paramsString = extractParametersFromSignature(functionSignature)
            val paramTypes = parseParameterTypes(paramsString)
            val encodedData = memo.substring(10)

            val decodedValues = decodeWithCorrectLayout(
                paramTypes,
                encodedData
            )
            decodedValues?.let {
                json.encodeToString(
                    JsonElement.serializer(),
                    JsonArray(it)
                )
            }
        } catch (e: Exception) {
            Timber.e("Error decoding function args: ${e.message}")
            null
        }
    }

    private fun decodeWithCorrectLayout(
        paramTypes: List<String>, encodedData: String
    ): List<JsonElement>? {
        return try {
            val chunks = encodedData.chunked(64)
            if (chunks.isEmpty()) return null

            val result = mutableListOf<JsonElement>()
            var currentChunkIndex = 0

            for (paramType in paramTypes) {
                val (decodedValue, consumedChunks) = decodeParameter(
                    paramType,
                    chunks,
                    currentChunkIndex
                )
                result.add(decodedValue)
                currentChunkIndex += consumedChunks
            }

            result
        } catch (e: Exception) {
            Timber.e("Error decoding with correct layout: ${e.message}")
            null
        }
    }

    private fun decodeParameter(
        paramType: String, chunks: List<String>, startIndex: Int
    ): Pair<JsonElement, Int> {
        return when {
            paramType.startsWith("(") && paramType.endsWith(")") -> {
                val tupleContent = paramType.substring(
                    1,
                    paramType.length - 1
                )
                val tupleComponents = parseParameterTypes(tupleContent)

                if (isTupleDynamic(tupleComponents)) {
                    val offset = BigInteger(
                        chunks[startIndex],
                        16
                    ).toInt() / 32

                    val tupleElements = mutableListOf<JsonElement>()
                    var tupleChunkIndex = offset

                    for (component in tupleComponents) {
                        val (decodedValue, consumedChunks) = decodeParameter(
                            component,
                            chunks,
                            tupleChunkIndex
                        )
                        tupleElements.add(decodedValue)
                        tupleChunkIndex += consumedChunks
                    }

                    Pair(
                        JsonArray(tupleElements),
                        1
                    )
                } else {
                    val tupleElements = mutableListOf<JsonElement>()
                    var tupleChunkIndex = startIndex

                    for (component in tupleComponents) {
                        val (decodedValue, consumedChunks) = decodeParameter(
                            component,
                            chunks,
                            tupleChunkIndex
                        )
                        tupleElements.add(decodedValue)
                        tupleChunkIndex += consumedChunks
                    }

                    Pair(
                        JsonArray(tupleElements),
                        tupleChunkIndex - startIndex
                    )
                }
            }

            paramType == "uint32" -> {
                val value = BigInteger(
                    chunks[startIndex],
                    16
                )
                Pair(
                    JsonPrimitive(value.toString()),
                    1
                )
            }

            paramType == "uint256" -> {
                val value = BigInteger(
                    chunks[startIndex],
                    16
                )
                Pair(
                    JsonPrimitive(value.toString()),
                    1
                )
            }

            paramType.startsWith("uint") -> {
                val value = BigInteger(
                    chunks[startIndex],
                    16
                )
                Pair(
                    JsonPrimitive(value.toString()),
                    1
                )
            }

            paramType == "bytes32" -> {
                val value = "0x" + chunks[startIndex]
                Pair(
                    JsonPrimitive(value),
                    1
                )
            }

            paramType.startsWith("bytes") && paramType.length > 5 -> {
                val value = "0x" + chunks[startIndex]
                Pair(
                    JsonPrimitive(value),
                    1
                )
            }

            paramType == "address" -> {
                val chunk = chunks[startIndex]
                val address = "0x" + chunk.substring(24)
                Pair(
                    JsonPrimitive(address),
                    1
                )
            }

            paramType == "bytes" -> {
                val offset = BigInteger(
                    chunks[startIndex],
                    16
                ).toInt() / 32
                if (offset >= chunks.size) {
                    return Pair(
                        JsonPrimitive("0x"),
                        1
                    )
                }

                val length = BigInteger(
                    chunks[offset],
                    16
                ).toInt()
                if (length == 0) {
                    Pair(
                        JsonPrimitive("0x"),
                        1
                    )
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
                        data.toString().substring(
                            0,
                            hexLength
                        )
                    } else {
                        data.toString()
                    }

                    Pair(
                        JsonPrimitive("0x$actualData"),
                        1
                    )
                }
            }

            paramType == "string" -> {
                val offset = BigInteger(
                    chunks[startIndex],
                    16
                ).toInt() / 32
                if (offset >= chunks.size) {
                    return Pair(
                        JsonPrimitive(""),
                        1
                    )
                }

                val length = BigInteger(
                    chunks[offset],
                    16
                ).toInt()
                if (length == 0) {
                    Pair(
                        JsonPrimitive(""),
                        1
                    )
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
                    val hexData = if (data.length >= hexLength) {
                        data.toString().substring(
                            0,
                            hexLength
                        )
                    } else {
                        data.toString()
                    }

                    val stringValue = try {
                        String(hexData.chunked(2).map { it.toInt(16).toByte() }.toByteArray())
                    } catch (e: Exception) {
                        Timber.e("Error converting hex to string: ${e.message}")
                        ""
                    }

                    Pair(
                        JsonPrimitive(stringValue),
                        1
                    )
                }
            }

            paramType == "bool" -> {
                val value = BigInteger(
                    chunks[startIndex],
                    16
                )
                Pair(
                    JsonPrimitive(value != BigInteger.ZERO),
                    1
                )
            }

            paramType.endsWith("[]") -> {
                val elementType = paramType.substring(
                    0,
                    paramType.length - 2
                )
                val offset = BigInteger(
                    chunks[startIndex],
                    16
                ).toInt() / 32
                if (offset >= chunks.size) {
                    return Pair(
                        JsonArray(emptyList()),
                        1
                    )
                }

                val arrayLength = BigInteger(
                    chunks[offset],
                    16
                ).toInt()
                val arrayElements = mutableListOf<JsonElement>()
                var arrayChunkIndex = offset + 1

                repeat(arrayLength) {
                    val (element, consumed) = decodeParameter(
                        elementType,
                        chunks,
                        arrayChunkIndex
                    )
                    arrayElements.add(element)
                    arrayChunkIndex += consumed
                }

                Pair(
                    JsonArray(arrayElements),
                    1
                )
            }

            else -> {
                val value = "0x" + chunks[startIndex]
                Pair(
                    JsonPrimitive(value),
                    1
                )
            }
        }
    }

    private fun isTupleDynamic(tupleComponents: List<String>): Boolean {
        return tupleComponents.any { component ->
            when {
                component == "bytes" -> true
                component == "string" -> true
                component.endsWith("[]") -> true
                component.startsWith("(") && component.endsWith(")") -> {
                    val nestedContent = component.substring(
                        1,
                        component.length - 1
                    )
                    val nestedComponents = parseParameterTypes(nestedContent)
                    isTupleDynamic(nestedComponents)
                }

                else -> false
            }
        }
    }

    private fun decodeBytesFromOffset(chunks: List<String>, offsetChunkIndex: Int): JsonElement {
        return try {
            val offset = BigInteger(
                chunks[offsetChunkIndex],
                16
            ).toInt() / 32

            if (offset >= chunks.size) {
                return JsonPrimitive("0x")
            }

            val length = BigInteger(
                chunks[offset],
                16
            ).toInt()

            if (length == 0) {
                JsonPrimitive("0x")
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
                    data.toString().substring(
                        0,
                        hexLength
                    )
                } else {
                    data.toString()
                }

                JsonPrimitive("0x$actualData")
            }
        } catch (e: Exception) {
            Timber.e("Error decoding bytes from offset: ${e.message}")
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

        return functionSignature.substring(
            startIndex + 1,
            endIndex
        )
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