package com.vultisig.wallet.data.services

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.vultisig.wallet.R
import com.vultisig.wallet.app.activity.PushNotificationTapActivity
import com.vultisig.wallet.data.usecases.MAX_SIGNING_LINK_CHARS
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import timber.log.Timber

@AndroidEntryPoint
class VultisigFirebaseMessagingService : FirebaseMessagingService() {

    @Inject lateinit var pushNotificationManager: PushNotificationManager

    override fun onNewToken(token: String) {
        // Both halves are synchronous so neither can be lost to service teardown: the token is
        // persisted here and the HTTP re-registration is handed to WorkManager, which owns the
        // retries. Callbacks arrive on a Firebase background thread, so the prefs write is safe.
        pushNotificationManager.storeToken(token)
        PushRegistrationWorker.enqueue(this, replaceExisting = true)
    }

    override fun handleIntent(intent: Intent) {
        if (intent.hasExtra(QR_CODE_DATA)) {
            // A combined notification/data message normally bypasses onMessageReceived in the
            // background and opens the exported launcher. The pinned Firebase SDK checks both
            // flags to decide whether to render it. Use the app's private tap target instead.
            intent.removeExtra(FCM_NOTIFICATION_ENABLED)
            intent.removeExtra(FCM_LEGACY_NOTIFICATION_ENABLED)
        }
        super.handleIntent(intent)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val qrCodeData = message.data[QR_CODE_DATA]
        if (qrCodeData == null || qrCodeData.length !in 1..MAX_SIGNING_LINK_CHARS) {
            Timber.w("Push notification did not contain a valid-sized signing link")
            return
        }

        // Forward to foreground app via broadcast (handled in MainActivity when app is visible)
        val broadcastIntent =
            Intent(PUSH_NOTIFICATION_ACTION).apply {
                putExtra(QR_CODE_DATA, qrCodeData)
                setPackage(packageName)
            }
        sendBroadcast(broadcastIntent)

        // Post a system notification only when the app is not in the foreground;
        // foreground handling is done via the broadcast above in MainActivity.
        if (!isAppInForeground()) {
            showSystemNotification(qrCodeData)
        }
    }

    private fun isAppInForeground(): Boolean =
        ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)

    private fun showSystemNotification(qrCodeData: String) {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        val tapIntent =
            Intent(this, PushNotificationTapActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                putExtra(QR_CODE_DATA, qrCodeData)
            }
        val notificationId = notificationIdCounter.incrementAndGet()

        val pendingIntent =
            PendingIntent.getActivity(
                this,
                notificationId,
                tapIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        val notification =
            NotificationCompat.Builder(this, KeysignNotificationChannel.id(this))
                .setContentTitle(getString(R.string.keysign_notification_title))
                .setContentText(getString(R.string.keysign_notification_body))
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

        notificationManager.notify(notificationId, notification)
    }

    companion object {
        const val PUSH_NOTIFICATION_ACTION = "com.vultisig.wallet.PUSH_NOTIFICATION"
        const val QR_CODE_DATA = "message"
        private const val FCM_NOTIFICATION_ENABLED = "gcm.n.e"
        private const val FCM_LEGACY_NOTIFICATION_ENABLED = "gcm.notification.e"
        private val notificationIdCounter = AtomicInteger(1000)
    }
}
