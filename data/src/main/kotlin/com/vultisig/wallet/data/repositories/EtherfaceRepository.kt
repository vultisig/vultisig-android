package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.api.EtherfaceApi
import com.vultisig.wallet.data.common.stripHexPrefix
import javax.inject.Inject

interface EtherfaceRepository {
    suspend fun decodeFunction(memo: String): String?
}

internal class EtherfaceRepositoryImpl @Inject constructor(
    private val etherfaceApi: EtherfaceApi,
) : EtherfaceRepository {
    override suspend fun decodeFunction(memo: String): String? {
        if (memo.length < 8) return null
        try {
            val hash = memo.stripHexPrefix().substring(0, 8)
            return etherfaceApi.decodeFunction(hash)
        } catch (e: Exception) {
            return null
        }
    }
}