package com.vultisig.wallet.data.sources

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory [AppDataStore] holding preferences in a flow, so reads see prior writes. */
internal class FakeAppDataStore(initial: Preferences = emptyPreferences()) : AppDataStore {

    private val preferences = MutableStateFlow(initial)

    override suspend fun editData(transform: suspend (MutablePreferences) -> Unit): Preferences {
        val mutable = preferences.value.toMutablePreferences()
        transform(mutable)
        return mutable.toPreferences().also { preferences.value = it }
    }

    override fun <T> readData(key: Preferences.Key<T>, defaultValue: T): Flow<T> =
        preferences.map { it[key] ?: defaultValue }

    override suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        editData { it[key] = value }
    }

    override fun <T> readData(key: Preferences.Key<T>): Flow<T?> = preferences.map { it[key] }

    companion object {
        fun of(vararg entries: Preferences.Pair<*>): FakeAppDataStore =
            FakeAppDataStore(mutablePreferencesOf(*entries).toPreferences())
    }
}
