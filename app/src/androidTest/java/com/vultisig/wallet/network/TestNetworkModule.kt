package com.vultisig.wallet.network

import com.vultisig.wallet.data.di.NetworkModule
import com.vultisig.wallet.data.networkutils.NetworkStateInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.ktor.util.appendIfNameAbsent
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [NetworkModule::class]
)
object TestNetworkModule {

    @Provides
    @Singleton
    fun provideFaultyInterceptor(): FaultyInterceptor = FaultyInterceptor()

    @Provides
    @Singleton
    fun provideHttpClient(
        faultyInterceptor: FaultyInterceptor,
        networkStateInterceptor: NetworkStateInterceptor,
        json: Json,
    ): HttpClient = HttpClient(OkHttp) {

        install(DefaultRequest.Plugin) {
            headers.appendIfNameAbsent(HttpHeaders.ContentType, "application/json")
        }
        install(ContentNegotiation) {
            json(Json, ContentType.Any)
        }

        install(HttpRequestRetry) {
            retryOnServerErrors(maxRetries = 3)
            exponentialDelay()
            retryIf { request, response ->
                !response.status.isSuccess() && request.method in listOf(
                    HttpMethod.Get, HttpMethod.Head, HttpMethod.Options
                )
            }
        }
        engine {
            config {
                addInterceptor(networkStateInterceptor)

                // This ensures the exception happens "downstream" from the logic interceptor
                addInterceptor { chain ->
                    faultyInterceptor.intercept(chain)
                }
            }
        }
    }
}
