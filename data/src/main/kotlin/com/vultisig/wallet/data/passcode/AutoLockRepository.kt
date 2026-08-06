package com.vultisig.wallet.data.passcode

import androidx.datastore.preferences.core.intPreferencesKey
import com.vultisig.wallet.data.sources.AppDataStore
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * How long the app may stay in the background before it re-locks. Mirrors the options the Windows
 * client and browser extension offer, including their immediate-lock default.
 */
enum class AutoLockTimeout(val minutes: Int) {
    Immediate(0),
    OneMinute(1),
    FiveMinutes(5),
    TenMinutes(10),
    FifteenMinutes(15),
    ThirtyMinutes(30);

    companion object {
        val Default = Immediate

        /** Maps a persisted minute count back to an option, falling back to [Default]. */
        fun fromMinutes(minutes: Int): AutoLockTimeout =
            entries.firstOrNull { it.minutes == minutes } ?: Default
    }
}

/** Stores the user's auto-lock idle timeout. */
interface AutoLockRepository {
    val timeout: Flow<AutoLockTimeout>

    suspend fun setTimeout(timeout: AutoLockTimeout)
}

internal class AutoLockRepositoryImpl @Inject constructor(private val dataStore: AppDataStore) :
    AutoLockRepository {

    override val timeout: Flow<AutoLockTimeout> =
        dataStore
            .readData(KEY_AUTO_LOCK_MINUTES, AutoLockTimeout.Default.minutes)
            .map(AutoLockTimeout::fromMinutes)

    override suspend fun setTimeout(timeout: AutoLockTimeout) {
        dataStore.set(KEY_AUTO_LOCK_MINUTES, timeout.minutes)
    }

    private companion object {
        val KEY_AUTO_LOCK_MINUTES = intPreferencesKey("passcode_auto_lock_minutes")
    }
}
