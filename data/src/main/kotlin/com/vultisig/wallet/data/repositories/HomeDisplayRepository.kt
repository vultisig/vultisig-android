package com.vultisig.wallet.data.repositories

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vultisig.wallet.data.models.HomeBalanceDisplay
import com.vultisig.wallet.data.sources.AppDataStore
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** The app-wide home balance display and whether the home swipe hint has been seen. */
interface HomeDisplayRepository {
    /** [HomeBalanceDisplay.Separate] until the user picks otherwise. */
    val balanceDisplay: Flow<HomeBalanceDisplay>

    suspend fun setBalanceDisplay(display: HomeBalanceDisplay)

    suspend fun hasSeenSwipeHint(): Boolean

    suspend fun markSwipeHintSeen()
}

internal class HomeDisplayRepositoryImpl @Inject constructor(private val dataStore: AppDataStore) :
    HomeDisplayRepository {

    override val balanceDisplay: Flow<HomeBalanceDisplay> =
        dataStore.readData(KEY_BALANCE_DISPLAY).map { stored ->
            HomeBalanceDisplay.entries.firstOrNull { it.storedValue == stored }
                ?: HomeBalanceDisplay.Separate
        }

    override suspend fun setBalanceDisplay(display: HomeBalanceDisplay) {
        dataStore.set(KEY_BALANCE_DISPLAY, display.storedValue)
    }

    override suspend fun hasSeenSwipeHint(): Boolean =
        dataStore.readData(KEY_HAS_SEEN_SWIPE_HINT, false).first()

    override suspend fun markSwipeHintSeen() {
        dataStore.set(KEY_HAS_SEEN_SWIPE_HINT, true)
    }

    private companion object {
        val KEY_BALANCE_DISPLAY = stringPreferencesKey("home_balance_display")
        val KEY_HAS_SEEN_SWIPE_HINT = booleanPreferencesKey("has_seen_home_swipe_hint")
    }
}
