package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.api.FeatureFlagApi
import com.vultisig.wallet.data.api.models.FeatureFlagJson
import javax.inject.Inject
import timber.log.Timber

/** Reads remote feature flags and falls back to safe defaults when loading fails. */
interface FeatureFlagRepository {
    suspend fun getFeatureFlags(): FeatureFlagJson
}

internal class FeatureFlagRepositoryImpl
@Inject
constructor(private val featureFlagApi: FeatureFlagApi) : FeatureFlagRepository {
    override suspend fun getFeatureFlags(): FeatureFlagJson =
        runCatching { featureFlagApi.getFeatureFlags() }
            .onFailure { Timber.w(it, "Failed to load feature flags, using safe defaults") }
            .getOrDefault(FeatureFlagJson())
}
