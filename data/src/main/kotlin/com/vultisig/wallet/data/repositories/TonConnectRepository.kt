package com.vultisig.wallet.data.repositories

import androidx.datastore.preferences.core.stringPreferencesKey
import com.vultisig.wallet.data.models.TonConnectSession
import com.vultisig.wallet.data.sources.AppDataStore
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import timber.log.Timber

/** Persists the active TonConnect session across app restarts via DataStore. */
interface TonConnectRepository {
    /** Emits the current session, or null when no session is active. */
    val session: Flow<TonConnectSession?>

    /** Saves [session] to persistent storage. */
    suspend fun saveSession(session: TonConnectSession)

    /** Removes the persisted session. */
    suspend fun clearSession()
}

/** DataStore-backed [TonConnectRepository] implementation. */
internal class TonConnectRepositoryImpl
@Inject
constructor(private val dataStore: AppDataStore, json: Json) : TonConnectRepository {

    private val json: Json = Json(from = json) { coerceInputValues = true }

    override val session: Flow<TonConnectSession?> =
        dataStore.readData(KEY_SESSION).map { raw ->
            raw?.ifEmpty { null }
                ?.let {
                    runCatching { json.decodeFromString<TonConnectSession>(it) }
                        .onFailure { e ->
                            Timber.w(
                                e,
                                "Failed to decode TonConnectSession; dropping persisted value",
                            )
                        }
                        .getOrNull()
                }
        }

    override suspend fun saveSession(session: TonConnectSession) {
        dataStore.set(KEY_SESSION, json.encodeToString(TonConnectSession.serializer(), session))
    }

    // AppDataStore has no remove/clear API, so we write an empty string as a sentinel
    // for "no session"; the session flow converts empty strings back to null.
    override suspend fun clearSession() {
        dataStore.set(KEY_SESSION, "")
    }

    private companion object {
        val KEY_SESSION = stringPreferencesKey("ton_connect_session")
    }
}
