package com.vultisig.wallet.ui.widgets.market

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.vultisig.wallet.data.models.MarketWidgetQuery
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.data.repositories.MarketWidgetRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import timber.log.Timber

/**
 * Pulls market data for every placed widget into the last-good cache, then re-renders them.
 *
 * The widgets themselves only read the cache: a Glance `provideGlance` runs inside a broadcast and
 * can't afford a CoinGecko round trip, and WorkManager gives us network constraints, retry with
 * backoff, and survival across process death for free. Runs every 30 minutes while at least one
 * market widget is on a home screen, plus on demand when a widget is placed or the app currency
 * changes.
 */
@HiltWorker
internal class MarketWidgetRefreshWorker
@AssistedInject
constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: MarketWidgetRepository,
    private val appCurrencyRepository: AppCurrencyRepository,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        var refreshedAll = false
        var failed = false

        // A widget can be placed, or the currency changed, while this run is in flight; its
        // provideGlance then finds no cache entry and its enqueue is de-duplicated against us. So
        // after re-rendering, look again and fill any hole before returning.
        repeat(MAX_PASSES) {
            val currency = appCurrencyRepository.currency.first().ticker
            val queries = queriesInUse(applicationContext)
            if (queries.isEmpty()) {
                cancelPeriodic(applicationContext)
                return Result.success()
            }

            val toRefresh =
                if (refreshedAll) queries.filter { repository.cached(it, currency) == null }
                else queries
            if (toRefresh.isEmpty()) return finish(failed)
            Timber.d("Market widget refresh: %s in %s", toRefresh, currency)

            for (query in toRefresh) {
                try {
                    repository.refresh(query, currency)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.w(e, "Market widget refresh failed with nothing cached for %s", query)
                    failed = true
                }
            }
            refreshedAll = true

            CryptoTickerWidget().updateAll(applicationContext)
            TopCryptosWidget().updateAll(applicationContext)
        }
        return finish(failed)
    }

    // WorkManager has no built-in attempt cap, so bound it here to stop rescheduling a failure
    // that is never going to resolve.
    private fun finish(failed: Boolean): Result =
        when {
            !failed -> Result.success()
            runAttemptCount < MAX_ATTEMPTS -> Result.retry()
            else -> Result.failure()
        }

    companion object {
        private const val MAX_ATTEMPTS = 5
        private const val MAX_PASSES = 3
        private const val PERIODIC_WORK_NAME = "market_widget_refresh"
        private const val ONE_TIME_WORK_NAME = "market_widget_refresh_now"
        const val REFRESH_INTERVAL_MINUTES = 30L

        /** Every distinct query some placed widget currently needs. */
        suspend fun queriesInUse(context: Context): List<MarketWidgetQuery> {
            val manager = GlanceAppWidgetManager(context)
            val queries = mutableListOf<MarketWidgetQuery>()
            if (manager.getGlanceIds(TopCryptosWidget::class.java).isNotEmpty()) {
                queries += MarketWidgetQuery.Top(TopCryptosWidget.MAX_ROWS)
            }
            manager
                .getGlanceIds(CryptoTickerWidget::class.java)
                .map { id ->
                    val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
                    CryptoTickerWidget.selectedAssetId(prefs)
                }
                .distinct()
                .forEach { queries += MarketWidgetQuery.Ids(listOf(it)) }
            return queries
        }

        /** Keeps the 30-minute poll alive; a no-op if it is already scheduled. */
        fun schedulePeriodic(context: Context) {
            val request =
                PeriodicWorkRequestBuilder<MarketWidgetRefreshWorker>(
                        REFRESH_INTERVAL_MINUTES,
                        TimeUnit.MINUTES,
                    )
                    .setConstraints(networkConstraints())
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                    .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    PERIODIC_WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request,
                )
        }

        fun cancelPeriodic(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME)
        }

        /**
         * Queues a refresh right away.
         *
         * @param replaceQueued true when the inputs changed (currency, a widget's asset) so a run
         *   that is merely queued would fetch stale inputs and must be superseded; false when a
         *   widget just wants *some* data and a pending run is as good as a new one.
         */
        fun refreshNow(context: Context, replaceQueued: Boolean) {
            if (!hasPlacedWidgets(context)) return
            val request =
                OneTimeWorkRequestBuilder<MarketWidgetRefreshWorker>()
                    .setConstraints(networkConstraints())
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                    .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    ONE_TIME_WORK_NAME,
                    if (replaceQueued) ExistingWorkPolicy.APPEND_OR_REPLACE
                    else ExistingWorkPolicy.KEEP,
                    request,
                )
        }

        private fun hasPlacedWidgets(context: Context): Boolean {
            val manager = AppWidgetManager.getInstance(context)
            return listOf(
                    CryptoTickerWidgetReceiver::class.java,
                    TopCryptosWidgetReceiver::class.java,
                )
                .any { manager.getAppWidgetIds(ComponentName(context, it)).isNotEmpty() }
        }

        private fun networkConstraints() =
            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
    }
}
