package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.api.FourByteApi
import com.vultisig.wallet.data.common.stripHexPrefix
import com.vultisig.wallet.data.utils.Numeric
import io.ethers.abi.AbiFunction
import io.ethers.core.types.Address
import io.ethers.core.types.Bytes
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import java.math.BigInteger
import javax.inject.Inject

interface FourByteRepository {
    suspend fun decodeFunction(memo: String): String?
    fun decodeFunctionArgs(functionSignature: String, memo: String): String?
}

internal class FourByteRepositoryImpl @Inject constructor(
    private val fourByteApi: FourByteApi,
    @param:PrettyJson private val json: Json,
) : FourByteRepository {
    override suspend fun decodeFunction(memo: String): String? {
        if (memo.length < 8) return null
        try {
            val hash = memo.stripHexPrefix().substring(0, 8)
            return fourByteApi.decodeFunction(hash)
        } catch (_: Exception) {
            return null
        }
    }

    override fun decodeFunctionArgs(functionSignature: String, memo: String): String? {
        return try {
            val function = AbiFunction.parseSignature(functionSignature)
            val byteArray = Numeric.hexStringToByteArray(memo.stripHexPrefix())
            val encoded = Bytes(byteArray)
            val decoded = function.decodeCall(encoded)
            json.encodeToString(
                JsonArray.serializer(),
                JsonArray(decoded.map { it.toJsonElement() })
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun Any.toJsonElement(): JsonElement {
        return when (this) {
            is String -> JsonPrimitive(this)
            is Boolean -> JsonPrimitive(this)
            is Number -> JsonPrimitive(this)
            is BigInteger -> JsonPrimitive(this.toString())

            // Handle Bytes type (assuming it has a way to convert to hex)
            is Bytes -> JsonPrimitive(this.toString())

            // Handle Address type (assuming it has a way to convert to hex/string)
            is Address -> JsonPrimitive(this.toString())

            // Handle arrays (including typed arrays like Array<Address>, Array<String>, etc.)
            is Array<*> -> JsonArray(this.map {
                it?.toJsonElement() ?: JsonNull
            })

            // Handle primitive arrays
            is IntArray -> JsonArray(this.map { JsonPrimitive(it) })
            is LongArray -> JsonArray(this.map { JsonPrimitive(it) })
            is BooleanArray -> JsonArray(this.map { JsonPrimitive(it) })

            // Fallback: convert to string
            else -> JsonPrimitive(this.toString())
        }
    }
}