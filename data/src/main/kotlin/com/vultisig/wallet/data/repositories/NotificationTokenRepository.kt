package com.vultisig.wallet.data.repositories

import androidx.datastore.preferences.core.stringPreferencesKey
import com.vultisig.wallet.data.sources.AppDataStore
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

interface NotificationTokenRepository {
    val token: Flow<String?>
    suspend fun setToken(token: String)
}

internal class NotificationTokenRepositoryImpl @Inject constructor(
    private val dataStore: AppDataStore,
) : NotificationTokenRepository {

    override val token: Flow<String?> =
        dataStore.readData(TOKEN_KEY)

    override suspend fun setToken(token: String) {
        dataStore.set(TOKEN_KEY, token)
    }

    companion object {
        private val TOKEN_KEY = stringPreferencesKey("notification_token")
    }
}
