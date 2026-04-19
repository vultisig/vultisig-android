package com.vultisig.wallet.data.di

import com.vultisig.wallet.BuildConfig
import com.vultisig.wallet.data.networkutils.HttpClientConfigurator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
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

                if (BuildConfig.DEBUG) {
                    install(Logging) {
                        logger =
                            object : Logger {
                                override fun log(message: String) {
                                    Timber.tag("Ktor Client").d(message)
                                }
                            }
                        level = LogLevel.INFO
                    }
                }

                install(HttpCache)

                engine {
                    config {
                        connectTimeout(15, TimeUnit.SECONDS)
                        readTimeout(15, TimeUnit.SECONDS)
                        writeTimeout(15, TimeUnit.SECONDS)

                        retryOnConnectionFailure(true)
                    }
                }
            }
    }
}
