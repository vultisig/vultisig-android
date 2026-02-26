package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.api.models.signer.CreateMldsaVaultRequestJson
import com.vultisig.wallet.data.api.models.signer.JoinKeyImportRequest
import com.vultisig.wallet.data.api.models.signer.JoinKeygenRequestJson
import com.vultisig.wallet.data.api.models.signer.JoinKeysignRequestJson
import com.vultisig.wallet.data.api.models.signer.JoinReshareRequestJson
import com.vultisig.wallet.data.api.models.signer.MigrateRequest
import com.vultisig.wallet.data.api.utils.throwIfUnsuccessful
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

internal interface VultiSignerApi {

    suspend fun joinKeygen(
        request: JoinKeygenRequestJson,
    )

    suspend fun createMldsa(
        request: CreateMldsaVaultRequestJson,
    )
    suspend fun joinKeyImport(
        request: JoinKeyImportRequest,
    )
    suspend fun joinKeysign(
        requestJson: JoinKeysignRequestJson,
    )

    suspend fun joinReshare(
        request: JoinReshareRequestJson,
    )

    suspend fun get(
        publicKeyEcdsa: String,
        password: String,
    )

    suspend fun exist(
        publicKeyEcdsa: String,
    )

    suspend fun verifyBackupCode(
        publicKeyEcdsa: String,
        code: String,
    )

    suspend fun migrate(
        request: MigrateRequest,
    )

}

internal class VultiSignerApiImpl @Inject constructor(
    private val http: HttpClient,
) : VultiSignerApi {

    override suspend fun joinKeygen(
        request: JoinKeygenRequestJson,
    ) {
        http.post("$URL/create") {
            setBody(request)
        }.throwIfUnsuccessful()
    }

    override suspend fun createMldsa(
        request: CreateMldsaVaultRequestJson,
    ) {
        http.post("$URL/mldsa") {
            setBody(request)
        }.throwIfUnsuccessful()
    }

    override suspend fun joinKeyImport(request: JoinKeyImportRequest) {
        http.post("$URL/import") {
            setBody(request)
        }.throwIfUnsuccessful()
    }

    override suspend fun joinKeysign(
        requestJson: JoinKeysignRequestJson,
    ) {
        http.post("$URL/sign") {
            setBody(requestJson)
        }.throwIfUnsuccessful()
    }

    override suspend fun joinReshare(
        request: JoinReshareRequestJson
    ) {
        http.post("$URL/reshare") {
            setBody(request)
        }.throwIfUnsuccessful()
    }

    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun get(
        publicKeyEcdsa: String,
        password: String,
    ) {
        http.get("$URL/get/$publicKeyEcdsa") {
            header("x-password", Base64.encode(password.toByteArray(StandardCharsets.UTF_8)))
        }.throwIfUnsuccessful()
    }

    override suspend fun exist(publicKeyEcdsa: String) {
        http.get("$URL/exist/$publicKeyEcdsa")
            .throwIfUnsuccessful()
    }

    override suspend fun verifyBackupCode(publicKeyEcdsa: String, code: String) {
        http.get("$URL/verify/$publicKeyEcdsa/$code")
            .throwIfUnsuccessful()
    }

    override suspend fun migrate(request: MigrateRequest) {
        http.post("$URL/migrate") {
            setBody(request)
        }.throwIfUnsuccessful()
    }

    companion object {
        private const val URL = "https://api.vultisig.com/vault"
    }

}