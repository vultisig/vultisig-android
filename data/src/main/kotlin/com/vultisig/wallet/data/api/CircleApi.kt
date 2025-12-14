package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.utils.bodyOrThrow
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.appendPathSegments
import kotlinx.serialization.Serializable
import javax.inject.Inject

interface CircleApi {
    suspend fun createScAccount(owner: String)

    suspend fun getScAccount(vaultOwnerAddress: String)
}

internal class CircleApiImpl @Inject constructor(
    private val httpClient: HttpClient
): CircleApi {
    override suspend fun createScAccount(owner: String) {
        httpClient.post(CIRCLE_URL) {
            header("Content-Type", "application/json")
            setBody(CreateWalletJson())
        }
    }

    override suspend fun getScAccount(vaultOwnerAddress: String) {
        httpClient.get(CIRCLE_URL) {
            header("Content-Type", "application/json")
            url {
                appendPathSegments("/wallet")
            }
            parameter("refId", vaultOwnerAddress)
        }.bodyOrThrow<Unit>()
    }

    private companion object {
        const val CIRCLE_URL = "https://api.vultisig.com/circle"
    }
}

@Serializable
internal data class CreateWalletJson(
    @Serializable
    val name: String = "Vultisig Wallet",
    @Serializable
    val owner: String = "",
    @Serializable
    val accountType: String = "SCA",
    @Serializable
    val key: String ="",
)