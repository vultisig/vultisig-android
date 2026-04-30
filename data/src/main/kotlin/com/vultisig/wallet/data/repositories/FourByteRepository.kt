package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.api.FourByteApi
import com.vultisig.wallet.data.common.stripHexPrefix
import com.vultisig.wallet.data.utils.convertParameter
import com.vultisig.wallet.data.utils.decodeGeneric
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

interface FourByteRepository {
    suspend fun decodeFunction(memo: String): String?

    fun decodeFunctionArgs(functionSignature: String, memo: String): String?
}

internal class FourByteRepositoryImpl
@Inject
constructor(private val fourByteApi: FourByteApi, @param:PrettyJson private val json: Json) :
    FourByteRepository {
    override suspend fun decodeFunction(memo: String): String? {
        val hash = memo.stripHexPrefix()
        if (hash.length < 8) return null
        val selector = hash.substring(0, 8)
        return try {
            EvmCommonSelectors.lookup(selector) ?: fourByteApi.decodeFunction(selector)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    override fun decodeFunctionArgs(functionSignature: String, memo: String): String? {
        return try {
            val decodeGeneric = decodeGeneric(memo, functionSignature)
            convertDecodedCallToPrettyArray(decodeGeneric)
        } catch (_: Exception) {
            null
        }
    }

    private fun convertDecodedCallToPrettyArray(decoded: String): String {
        val root = json.parseToJsonElement(decoded).jsonObject
        val inputs = root["inputs"]?.jsonArray.orEmpty()
        val transformed = inputs.map { input -> convertParameter(input.jsonObject) }
        return json.encodeToString(JsonArray(transformed))
    }
}
