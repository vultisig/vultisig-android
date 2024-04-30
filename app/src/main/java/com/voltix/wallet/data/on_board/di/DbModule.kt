package com.voltix.wallet.data.on_board.di

import com.voltix.wallet.presenter.util.AppConstance
import android.content.Context
import android.util.Log
import androidx.room.Room
import com.voltix.wallet.data.on_board.db.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.Executors
import javax.inject.Singleton


@Module
@InstallIn(SingletonComponent::class)
object DbModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext appContext: Context): AppDatabase {
        return Room.databaseBuilder(
            appContext,
            AppDatabase::class.java,
            AppConstance.DATABASE_NAME
        )
            .fallbackToDestructiveMigrationFrom(1)
            .setQueryCallback(
                queryCallback = { sqlQuery, bindArgs -> Log.d(AppConstance.DATABASE_TAG, "SQL Query: $sqlQuery SQL Args: $bindArgs") },

                executor = Executors.newSingleThreadExecutor()
            )


            .build()
    }

}