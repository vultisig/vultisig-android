package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.api.VultiSignerApi
import com.vultisig.wallet.data.api.models.signer.JoinKeygenRequestJson
import com.vultisig.wallet.data.api.models.signer.JoinKeysignRequestJson
import com.vultisig.wallet.data.api.models.signer.JoinReshareRequestJson
import javax.inject.Inject

interface VultiSignerRepository {

    suspend fun joinKeygen(
        request: JoinKeygenRequestJson,
    )

    suspend fun joinKeysign(
        request: JoinKeysignRequestJson,
    )

    suspend fun joinReshare(
        request: JoinReshareRequestJson,
    )

    suspend fun isPasswordValid(
        publicKeyEcdsa: String,
        password: String,
    ): Boolean

    suspend fun hasFastSign(
        publicKeyEcdsa: String,
    ): Boolean

    suspend fun isBackupCodeValid(
        publicKeyEcdsa: String,
        code: String,
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

    override suspend fun joinReshare(request: JoinReshareRequestJson) {
        api.joinReshare(request)
    }

    override suspend fun isPasswordValid(
        publicKeyEcdsa: String,
        password: String,
    ): Boolean = try {
        api.get(publicKeyEcdsa, password)
        true
    } catch (e: Exception) {
        false
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

    override suspend fun isBackupCodeValid(publicKeyEcdsa: String, code: String): Boolean {
        return try {
            api.verifyBackupCode(publicKeyEcdsa, code)
            true
        } catch (e: Exception) {
            false
        }
    }
}