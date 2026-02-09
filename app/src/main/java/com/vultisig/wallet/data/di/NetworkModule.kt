package com.vultisig.wallet.data.di

import com.vultisig.wallet.BuildConfig
import com.vultisig.wallet.data.networkutils.NetworkStateInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.ANDROID
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.ktor.util.appendIfNameAbsent
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal interface NetworkModule {

    companion object {

        @Provides
        @Singleton
        fun provideHttpClient(
            json: Json,
            networkStateInterceptor: NetworkStateInterceptor,
        ): HttpClient = HttpClient(OkHttp) {
            if (BuildConfig.DEBUG) {
                install(Logging) {
                   logger = Logger.ANDROID
                   level = LogLevel.ALL
                }
            }
            install(HttpCache)
            install(DefaultRequest.Plugin) {
               headers.appendIfNameAbsent(
                    HttpHeaders.ContentType, "application/json"
                )
            }
            install(ContentNegotiation) {
                json(json, ContentType.Any)
            }

            install(HttpRequestRetry) {
                retryOnServerErrors(maxRetries = 3)
                exponentialDelay()

                retryIf { request, response ->
                    !response.status.isSuccess() && request.method in listOf(
                        HttpMethod.Get,
                        HttpMethod.Head,
                        HttpMethod.Options
                    )
                }
            }
            engine {
                config {
                    connectTimeout(15, TimeUnit.SECONDS)
                    readTimeout(15, TimeUnit.SECONDS)
                    writeTimeout(15, TimeUnit.SECONDS)

                    retryOnConnectionFailure(
                        retryOnConnectionFailure = true
                    )
                    addInterceptor(networkStateInterceptor)
                }
            }
        }

    }

}