package com.vultisig.wallet.data.di

import android.content.Context
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.vultisig.wallet.data.repositories.PrettyJson
import com.vultisig.wallet.data.utils.BigDecimalSerializer
import com.vultisig.wallet.data.utils.BigIntegerSerializer
import com.vultisig.wallet.data.utils.KeysignResponseSerializer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.protobuf.ProtoBuf
import tss.KeysignResponse
import java.math.BigDecimal
import java.math.BigInteger
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal interface DataModule {

    companion object {

        @OptIn(ExperimentalSerializationApi::class)
        @Provides
        @Singleton
        fun provideProtoBuf(): ProtoBuf = ProtoBuf.Default

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
                KeysignResponse::class,
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