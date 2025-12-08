package com.vultisig.wallet.data.sources

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject


interface AppDataStore {
    suspend fun editData(transform: suspend (MutablePreferences) -> Unit): Preferences

    fun <T> readData(
        key: Preferences.Key<T>,
        defaultValue: T,
    ): Flow<T>

    suspend fun <T> set(key: Preferences.Key<T>, value: T)

    fun <T> readData(
        key: Preferences.Key<T>,
    ): Flow<T?>
}

internal class AppDataStoreImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : AppDataStore {

    override suspend fun editData(transform: suspend (MutablePreferences) -> Unit) =
        dataStore.edit(transform)

    override fun <T> readData(
        key: Preferences.Key<T>,
        defaultValue: T,
    ): Flow<T> = dataStore.data.map { preferences ->
        preferences[key] ?: defaultValue
    }

    override suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        editData {
            it[key] = value
        }
    }

    override fun <T> readData(
        key: Preferences.Key<T>,
    ): Flow<T?> = dataStore.data.map { preferences ->
        preferences[key]
    }


}
