package com.vultisig.wallet.data.repositories

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import com.vultisig.wallet.data.sources.AppDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Throttles the Play in-app review prompt so it is only requested at a genuine satisfaction peak.
 *
 * Play enforces its own opaque quota on top of this and the flow may no-op silently, so the app
 * never learns whether the dialog appeared — the cooldown here therefore starts when the prompt is
 * *requested*, not when the user rates.
 */
interface InAppReviewRepository {

    /**
     * Records one more confirmed transaction and reports whether the review flow should be
     * requested now.
     *
     * Returns true only when the install has produced enough successful transactions, is old enough
     * to have a real opinion, and has not been prompted recently. A true result consumes the
     * cooldown, so the caller must launch the flow.
     */
    suspend fun onTransactionSucceeded(): Boolean
}

internal class InAppReviewRepositoryImpl
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val appDataStore: AppDataStore,
) : InAppReviewRepository {

    /** Minimal clock seam so tests can drive the gates without sleeping. */
    fun interface Clock {
        fun nowMillis(): Long
    }

    /** Install-time seam so tests can age an install without a real package. */
    fun interface InstallTime {
        fun firstInstallMillis(): Long
    }

    @VisibleForTesting internal var clock: Clock = Clock { System.currentTimeMillis() }

    /**
     * Read from the package manager rather than a stored timestamp so users who installed before
     * this gate existed are correctly treated as long-standing rather than brand new. Falls back to
     * "installed right now", which fails closed: an unreadable install date suppresses the prompt
     * instead of showing it to a first-run user.
     */
    @VisibleForTesting
    internal var installTime: InstallTime = InstallTime {
        runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime
            }
            .getOrElse { error ->
                Timber.w(error, "Unable to read first install time; suppressing the review prompt")
                clock.nowMillis()
            }
    }

    override suspend fun onTransactionSucceeded(): Boolean =
        withContext(Dispatchers.IO) {
            val successfulTransactions = incrementSuccessfulTransactions()
            if (successfulTransactions < MIN_SUCCESSFUL_TRANSACTIONS) return@withContext false

            val now = clock.nowMillis()
            if (now - installTime.firstInstallMillis() < MIN_INSTALL_AGE.inWholeMilliseconds) {
                return@withContext false
            }

            val lastPromptedAt = appDataStore.readData(LAST_PROMPTED_AT_KEY).first()
            if (
                lastPromptedAt != null && now - lastPromptedAt < PROMPT_COOLDOWN.inWholeMilliseconds
            ) {
                return@withContext false
            }

            appDataStore.set(LAST_PROMPTED_AT_KEY, now)
            true
        }

    /**
     * Increments the counter inside a single DataStore transaction and returns the new total, so
     * concurrent successes can't read-modify-write over each other.
     */
    private suspend fun incrementSuccessfulTransactions(): Int =
        appDataStore
            .editData { preferences ->
                preferences[SUCCESSFUL_TRANSACTIONS_KEY] =
                    (preferences[SUCCESSFUL_TRANSACTIONS_KEY] ?: 0) + 1
            }[SUCCESSFUL_TRANSACTIONS_KEY] ?: 0

    internal companion object {
        /** Enough completed transactions to have formed an opinion worth rating. */
        const val MIN_SUCCESSFUL_TRANSACTIONS = 3

        /** Keeps the prompt away from users still evaluating the wallet. */
        val MIN_INSTALL_AGE = 7.days

        /** Gap between two prompts, so a decline is not re-asked for months. */
        val PROMPT_COOLDOWN = 120.days

        private val SUCCESSFUL_TRANSACTIONS_KEY =
            intPreferencesKey("in_app_review/successful_transactions")
        private val LAST_PROMPTED_AT_KEY = longPreferencesKey("in_app_review/last_prompted_at")
    }
}
