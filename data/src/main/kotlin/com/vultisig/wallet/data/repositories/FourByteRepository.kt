package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.api.FourByteApi
import com.vultisig.wallet.data.common.stripHexPrefix
import javax.inject.Inject

interface FourByteRepository {
    suspend fun decodeFunction(memo: String): String?
}

internal class FourByteRepositoryImpl @Inject constructor(
    private val fourByteApi: FourByteApi,
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
}