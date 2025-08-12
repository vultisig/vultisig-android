package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.api.FourByteApi
import com.vultisig.wallet.data.common.stripHexPrefix
import com.vultisig.wallet.data.utils.Numeric
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import javax.inject.Inject
import org.web3j.abi.FunctionReturnDecoder
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.*
import org.web3j.abi.datatypes.generated.Uint256
import timber.log.Timber

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
            val hash = memo.stripHexPrefix().substring(0, 8)
            return fourByteApi.decodeFunction(hash)
        } catch (e: Exception) {
            return null
        }
    }

    override fun decodeFunctionArgs(functionSignature: String, memo: String): String? {
        try {
            val paramsString = functionSignature.substringAfter("(").substringBefore(")")
            val paramTypes = paramsString.split(",").map { it.trim() }
            val decodedValues = getTypedReference(paramTypes, memo)
            return decodeTypedReferences(decodedValues)?.let {
                json.encodeToString(JsonElement.serializer(), JsonArray(it))
            }
        } catch (e: Exception) {
            Timber.e(e,"decode function args error")
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
                is DynamicBytes -> JsonPrimitive(Numeric.toHexString(it.value as ByteArray))
                is DynamicArray<*> -> JsonArray(it.value.map { v ->
                    JsonPrimitive(Numeric.toHexString(v.value as ByteArray))
                })
                else -> JsonPrimitive(it.toString())
            }
        }


    private fun getTypedReference(
        paramTypes: List<String>,
        memo: String,
    ): MutableList<Type<Any>>? {
        val typeReferences: List<TypeReference<*>?> = paramTypes.mapNotNull {
            when (it) {
                "bytes" -> object : TypeReference<DynamicBytes>() {}
                "bytes[]" -> object : TypeReference<DynamicArray<DynamicBytes>>() {}
                "address" -> object : TypeReference<Address>() {}
                "uint256" -> object : TypeReference<Uint256>() {}
                "string" -> object : TypeReference<Utf8String>() {}
                "bool" -> object : TypeReference<Bool>() {}
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
}