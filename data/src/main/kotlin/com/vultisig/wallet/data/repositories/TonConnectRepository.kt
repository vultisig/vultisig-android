package com.vultisig.wallet.data.repositories

import androidx.datastore.preferences.core.stringPreferencesKey
import com.vultisig.wallet.data.models.TonKeysignSession
import com.vultisig.wallet.data.sources.AppDataStore
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import timber.log.Timber

/**
 * Persists the active TonConnect session across app restarts via DataStore.
 *
 * TODO(#4147): nothing in production reads [session] yet; wiring a real consumer is tracked there.
 */
interface TonConnectRepository {
    /** Emits the current session, or null when no session is active. */
    val session: Flow<TonKeysignSession?>

    /** Saves [session] to persistent storage. */
    suspend fun saveSession(session: TonKeysignSession)

    /** Removes the persisted session. */
    suspend fun clearSession()
}

/** DataStore-backed [TonConnectRepository] implementation. */
internal class TonConnectRepositoryImpl
@Inject
constructor(private val dataStore: AppDataStore, private val json: Json) : TonConnectRepository {

    // TODO(#4147): upgrade to stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)
    //              once a real consumer subscribes from multiple call sites.
    /** Reads and deserializes the session from DataStore; emits null when absent or unparseable. */
    override val session: Flow<TonKeysignSession?> =
        dataStore.readData(KEY_SESSION).map { raw ->
            raw?.let {
                runCatching { json.decodeFromString<TonKeysignSession>(it) }
                    .onFailure {
                        Timber.w("Failed to decode TonKeysignSession; dropping persisted value")
                    }
                    .getOrNull()
            }
        }

    /** Serializes [session] to JSON and writes it to DataStore. */
    override suspend fun saveSession(session: TonKeysignSession) {
        dataStore.set(KEY_SESSION, json.encodeToString(TonKeysignSession.serializer(), session))
    }

    /** Removes the persisted session from DataStore. */
    override suspend fun clearSession() {
        dataStore.editData { it.remove(KEY_SESSION) }
    }

    private companion object {
        val KEY_SESSION = stringPreferencesKey("ton_connect_session")
    }
}
