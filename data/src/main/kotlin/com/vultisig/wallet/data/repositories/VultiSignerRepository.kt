package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.api.VultiSignerApi
import com.vultisig.wallet.data.api.models.signer.JoinKeygenRequestJson
import com.vultisig.wallet.data.api.models.signer.JoinKeysignRequestJson
import javax.inject.Inject

interface VultiSignerRepository {

    suspend fun joinKeygen(
        request: JoinKeygenRequestJson,
    )

    suspend fun joinKeysign(
        request: JoinKeysignRequestJson,
    )

    suspend fun hasFastSign(
        publicKeyEcdsa: String,
    ): Boolean

}

internal class VultiSignerRepositoryImpl @Inject constructor(
    private val api: VultiSignerApi,
) : VultiSignerRepository {

    override suspend fun joinKeygen(
        request: JoinKeygenRequestJson,
    ) {
        api.joinKeygen(request)
    }

    override suspend fun joinKeysign(
        request: JoinKeysignRequestJson,
    ) {
        api.joinKeysign(request)
    }

    override suspend fun hasFastSign(
        publicKeyEcdsa: String
    ): Boolean {
        return try {
            api.exist(publicKeyEcdsa)
            true
        } catch (e: Exception) {
            false
        }
    }

}