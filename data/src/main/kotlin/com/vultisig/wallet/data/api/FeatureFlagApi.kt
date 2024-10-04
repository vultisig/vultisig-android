package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.api.utils.throwIfUnsuccessful
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject

interface FeatureFlagApi {
    suspend fun isFeatureEnabled(feature: String): Boolean
}

internal class FeatureFlagApiImpl @Inject constructor(
    private val http: HttpClient,
) : FeatureFlagApi {
    override suspend fun isFeatureEnabled(feature: String): Boolean {
        val resp = http.get("https://api.vultisig.com/feature/release.json").throwIfUnsuccessful()
        val responseText = resp.bodyAsText()
        val map = Json.parseToJsonElement(responseText).jsonObject.toMap()
        return map[feature]?.jsonPrimitive?.boolean == true
    }
}