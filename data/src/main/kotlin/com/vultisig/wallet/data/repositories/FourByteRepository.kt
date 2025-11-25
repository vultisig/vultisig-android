package com.vultisig.wallet.data.repositories

import com.fasterxml.jackson.core.util.DefaultIndenter
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.vultisig.wallet.data.api.FourByteApi
import com.vultisig.wallet.data.common.stripHexPrefix
import com.vultisig.wallet.data.utils.Numeric
import io.ethers.abi.AbiFunction
import io.ethers.core.types.Bytes
import javax.inject.Inject

interface FourByteRepository {
    suspend fun decodeFunction(memo: String): String?
    fun decodeFunctionArgs(functionSignature: String, memo: String): String?
}

internal class FourByteRepositoryImpl @Inject constructor(
    private val fourByteApi: FourByteApi,
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
            decoded.toPrettyJson()
        } catch (_: Exception) {
            null
        }
    }

    private fun Array<Any>.toPrettyJson(): String {
        val mapper = jacksonObjectMapper().apply {
            enable(SerializationFeature.INDENT_OUTPUT)
        }

        val printer = DefaultPrettyPrinter().apply {
            indentArraysWith(DefaultIndenter.SYSTEM_LINEFEED_INSTANCE)
        }

        return mapper.writer(printer).writeValueAsString(this)
    }
}