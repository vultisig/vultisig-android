package com.vultisig.wallet.app.startup

import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import dagger.hilt.InstallIn
import dagger.hilt.android.EarlyEntryPoint
import dagger.hilt.android.EarlyEntryPoints
import dagger.hilt.components.SingletonComponent

@EarlyEntryPoint
@InstallIn(SingletonComponent::class)
internal interface InitializerEntryPoint {

    fun getHiltWorkerFactory(): HiltWorkerFactory

    companion object {
        //a helper method to resolve the InitializerEntryPoint from the context
        fun resolve(context: Context): InitializerEntryPoint {
            return EarlyEntryPoints.get(
                context,
                InitializerEntryPoint::class.java
            )
        }
    }
}