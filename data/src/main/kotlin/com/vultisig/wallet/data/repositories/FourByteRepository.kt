package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.api.FourByteApi
import com.vultisig.wallet.data.common.stripHexPrefix
import com.vultisig.wallet.data.utils.Numeric
import com.vultisig.wallet.data.utils.toJsonElement
import io.ethers.abi.AbiFunction
import io.ethers.core.types.Bytes
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
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
            val hash = memo.stripHexPrefix().substring(0, 8)
            return fourByteApi.decodeFunction(hash)
        } catch (e: Exception) {
            return null
        }
    }

    override fun decodeFunctionArgs(functionSignature: String, memo: String): String? {
        return try {
            val function = AbiFunction.parseSignature(functionSignature)
            val byteArray = Numeric.hexStringToByteArray(memo.stripHexPrefix())
            val encoded = Bytes(byteArray)
            val decoded = function.decodeCall(encoded)
            val jsonArray = decoded.toJsonElement()
            json.encodeToString(
                JsonElement.serializer(),
                jsonArray
            )
        } catch (_: Exception) {
            null
        }
    }

}