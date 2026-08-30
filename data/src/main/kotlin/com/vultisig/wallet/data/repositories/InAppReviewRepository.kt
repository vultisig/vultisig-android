package com.vultisig.wallet.data.repositories

import androidx.annotation.VisibleForTesting
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import com.vultisig.wallet.data.sources.AppDataStore
import javax.inject.Inject
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.flow.Flow
import timber.log.Timber

/**
 * Decides when the app may ask Play for the in-app review card.
 *
 * Two moments qualify, whichever the user reaches first: a vault has just been created, or a
 * transaction has just gone through. Play reports neither whether the card appeared nor what the
 * user did with it, so an ask is spent the moment it is requested — someone who has already rated
 * is never shown the card again by Play itself, and someone who dismissed it is asked again no
 * sooner than [InAppReviewRepositoryImpl.PROMPT_COOLDOWN].
 */
interface InAppReviewRepository {

    /** True while a qualifying moment has been reached and the card has not been asked for yet. */
    val isPromptPending: Flow<Boolean>

    /** Records that a new vault finished being created. */
    suspend fun onVaultCreated()

    /** Records that a transaction reached a successful terminal status. */
    suspend fun onTransactionSucceeded()

    /** Spends the pending ask and starts the cooldown. Call as the Play flow is requested. */
    suspend fun onPromptRequested()
}

internal class InAppReviewRepositoryImpl
@Inject
constructor(private val appDataStore: AppDataStore) : InAppReviewRepository {

    /** Minimal clock seam so tests can drive the cooldown without sleeping. */
    fun interface Clock {
        fun nowMillis(): Long
    }

    @VisibleForTesting internal var clock: Clock = Clock { System.currentTimeMillis() }

    override val isPromptPending: Flow<Boolean> = appDataStore.readData(PROMPT_PENDING_KEY, false)

    override suspend fun onVaultCreated() = onReviewMoment(VAULT_CREATED)

    override suspend fun onTransactionSucceeded() = onReviewMoment(TRANSACTION_SUCCEEDED)

    override suspend fun onPromptRequested() {
        appDataStore.editData { preferences ->
            preferences[PROMPT_PENDING_KEY] = false
            preferences[LAST_PROMPTED_AT_KEY] = clock.nowMillis()
        }
        Timber.i("In-app review: prompt requested")
    }

    /**
     * Marks the card as owed unless a previous ask is still cooling down.
     *
     * The read and the write share one transaction so two moments landing together cannot both
     * decide the cooldown has elapsed.
     */
    private suspend fun onReviewMoment(moment: String) {
        var isPending = false
        appDataStore.editData { preferences ->
            val lastPromptedAt = preferences[LAST_PROMPTED_AT_KEY]
            isPending =
                lastPromptedAt == null ||
                    clock.nowMillis() - lastPromptedAt >= PROMPT_COOLDOWN.inWholeMilliseconds
            if (isPending) {
                preferences[PROMPT_PENDING_KEY] = true
            }
        }
        Timber.i("In-app review: %s, prompt pending=%b", moment, isPending)
    }

    internal companion object {
        /**
         * How long a spent ask keeps the card away. Play throttles far harder than this on its own
         * side; the gap here is only so a user who dismissed the card is not re-asked next week.
         */
        val PROMPT_COOLDOWN = 30.days

        private const val VAULT_CREATED = "vault created"
        private const val TRANSACTION_SUCCEEDED = "transaction succeeded"

        private val PROMPT_PENDING_KEY = booleanPreferencesKey("in_app_review/prompt_pending")
        private val LAST_PROMPTED_AT_KEY = longPreferencesKey("in_app_review/last_prompted_at")
    }
}
