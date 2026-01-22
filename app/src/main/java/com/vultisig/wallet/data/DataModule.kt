@file:OptIn(ExperimentalSerializationApi::class)

package com.vultisig.wallet.data

import android.content.Context
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.vultisig.wallet.BuildConfig
import com.vultisig.wallet.data.repositories.PrettyJson
import com.vultisig.wallet.data.utils.BigDecimalSerializer
import com.vultisig.wallet.data.utils.BigIntegerSerializer
import com.vultisig.wallet.data.utils.KeysignResponseSerializer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.ANDROID
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import io.ktor.util.appendIfNameAbsent
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.protobuf.ProtoBuf
import java.math.BigDecimal
import java.math.BigInteger
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal interface DataModule {

    companion object {

        @Provides
        @Singleton
        fun provideProtoBuf(): ProtoBuf = ProtoBuf

        @Provides
        @Singleton
        fun provideHttpClient(
            json: Json,
        ): HttpClient = HttpClient(OkHttp) {
            if (BuildConfig.DEBUG) {
                install(Logging) {
                    logger = Logger.ANDROID
                    level = LogLevel.ALL
                }
            }
            install(HttpCache)
            install(DefaultRequest) {
                headers.appendIfNameAbsent(
                    HttpHeaders.ContentType, "application/json"
                )
            }
            install(ContentNegotiation) {
                json(json, ContentType.Any)
            }

            install(HttpTimeout) {
                requestTimeoutMillis = 15000
                connectTimeoutMillis = 15000
            }

            install(HttpRequestRetry) {
                retryOnServerErrors(
                    maxRetries = 3,
                )
                retryOnException(
                    maxRetries = 3,
                    retryOnTimeout = true
                )
                exponentialDelay()
            }
            engine {
                config {
                    retryOnConnectionFailure(
                        retryOnConnectionFailure = true
                    )
                }
            }
        }

        @Provides
        @Singleton
        fun provideJson(
            serializersModule: SerializersModule,
        ) = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            encodeDefaults = true
            this.serializersModule = serializersModule
        }

        @Provides
        @Singleton
        @PrettyJson
        fun providePrettyJson() = Json {
            prettyPrint = true
        }


        @Provides
        @Singleton
        fun provideSerializersModule(
            bigDecimalSerializer: BigDecimalSerializer,
            bigIntegerSerializer: BigIntegerSerializer,
            keysignResponseSerializer: KeysignResponseSerializer,
        ) = SerializersModule {
            contextual(
                BigDecimal::class,
                bigDecimalSerializer
            )
            contextual(
                BigInteger::class,
                bigIntegerSerializer
            )
            contextual(
                tss.KeysignResponse::class,
                keysignResponseSerializer
            )
        }

        @Singleton
        @Provides
        fun provideAppUpdateManager(
            @ApplicationContext context: Context
        ) = AppUpdateManagerFactory.create(context)

    }

}