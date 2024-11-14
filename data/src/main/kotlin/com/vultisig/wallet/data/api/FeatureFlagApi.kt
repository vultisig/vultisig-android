package com.vultisig.wallet.data.api


import com.vultisig.wallet.data.api.models.FeatureFlagJson
import com.vultisig.wallet.data.api.utils.throwIfUnsuccessful
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import javax.inject.Inject

interface FeatureFlagApi {
    suspend fun getFeatureFlag(): FeatureFlagJson
}

internal class FeatureFlagApiImpl @Inject constructor(
    private val http: HttpClient,
) : FeatureFlagApi {

    override suspend fun getFeatureFlag(): FeatureFlagJson =
        http.get("https://api.vultisig.com/feature/release.json")
            .throwIfUnsuccessful()
            .body<FeatureFlagJson>()

}