package com.vultisig.wallet.startup

import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface InitializerEntryPoint {

    fun getHiltWorkerFactory(): HiltWorkerFactory

    companion object {
        //a helper method to resolve the InitializerEntryPoint from the context
        fun resolve(context: Context): InitializerEntryPoint {
            return EntryPointAccessors.fromApplication(
                context,
                InitializerEntryPoint::class.java
            )
        }
    }
}