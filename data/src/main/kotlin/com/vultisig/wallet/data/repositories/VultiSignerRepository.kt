package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.api.VultiSignerApi
import com.vultisig.wallet.data.api.models.signer.BatchKeygenRequestJson
import com.vultisig.wallet.data.api.models.signer.CreateMldsaVaultRequestJson
import com.vultisig.wallet.data.api.models.signer.JoinKeyImportRequest
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

/** Result of a server backup request to the VultiSigner `/vault/resend` endpoint. */
sealed class ServerBackupResult {
    data object Success : ServerBackupResult()

    data class Error(val type: ErrorType) : ServerBackupResult()

    enum class ErrorType {
        INVALID_PASSWORD,
        NETWORK_ERROR,
        TOO_MANY_REQUESTS,
        BAD_REQUEST,
        UNKNOWN,
    }
}

interface VultiSignerRepository {

    suspend fun joinKeygen(request: JoinKeygenRequestJson)

    suspend fun joinBatchKeygen(request: BatchKeygenRequestJson)

    suspend fun createMldsa(request: CreateMldsaVaultRequestJson)

    suspend fun joinKeyImport(request: JoinKeyImportRequest)

    suspend fun joinKeysign(request: JoinKeysignRequestJson)

    suspend fun joinReshare(request: JoinReshareRequestJson)

    suspend fun migrate(request: MigrateRequest)

    suspend fun isPasswordValid(publicKeyEcdsa: String, password: String): Boolean

    suspend fun checkPassword(publicKeyEcdsa: String, password: String): PasswordCheckResult

    suspend fun hasFastSign(publicKeyEcdsa: String): Boolean

    suspend fun isBackupCodeValid(publicKeyEcdsa: String, code: String): Boolean

    /**
     * Requests the server to resend the encrypted vault backup share to [email]. The [password] is
     * used server-side to decrypt and re-encrypt the backup. Returns [ServerBackupResult]
     * indicating success or a typed error.
     */
    suspend fun requestServerBackup(
        publicKeyEcdsa: String,
        email: String,
        password: String,
    ): ServerBackupResult
}

internal class VultiSignerRepositoryImpl @Inject constructor(private val api: VultiSignerApi) :
    VultiSignerRepository {

    override suspend fun joinKeygen(request: JoinKeygenRequestJson) {
        api.joinKeygen(request)
    }

    override suspend fun joinBatchKeygen(request: BatchKeygenRequestJson) {
        api.joinBatchKeygen(request)
    }

    override suspend fun createMldsa(request: CreateMldsaVaultRequestJson) {
        api.createMldsa(request)
    }

    override suspend fun joinKeyImport(request: JoinKeyImportRequest) {
        api.joinKeyImport(request)
    }

    override suspend fun joinKeysign(request: JoinKeysignRequestJson) {
        api.joinKeysign(request)
    }

    override suspend fun joinReshare(request: JoinReshareRequestJson) {
        api.joinReshare(request)
    }

    override suspend fun migrate(request: MigrateRequest) {
        api.migrate(request)
    }

    override suspend fun isPasswordValid(publicKeyEcdsa: String, password: String): Boolean =
        try {
            api.get(publicKeyEcdsa, password)
            true
        } catch (e: Exception) {
            false
        }

    override suspend fun checkPassword(
        publicKeyEcdsa: String,
        password: String,
    ): PasswordCheckResult =
        try {
            api.get(publicKeyEcdsa, password)
            PasswordCheckResult.Valid
        } catch (e: Exception) {
            when {
                e.message?.contains("401") == true ||
                    e.message?.contains("403") == true ||
                    e.message?.contains("500") == true ||
                    e.message?.contains("Unauthorized", ignoreCase = true) == true ->
                    PasswordCheckResult.Invalid

                e.message?.contains("Unable to resolve host", ignoreCase = true) == true ||
                    e.message?.contains("UnknownHost", ignoreCase = true) == true ||
                    e is java.net.UnknownHostException ||
                    e is java.io.IOException &&
                        e.message?.contains("Network", ignoreCase = true) == true ->
                    PasswordCheckResult.NetworkError()

                e.message?.contains("timeout", ignoreCase = true) == true ||
                    e is java.net.SocketTimeoutException ->
                    PasswordCheckResult.NetworkError("Connection timeout")

                else -> PasswordCheckResult.Error(e.message ?: "Unknown error")
            }
        }

    override suspend fun hasFastSign(publicKeyEcdsa: String): Boolean {
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

    override suspend fun requestServerBackup(
        publicKeyEcdsa: String,
        email: String,
        password: String,
    ): ServerBackupResult =
        try {
            api.requestServerBackup(publicKeyEcdsa, email, password)
            ServerBackupResult.Success
        } catch (e: Exception) {
            val message = e.message.orEmpty()
            val errorType =
                when {
                    message.contains("401") ||
                        message.contains("403") ||
                        message.contains("Unauthorized", ignoreCase = true) ->
                        ServerBackupResult.ErrorType.INVALID_PASSWORD

                    message.contains("429") || message.contains("Too Many", ignoreCase = true) ->
                        ServerBackupResult.ErrorType.TOO_MANY_REQUESTS

                    message.contains("400") || message.contains("Bad Request", ignoreCase = true) ->
                        ServerBackupResult.ErrorType.BAD_REQUEST

                    e is java.net.UnknownHostException ||
                        e is java.net.SocketTimeoutException ||
                        message.contains("timeout", ignoreCase = true) ||
                        message.contains("Unable to resolve host", ignoreCase = true) ->
                        ServerBackupResult.ErrorType.NETWORK_ERROR

                    else -> ServerBackupResult.ErrorType.UNKNOWN
                }
            ServerBackupResult.Error(errorType)
        }
}
