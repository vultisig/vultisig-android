@file:OptIn(ExperimentalSerializationApi::class)

package com.vultisig.wallet.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.vultisig.wallet.BuildConfig
import com.vultisig.wallet.data.models.OneInchSwapPayloadJson
import com.vultisig.wallet.data.models.OneInchSwapPayloadJsonDeserializer
import com.vultisig.wallet.data.models.OneInchSwapPayloadJsonSerializer
import com.vultisig.wallet.data.sources.AppDataStore
import com.vultisig.wallet.models.ERC20ApprovePayload
import com.vultisig.wallet.models.ERC20ApprovePayloadDeserializer
import com.vultisig.wallet.models.ERC20ApprovePayloadSerializer
import com.vultisig.wallet.models.THORChainSwapPayload
import com.vultisig.wallet.models.THORChainSwapPayloadDeserializer
import com.vultisig.wallet.models.THORChainSwapPayloadSerializer
import com.vultisig.wallet.presenter.keysign.BlockChainSpecific
import com.vultisig.wallet.presenter.keysign.BlockChainSpecificDeserializer
import com.vultisig.wallet.presenter.keysign.BlockChainSpecificSerializer
import com.vultisig.wallet.presenter.keysign.KeysignPayload
import com.vultisig.wallet.presenter.keysign.KeysignPayloadDeserializer
import com.vultisig.wallet.presenter.keysign.KeysignPayloadSerializer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.logging.ANDROID
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.HttpHeaders
import io.ktor.util.appendIfNameAndValueAbsent
import kotlinx.serialization.ExperimentalSerializationApi
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
                .registerTypeAdapter(KeysignPayload::class.java, KeysignPayloadSerializer())
                .registerTypeAdapter(
                    BlockChainSpecific.UTXO::class.java,
                    BlockChainSpecificSerializer()
                ).registerTypeAdapter(
                    BlockChainSpecific.Cosmos::class.java,
                    BlockChainSpecificSerializer()
                ).registerTypeAdapter(
                    BlockChainSpecific.THORChain::class.java,
                    BlockChainSpecificSerializer()
                ).registerTypeAdapter(
                    BlockChainSpecific.MayaChain::class.java,
                    BlockChainSpecificSerializer()
                ).registerTypeAdapter(
                    BlockChainSpecific.Sui::class.java,
                    BlockChainSpecificSerializer()
                ).registerTypeAdapter(
                    BlockChainSpecific.Polkadot::class.java,
                    BlockChainSpecificSerializer()
                ).registerTypeAdapter(
                    BlockChainSpecific.Solana::class.java,
                    BlockChainSpecificSerializer()
                ).registerTypeAdapter(
                    BlockChainSpecific.Ethereum::class.java,
                    BlockChainSpecificSerializer()
                ).registerTypeAdapter(
                    BlockChainSpecific::class.java, BlockChainSpecificSerializer()
                ).registerTypeAdapter(
                    BlockChainSpecific::class.java,
                    BlockChainSpecificDeserializer()
                )
                .registerTypeAdapter(
                    KeysignPayload::class.java,
                    KeysignPayloadDeserializer()
                )
                .registerTypeAdapter(
                    THORChainSwapPayload::class.java,
                    THORChainSwapPayloadSerializer(),
                )
                .registerTypeAdapter(
                    THORChainSwapPayload::class.java,
                    THORChainSwapPayloadDeserializer(),
                )
                .registerTypeAdapter(
                    OneInchSwapPayloadJson::class.java,
                    OneInchSwapPayloadJsonDeserializer(),
                )
                .registerTypeAdapter(
                    OneInchSwapPayloadJson::class.java,
                    OneInchSwapPayloadJsonSerializer(),
                )
                .registerTypeAdapter(
                    ERC20ApprovePayload::class.java,
                    ERC20ApprovePayloadDeserializer(),
                )
                .registerTypeAdapter(
                    ERC20ApprovePayload::class.java,
                    ERC20ApprovePayloadSerializer(),
                )
                .create()
        }

        @Provides
        @Singleton
        fun provideProtoBuf(): ProtoBuf = ProtoBuf

        @Provides
        @Singleton
        fun provideHttpClient(): HttpClient = HttpClient(OkHttp) {
            if (BuildConfig.DEBUG) {
                install(Logging) {
                    logger = Logger.ANDROID
                    level = LogLevel.ALL
                }
            }
            install(HttpCache)
            install(DefaultRequest) {
                headers.appendIfNameAndValueAbsent(
                    HttpHeaders.ContentType, "application/json"
                )
            }
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

    }

}