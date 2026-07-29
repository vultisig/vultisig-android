package com.vultisig.wallet.data.services

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit
import timber.log.Timber

/**
 * Re-points the server at this device's current FCM token for every opted-in vault.
 *
 * Runs here rather than inline in `FirebaseMessagingService.onNewToken` because that service stops
 * itself — and cancels its scope — as soon as the callback returns, so an in-flight HTTP
 * re-registration raced teardown and a lost race left the server holding a dead token forever.
 * WorkManager survives teardown, process death and reboot, and retries with backoff.
 */
@HiltWorker
internal class PushRegistrationWorker
@AssistedInject
constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val pushNotificationManager: PushNotificationManager,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        // Nothing to keep alive on the server until at least one vault has opted in. Minting a
        // token here would also prompt Firebase for one on devices that never asked for pushes.
        if (!pushNotificationManager.hasOptedInVaults()) return Result.success()

        val token = pushNotificationManager.currentToken() ?: return retryOrFail()

        return if (pushNotificationManager.reRegisterOptedInVaults(token)) {
            Result.success()
        } else {
            retryOrFail()
        }
    }

    // WorkManager has no built-in attempt cap, so bound it here to stop rescheduling a failure
    // that is never going to resolve.
    private fun retryOrFail(): Result =
        if (runAttemptCount < MAX_ATTEMPTS) {
            Result.retry()
        } else {
            Timber.w("Giving up on FCM re-registration after %d attempts", runAttemptCount + 1)
            Result.failure()
        }

    companion object {
        const val MAX_ATTEMPTS = 5
        private const val WORK_NAME = "push_registration"

        /**
         * Queues a re-registration.
         *
         * @param replaceExisting true when a *new* token has just arrived, so a run still queued
         *   against the previous token is redundant and must not overwrite the newer one. False for
         *   the startup reconcile, which must not restart an already-running attempt.
         */
        fun enqueue(context: Context, replaceExisting: Boolean) {
            val request =
                OneTimeWorkRequestBuilder<PushRegistrationWorker>()
                    .setConstraints(
                        Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                    )
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                    .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    WORK_NAME,
                    if (replaceExisting) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
                    request,
                )
        }
    }
}
