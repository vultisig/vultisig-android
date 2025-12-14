package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.utils.bodyOrThrow
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.appendPathSegments
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import javax.inject.Inject
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

interface CircleApi {
    suspend fun createScAccount(owner: String): String

    suspend fun getScAccount(vaultOwnerAddress: String)
}

internal class CircleApiImpl @Inject constructor(
    private val httpClient: HttpClient
): CircleApi {
    @OptIn(ExperimentalUuidApi::class)
    override suspend fun createScAccount(owner: String): String {
        val requestId = Uuid.random().toString()

        return httpClient.post(CIRCLE_URL) {
            header("Content-Type", "application/json")
            url {
                appendPathSegments("/create")
            }
            setBody(CreateWalletJson(owner = owner, key = requestId))
        }.body<String>()
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
    @SerialName("name")
    val name: String = "Vultisig Wallet",
    @SerialName("owner")
    val owner: String = "",
    @SerialName("account_type")
    val accountType: String = "SCA",
    @SerialName("idempotency_key")
    val key: String ="",
)