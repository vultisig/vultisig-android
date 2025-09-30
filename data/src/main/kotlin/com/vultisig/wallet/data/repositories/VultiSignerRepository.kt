package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.api.VultiSignerApi
import com.vultisig.wallet.data.api.models.signer.JoinKeygenRequestJson
import com.vultisig.wallet.data.api.models.signer.JoinKeysignRequestJson
import com.vultisig.wallet.data.api.models.signer.JoinReshareRequestJson
import com.vultisig.wallet.data.api.models.signer.MigrateRequest
import javax.inject.Inject

sealed class PasswordCheckResult {
    object Valid : PasswordCheckResult()
    object Invalid : PasswordCheckResult()
    data class NetworkError(val message: String = "No internet connection") : PasswordCheckResult()
    data class Error(val message: String) : PasswordCheckResult()
}

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

    suspend fun migrate(
        request: MigrateRequest,
    )
    suspend fun isPasswordValid(
        publicKeyEcdsa: String,
        password: String,
    ): Boolean
    
    suspend fun checkPassword(
        publicKeyEcdsa: String,
        password: String,
    ): PasswordCheckResult

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

    override suspend fun migrate(request: MigrateRequest) {
        api.migrate(request)
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
    
    override suspend fun checkPassword(
        publicKeyEcdsa: String,
        password: String,
    ): PasswordCheckResult = try {
        api.get(publicKeyEcdsa, password)
        PasswordCheckResult.Valid
    } catch (e: Exception) {
        when {
            e.message?.contains("401") == true || 
            e.message?.contains("403") == true || 
            e.message?.contains("Unauthorized", ignoreCase = true) == true -> 
                PasswordCheckResult.Invalid
                
            e.message?.contains("Unable to resolve host", ignoreCase = true) == true ||
            e.message?.contains("UnknownHost", ignoreCase = true) == true ||
            e is java.net.UnknownHostException ||
            e is java.io.IOException && e.message?.contains("Network", ignoreCase = true) == true ->
                PasswordCheckResult.NetworkError()
                
            e.message?.contains("timeout", ignoreCase = true) == true ||
            e is java.net.SocketTimeoutException ->
                PasswordCheckResult.NetworkError("Connection timeout")
                
            else -> PasswordCheckResult.Error(e.message ?: "Unknown error")
        }
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