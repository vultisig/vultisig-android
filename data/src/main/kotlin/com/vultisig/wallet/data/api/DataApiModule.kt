package com.vultisig.wallet.data.api

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal interface DataApiModule {

    @Binds
    @Singleton
    fun bindVultiSignerApi(
        impl: VultiSignerApiImpl
    ): VultiSignerApi

}