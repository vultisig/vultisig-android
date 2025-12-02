package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.api.FourByteApi
import com.vultisig.wallet.data.common.stripHexPrefix
import com.vultisig.wallet.data.utils.convertParameter
import com.vultisig.wallet.data.utils.decodeGeneric
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
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
            val decodeGeneric = decodeGeneric(memo, functionSignature)
            if (!decodeGeneric.startsWith("Error decoding")) {
                val prettyArrayJson = convertDecodedCallToPrettyArray(decodeGeneric)
                prettyArrayJson
            } else {
                decodeGeneric
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun convertDecodedCallToPrettyArray(decoded: String): String {
        val root = json.parseToJsonElement(decoded).jsonObject
        val inputs = root["inputs"]?.jsonArray.orEmpty()
        val transformed = inputs.map { input ->
            convertParameter(input.jsonObject)
        }
        return json.encodeToString(JsonArray(transformed))
    }
}