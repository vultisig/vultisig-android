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
import org.web3j.abi.FunctionReturnDecoder
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.*
import org.web3j.abi.datatypes.generated.Bytes32
import org.web3j.abi.datatypes.generated.Uint128
import org.web3j.abi.datatypes.generated.Uint256

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
            return null
        }
    }

    override fun decodeFunctionArgs(functionSignature: String, memo: String): String? {
        try {

            val paramsStrings = functionSignature.parseNestedSignature()

//            val paramsString = functionSignature.substringAfter("(").substringBefore(")")

            //((address,address,uint256,address,uint256,string,uint256,uint256,address,uint128,uint128,bytes32),bytes)
            val StringList = paramsStrings.map { paramTypes ->
                val decodedValues = getTypedReference(
                    paramTypes,
                    memo
                )
                decodeTypedReferences(decodedValues)?.let {
                    json.encodeToString(
                        JsonElement.serializer(),
                        JsonArray(it)
                    )
                }
            }
            return json.encodeToString(
                JsonElement.serializer(),
                JsonArray(StringList.map { JsonPrimitive(it) })
            )
//            val paramTypes = paramsString.split(",").map { it.trim() }
//            val decodedValues = getTypedReference(
//                paramTypes,
//                memo
//            )
//            return decodeTypedReferences(decodedValues)?.let {
//                json.encodeToString(
//                    JsonElement.serializer(),
//                    JsonArray(it)
//                )

        } catch (e: Exception) {
            Timber.e(
                e,
                "decode function args error"
            )
            return null
        }
    }


    private fun decodeTypedReferences(decodedValues: MutableList<Type<Any>>?): List<JsonElement>? =
        decodedValues?.map {
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
                        else -> JsonPrimitive(v.toString())
                    }
                })

                else -> {
                    JsonPrimitive(it.toString())
                }
            }
        }


    private fun getTypedReference(
        paramTypes: List<String>,
        memo: String,
    ): MutableList<Type<Any>>? {
        val typeReferences: List<TypeReference<*>?> = paramTypes.mapNotNull { param ->
            // Remove any extra whitespace and handle nested types
            val cleanedParam = param.replace(
                "\\s+".toRegex(),
                ""
            )
            when {
                cleanedParam == "bytes" -> object : TypeReference<DynamicBytes>() {}
                cleanedParam == "bytes[]" -> object : TypeReference<DynamicArray<DynamicBytes>>() {}
                cleanedParam == "address" -> object : TypeReference<Address>() {}
                cleanedParam == "uint256" -> object : TypeReference<Uint256>() {}
                cleanedParam == "string" -> object : TypeReference<Utf8String>() {}
                cleanedParam == "bool" -> object : TypeReference<Bool>() {}
                cleanedParam == "bytes32" -> object : TypeReference<Bytes32>() {}
                cleanedParam == "uint128" -> object : TypeReference<Uint128>() {}
                cleanedParam == "uint256[]" -> object : TypeReference<DynamicArray<Uint256>>() {}
                cleanedParam == "address[]" -> object : TypeReference<DynamicArray<Address>>() {}
//                cleanedParam.startsWith("(") && cleanedParam.endsWith(")") -> {
//                    // Handle nested tuple types
//                    object : TypeReference<Tuple>() {}
//                }
                else -> null
            }
        }

        val encodedData = memo.substring(10)
        val decodedValues = FunctionReturnDecoder.decode(
            encodedData,
            typeReferences as MutableList<TypeReference<Type<Any>>>?
        )
        return decodedValues
    }

    private fun String.parseNestedSignature(): List<List<String>> {


        val parts = mutableListOf<String>()
        var currentPart = StringBuilder()
        var parenthesesLevel = 0
//mintstadd((address,address,uint256,address,uint256,string,uint256,uint256,address,uint128,uint128,bytes32),bytes)
        val cleanedSignature = this.substringAfter(
            "(",
            this
        )
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

        parts.add(currentPart.toString().trim())

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
}

