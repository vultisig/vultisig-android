package com.vultisig.wallet.data.sources

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

// TODO move to DI graph
internal val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_pref")

// TODO add interface
class AppDataStore @Inject constructor(@ApplicationContext context: Context) {

    private val dataStore = context.dataStore

    suspend fun editData(transform: suspend (MutablePreferences) -> Unit) =
        dataStore.edit(transform)

    fun <T> readData(
        key: Preferences.Key<T>,
        defaultValue: T,
    ): Flow<T> = dataStore.data.map { preferences ->
        preferences[key] ?: defaultValue
    }

    suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        editData {
            it[key] = value
        }
    }

    fun <T> readData(
        key: Preferences.Key<T>,
    ): Flow<T?> = dataStore.data.map { preferences ->
        preferences[key]
    }


}
