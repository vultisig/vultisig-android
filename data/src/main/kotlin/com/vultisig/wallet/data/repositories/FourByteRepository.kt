package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.api.FourByteApi
import com.vultisig.wallet.data.common.stripHexPrefix
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import timber.log.Timber
import java.math.BigInteger
import javax.inject.Inject

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
            Timber.e(e)
            return null
        }
    }

    override fun decodeFunctionArgs(functionSignature: String, memo: String): String? {
        return try {
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
            Timber.e(e)
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
            Timber.e(e)
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
                    val tupleStartByte = offset * 32

                    for (component in tupleComponents) {
                        val (decodedValue, consumedChunks) = decodeParameterInTuple(
                            component,
                            chunks,
                            tupleChunkIndex,
                            tupleStartByte
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

            paramType.endsWith("[]") -> {
                val elementType = paramType.substring(
                    0,
                    paramType.length - 2
                )
                try {
                    val offsetBytes = BigInteger(
                        chunks[startIndex],
                        16
                    ).toInt()
                    val offsetChunks = offsetBytes / 32

                    if (offsetChunks >= chunks.size) {
                        return Pair(
                            JsonArray(emptyList()),
                            1
                        )
                    }

                    val arrayLength = BigInteger(
                        chunks[offsetChunks],
                        16
                    ).toInt()
                    if (arrayLength == 0) {
                        return Pair(
                            JsonArray(emptyList()),
                            1
                        )
                    }

                    val arrayElements = mutableListOf<JsonElement>()

                    if (elementType == "bytes") {
                        val elementOffsetsStart = offsetChunks + 1
                        val elementOffsets = mutableListOf<Int>()

                        for (i in 0 until arrayLength) {
                            val relativeOffsetBytes = BigInteger(
                                chunks[elementOffsetsStart + i],
                                16
                            ).toInt()
                            val absoluteOffset = offsetChunks + 1 + (relativeOffsetBytes / 32)
                            elementOffsets.add(absoluteOffset)
                        }

                        for (elementOffset in elementOffsets) {
                            if (elementOffset >= chunks.size) {
                                arrayElements.add(JsonPrimitive("0x"))
                                continue
                            }

                            val elementLength = BigInteger(
                                chunks[elementOffset],
                                16
                            ).toInt()

                            if (elementLength == 0) {
                                arrayElements.add(JsonPrimitive("0x"))
                            } else {
                                val dataStartChunk = elementOffset + 1
                                val chunksNeeded = (elementLength + 31) / 32
                                val hexData = StringBuilder()

                                for (j in 0 until chunksNeeded) {
                                    val chunkIndex = dataStartChunk + j
                                    if (chunkIndex < chunks.size) {
                                        hexData.append(chunks[chunkIndex])
                                    }
                                }

                                val requiredHexLength = elementLength * 2
                                val actualData = if (hexData.length >= requiredHexLength) {
                                    hexData.substring(
                                        0,
                                        requiredHexLength
                                    )
                                } else {
                                    hexData.toString()
                                }

                                arrayElements.add(JsonPrimitive("0x$actualData"))
                            }
                        }
                    } else {
                        var arrayChunkIndex = offsetChunks + 1
                        repeat(arrayLength) {
                            val (element, consumed) = decodeParameter(
                                elementType,
                                chunks,
                                arrayChunkIndex
                            )
                            arrayElements.add(element)
                            arrayChunkIndex += consumed
                        }
                    }

                    Pair(
                        JsonArray(arrayElements),
                        1
                    )
                } catch (e: Exception) {
                    Timber.e(e)
                    Pair(
                        JsonArray(emptyList()),
                        1
                    )
                }
            }

            paramType == "bytes" -> {
                try {
                    val offsetBytes = BigInteger(
                        chunks[startIndex],
                        16
                    ).toInt()
                    val offsetChunks = offsetBytes / 32

                    if (offsetChunks >= chunks.size) {
                        return Pair(
                            JsonPrimitive("0x"),
                            1
                        )
                    }

                    val length = BigInteger(
                        chunks[offsetChunks],
                        16
                    ).toInt()

                    if (length == 0) {
                        Pair(
                            JsonPrimitive("0x"),
                            1
                        )
                    } else {
                        val dataStartChunk = offsetChunks + 1
                        val chunksNeeded = (length + 31) / 32
                        val hexData = StringBuilder()

                        for (i in 0 until chunksNeeded) {
                            val chunkIndex = dataStartChunk + i
                            if (chunkIndex < chunks.size) {
                                hexData.append(chunks[chunkIndex])
                            }
                        }

                        val requiredHexLength = length * 2
                        val actualData = if (hexData.length >= requiredHexLength) {
                            hexData.substring(
                                0,
                                requiredHexLength
                            )
                        } else {
                            hexData.toString()
                        }

                        val result = if (actualData.isEmpty()) "0x" else "0x$actualData"
                        Pair(
                            JsonPrimitive(result),
                            1
                        )
                    }
                } catch (e: Exception) {
                    Timber.e(e)
                    Pair(
                        JsonPrimitive("0x"),
                        1
                    )
                }
            }

            paramType.startsWith("bytes") && paramType.length > 5 -> {
                try {
                    val sizeStr = paramType.substring(5)
                    if (sizeStr.toIntOrNull() != null) {
                        val size = sizeStr.toInt()
                        val chunk = chunks[startIndex]

                        val hexLength = size * 2
                        val actualData = if (chunk.length >= hexLength) {
                            chunk.substring(
                                0,
                                hexLength
                            )
                        } else {
                            chunk
                        }

                        Pair(
                            JsonPrimitive("0x$actualData"),
                            1
                        )
                    } else {
                        Pair(
                            JsonPrimitive("0x" + chunks[startIndex]),
                            1
                        )
                    }
                } catch (e: Exception) {
                    Timber.e(e)
                    Pair(
                        JsonPrimitive("0x" + chunks[startIndex]),
                        1
                    )
                }
            }

            paramType == "string" -> {
                try {
                    val offsetBytes = BigInteger(
                        chunks[startIndex],
                        16
                    ).toInt()
                    val offsetChunks = offsetBytes / 32

                    if (offsetChunks >= chunks.size) {
                        return Pair(
                            JsonPrimitive(""),
                            1
                        )
                    }

                    val length = BigInteger(
                        chunks[offsetChunks],
                        16
                    ).toInt()

                    if (length == 0) {
                        Pair(
                            JsonPrimitive(""),
                            1
                        )
                    } else {
                        val dataStartChunk = offsetChunks + 1
                        val chunksNeeded = (length + 31) / 32
                        val hexData = StringBuilder()

                        for (i in 0 until chunksNeeded) {
                            val chunkIndex = dataStartChunk + i
                            if (chunkIndex < chunks.size) {
                                hexData.append(chunks[chunkIndex])
                            }
                        }

                        val hexLength = length * 2
                        val hexString = if (hexData.length >= hexLength) {
                            hexData.substring(
                                0,
                                hexLength
                            )
                        } else {
                            hexData.toString()
                        }

                        val stringValue = try {
                            String(hexString.chunked(2).map { it.toInt(16).toByte() }.toByteArray())
                        } catch (e: Exception) {
                            Timber.e(e)
                            ""
                        }

                        Pair(
                            JsonPrimitive(stringValue),
                            1
                        )
                    }
                } catch (e: Exception) {
                    Timber.e(e)
                    Pair(
                        JsonPrimitive(""),
                        1
                    )
                }
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

            paramType.startsWith("int") -> {
                val value = BigInteger(
                    chunks[startIndex],
                    16
                )
                Pair(
                    JsonPrimitive(value.toString()),
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

            paramType == "bytes32" -> {
                val value = "0x" + chunks[startIndex]
                Pair(
                    JsonPrimitive(value),
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

    private fun decodeParameterInTuple(
        paramType: String,
        chunks: List<String>,
        currentIndex: Int,
        tupleStartByte: Int
    ): Pair<JsonElement, Int> {
        return when (paramType) {
            "bytes" -> {
                try {
                    val relativeOffset = BigInteger(
                        chunks[currentIndex],
                        16
                    ).toInt()
                    val absoluteByteOffset = tupleStartByte + relativeOffset
                    val absoluteChunkIndex = absoluteByteOffset / 32

                    if (absoluteChunkIndex >= chunks.size) {
                        return Pair(
                            JsonPrimitive("0x"),
                            1
                        )
                    }

                    val length = BigInteger(
                        chunks[absoluteChunkIndex],
                        16
                    ).toInt()

                    if (length == 0) {
                        Pair(
                            JsonPrimitive("0x"),
                            1
                        )
                    } else {
                        val dataStartChunk = absoluteChunkIndex + 1
                        val chunksNeeded = (length + 31) / 32
                        val hexData = StringBuilder()

                        for (i in 0 until chunksNeeded) {
                            val chunkIndex = dataStartChunk + i
                            if (chunkIndex < chunks.size) {
                                hexData.append(chunks[chunkIndex])
                            }
                        }

                        val requiredHexLength = length * 2
                        val actualData = if (hexData.length >= requiredHexLength) {
                            hexData.substring(
                                0,
                                requiredHexLength
                            )
                        } else {
                            hexData.toString()
                        }

                        val result = if (actualData.isEmpty()) "0x" else "0x$actualData"
                        Pair(
                            JsonPrimitive(result),
                            1
                        )
                    }
                } catch (e: Exception) {
                    Timber.e(e)
                    Pair(
                        JsonPrimitive("0x"),
                        1
                    )
                }
            }

            else -> decodeParameter(
                paramType,
                chunks,
                currentIndex
            )
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