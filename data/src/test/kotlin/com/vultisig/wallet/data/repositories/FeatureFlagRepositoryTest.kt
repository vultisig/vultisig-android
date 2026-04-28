package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.api.FeatureFlagApi
import com.vultisig.wallet.data.api.models.FeatureFlagJson
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class FeatureFlagRepositoryTest {

    @BeforeEach
    fun resetOverrides() {
        FeatureFlagRepositoryImpl.tssBatchEnabledOverride = null
    }

    @Test
    fun `repository returns fetched flags`() = runTest {
        val repository =
            FeatureFlagRepositoryImpl(
                featureFlagApi =
                    FakeFeatureFlagApi(
                        listOf(
                            Result.success(
                                FeatureFlagJson(
                                    isEncryptGcmEnabled = true,
                                    isTssBatchEnabled = true,
                                )
                            )
                        )
                    )
            )

        val flags = repository.getFeatureFlags()

        assertEquals(true, flags.isEncryptGcmEnabled)
        assertEquals(true, flags.isTssBatchEnabled)
    }

    @Test
    fun `repository falls back to safe defaults when fetch fails`() = runTest {
        val repository =
            FeatureFlagRepositoryImpl(
                featureFlagApi =
                    FakeFeatureFlagApi(
                        listOf(Result.failure(IllegalStateException("network error")))
                    )
            )

        val flags = repository.getFeatureFlags()

        assertEquals(FeatureFlagJson(), flags)
    }

    @Test
    fun `repository fetches flags on each call`() = runTest {
        val api =
            FakeFeatureFlagApi(
                listOf(
                    Result.success(
                        FeatureFlagJson(isEncryptGcmEnabled = true, isTssBatchEnabled = false)
                    ),
                    Result.success(
                        FeatureFlagJson(isEncryptGcmEnabled = false, isTssBatchEnabled = true)
                    ),
                )
            )
        val repository = FeatureFlagRepositoryImpl(api)

        val first = repository.getFeatureFlags()
        val second = repository.getFeatureFlags()

        assertEquals(2, api.calls)
        assertEquals(false, first.isTssBatchEnabled)
        assertEquals(true, second.isTssBatchEnabled)
    }

    @Test
    fun `repository retries after a failed fetch instead of reusing defaults`() = runTest {
        val api =
            FakeFeatureFlagApi(
                listOf(
                    Result.failure(IllegalStateException("temporary failure")),
                    Result.success(FeatureFlagJson(isTssBatchEnabled = true)),
                )
            )
        val repository = FeatureFlagRepositoryImpl(api)

        val first = repository.getFeatureFlags()
        val second = repository.getFeatureFlags()

        assertEquals(FeatureFlagJson(), first)
        assertEquals(true, second.isTssBatchEnabled)
        assertEquals(2, api.calls)
    }

    private class FakeFeatureFlagApi(private val results: List<Result<FeatureFlagJson>>) :
        FeatureFlagApi {
        var calls: Int = 0
            private set

        override suspend fun getFeatureFlags(): FeatureFlagJson {
            calls += 1
            return results.getOrElse(calls - 1) { results.last() }.getOrThrow()
        }
    }
}
