package com.vultisig.wallet.network

import com.vultisig.wallet.data.di.NetworkModule
import com.vultisig.wallet.data.networkutils.HttpClientConfigurator
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
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
        baseConfig: HttpClientConfigurator,
        faultyInterceptor: FaultyInterceptor,
    ): HttpClient = HttpClient(OkHttp) {

        baseConfig.configure(this)

        engine {
            config {
                addInterceptor(faultyInterceptor)
            }
        }
    }
}
