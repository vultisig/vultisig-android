package com.vultisig.wallet.data.passcode

import androidx.datastore.preferences.core.intPreferencesKey
import com.vultisig.wallet.data.sources.AppDataStore
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * How long the app may stay in the background before it re-locks.
 *
 * The set and the default match the Windows client and browser extension
 * (`core/ui/storage/passcodeAutoLock.tsx`): 1, 5, 10, 15 and 30 minutes, plus never, defaulting to
 * never. There is deliberately no immediate option — the other clients do not offer one, and
 * defaulting to it here would re-lock the app on every excursion to a QR scanner or a browser,
 * which is most of what a wallet does.
 */
enum class AutoLockTimeout(val minutes: Int) {
    OneMinute(1),
    FiveMinutes(5),
    TenMinutes(10),
    FifteenMinutes(15),
    ThirtyMinutes(30),
    /** Only ever locks when the user asks, or when the process is killed. */
    Never(NEVER_MINUTES);

    companion object {
        val Default = Never

        /** Maps a persisted minute count back to an option, falling back to [Default]. */
        fun fromMinutes(minutes: Int): AutoLockTimeout =
            entries.firstOrNull { it.minutes == minutes } ?: Default
    }
}

/** Sentinel minute count for [AutoLockTimeout.Never]; never compared against elapsed time. */
private const val NEVER_MINUTES = -1

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
