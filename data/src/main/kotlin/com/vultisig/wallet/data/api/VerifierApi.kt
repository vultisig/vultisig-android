package com.vultisig.wallet.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

interface VerifierApi {

    suspend fun authenticate(request: VerifierAuthRequest): VerifierAuthResponse

    suspend fun refreshToken(refreshToken: String): VerifierAuthResponse

    suspend fun validateToken(accessToken: String)

    suspend fun revokeAllTokens(accessToken: String)
}

@Serializable
data class VerifierAuthRequest(
    val message: String,
    val signature: String,
    @SerialName("chain_code_hex") val chainCodeHex: String,
    @SerialName("public_key") val publicKey: String,
)

@Serializable
data class VerifierAuthResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in") val expiresIn: Long,
)

@Serializable
data class VerifierApiEnvelope(
    val data: VerifierAuthResponse? = null,
    val status: Int = 0,
    val error: VerifierApiError? = null,
)

@Serializable data class VerifierApiError(val message: String = "")
