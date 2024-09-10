@file:OptIn(ExperimentalSerializationApi::class)

package com.vultisig.wallet.data

import android.content.Context
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.vultisig.wallet.BuildConfig
import com.vultisig.wallet.data.sources.AppDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.ANDROID
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import io.ktor.util.appendIfNameAbsent
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import org.apache.commons.compress.compressors.CompressorStreamFactory
import org.apache.commons.compress.compressors.CompressorStreamProvider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal interface DataModule {

    companion object {

        @Provides
        @Singleton
        fun provideGson(): Gson {
            return GsonBuilder()
                .create()
        }

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
                json(json)
            }
        }

        @Provides
        @Singleton
        fun provideJson() = Json {
            ignoreUnknownKeys = true
        }

        @Provides
        @Singleton
        fun provideAppDataStore(
            @ApplicationContext context: Context
        ): AppDataStore = AppDataStore(context)

        @Provides
        @Singleton
        fun provideCompressorStreamProvider(): CompressorStreamProvider =
            CompressorStreamFactory()

        @Singleton
        @Provides
        fun provideAppUpdateManager(
            @ApplicationContext context: Context
        ) = AppUpdateManagerFactory.create(context)

    }

}