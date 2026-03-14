package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.utils.NetworkException
import com.vultisig.wallet.data.utils.bodyOrThrow
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import javax.inject.Inject
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

internal class VerifierApiImpl @Inject constructor(private val http: HttpClient) : VerifierApi {

    override suspend fun authenticate(request: VerifierAuthRequest): VerifierAuthResponse {
        val envelope: VerifierApiEnvelope =
            http
                .post("$BASE_URL/auth") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }
                .bodyOrThrow()
        val errorMsg = envelope.error?.message.orEmpty()
        return envelope.data ?: throw NetworkException(envelope.status, "Auth failed: $errorMsg")
    }

    override suspend fun refreshToken(refreshToken: String): VerifierAuthResponse {
        val envelope: VerifierApiEnvelope =
            http
                .post("$BASE_URL/auth/refresh") {
                    contentType(ContentType.Application.Json)
                    setBody(RefreshTokenRequest(refreshToken))
                }
                .bodyOrThrow()
        val errorMsg = envelope.error?.message.orEmpty()
        return envelope.data ?: throw NetworkException(envelope.status, "Refresh failed: $errorMsg")
    }

    override suspend fun validateToken(accessToken: String) {
        val response =
            http.get("$BASE_URL/auth/me") {
                header(HttpHeaders.Authorization, "Bearer $accessToken")
            }
        if (!response.status.isSuccess()) {
            throw NetworkException(
                response.status.value,
                "Token validation failed: ${response.status}",
            )
        }
    }

    override suspend fun revokeAllTokens(accessToken: String) {
        val response =
            http.delete("$BASE_URL/auth/tokens/all") {
                header(HttpHeaders.Authorization, "Bearer $accessToken")
                contentType(ContentType.Application.Json)
                setBody("{}")
            }
        if (!response.status.isSuccess()) {
            throw NetworkException(
                response.status.value,
                "Revoke tokens failed: ${response.status}",
            )
        }
    }

    companion object {
        private const val BASE_URL = "https://verifier.vultisig.com"
    }
}

@Serializable
private data class RefreshTokenRequest(@SerialName("refresh_token") val refreshToken: String)
