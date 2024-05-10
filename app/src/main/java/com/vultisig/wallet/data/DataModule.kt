package com.vultisig.wallet.data

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.vultisig.wallet.data.sources.AppDataStore
import com.vultisig.wallet.presenter.keysign.BlockChainSpecific
import com.vultisig.wallet.presenter.keysign.BlockChainSpecificDeserializer
import com.vultisig.wallet.presenter.keysign.BlockChainSpecificSerializer
import com.vultisig.wallet.presenter.keysign.KeysignPayload
import com.vultisig.wallet.presenter.keysign.KeysignPayloadDeserializer
import com.vultisig.wallet.presenter.keysign.KeysignPayloadSerializer
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal interface DataModule {

    @Binds
    @Singleton
    fun provideAppDataStore(
        impl: AppDataStore
    ): AppDataStore

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
                ) .registerTypeAdapter(
                    BlockChainSpecific::class.java,
                    BlockChainSpecificDeserializer()
                )
                .registerTypeAdapter(
                    KeysignPayload::class.java,
                    KeysignPayloadDeserializer()
                )
                .create()
        }

        @Provides
        @Singleton
        fun provideHttpClient(): HttpClient = HttpClient(OkHttp)

    }

}