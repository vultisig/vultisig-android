package com.vultisig.wallet.app

import android.content.Context
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal interface AppModule {
    companion object {

        @Singleton
        @Provides
        fun provideAppUpdateManager(
            @ApplicationContext context: Context
        ) = AppUpdateManagerFactory.create(context)

    }
}