package com.vultisig.wallet.data.di

import com.vultisig.wallet.BuildConfig
import com.vultisig.wallet.data.blockchain.cosmos.qbtc.claim.QbtcProofHttpClient
import com.vultisig.wallet.data.networkutils.HttpClientConfigurator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.serialization.kotlinx.json.json
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.Dispatcher
import timber.log.Timber

@Module
@InstallIn(SingletonComponent::class)
internal interface NetworkModule {

    companion object {

        @Provides
        @Singleton
        fun provideHttpClient(baseConfig: HttpClientConfigurator): HttpClient =
            HttpClient(OkHttp) {
                baseConfig.configure(this)

                install(HttpCache)

                engine {
                    config {
                        connectTimeout(15, TimeUnit.SECONDS)
                        readTimeout(15, TimeUnit.SECONDS)
                        writeTimeout(15, TimeUnit.SECONDS)

                        retryOnConnectionFailure(true)
                        if (BuildConfig.DEBUG) {
                            addInterceptor { chain ->
                                val request = chain.request()
                                chain.proceed(request).also { response ->
                                    // The request URL can contain backup codes and session IDs.
                                    Timber.d("HTTP %s -> %d", request.method, response.code)
                                }
                            }
                        }

                        // Most chains share one proxy host; OkHttp's default allows 5 at a time.
                        dispatcher(
                            Dispatcher().apply { maxRequestsPerHost = MAX_REQUESTS_PER_HOST }
                        )
                    }
                }
            }

        /**
         * Dedicated client for the QBTC proof service. Proof generation blocks for up to ~5
         * minutes, so the read/write timeout is far longer than the shared client's, and there is
         * no auto-retry — a timeout means the prover genuinely didn't finish, and the claim flow
         * surfaces it for an explicit user retry rather than silently re-running a 5-minute prove.
         */
        @Provides
        @Singleton
        @QbtcProofHttpClient
        fun provideQbtcProofHttpClient(json: Json): HttpClient =
            HttpClient(OkHttp) {
                install(ContentNegotiation) { json(json, ContentType.Any) }

                engine {
                    config {
                        connectTimeout(15, TimeUnit.SECONDS)
                        readTimeout(PROOF_SERVICE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                        writeTimeout(PROOF_SERVICE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                        // On by default in OkHttp; disable so a slow, non-idempotent /prove
                        // is never implicitly retried (the "no auto-retry" intent above).
                        retryOnConnectionFailure(false)
                    }
                }
            }

        private const val PROOF_SERVICE_TIMEOUT_SECONDS = 300L
        private const val MAX_REQUESTS_PER_HOST = 16
    }
}
