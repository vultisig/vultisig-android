package com.vultisig.wallet.data.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.vultisig.wallet.R
import com.vultisig.wallet.app.activity.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
import timber.log.Timber

@AndroidEntryPoint
class VultisigFirebaseMessagingService : FirebaseMessagingService() {

    @Inject lateinit var pushNotificationManager: PushNotificationManager
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        serviceScope.launch { pushNotificationManager.onNewToken(token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        Timber.d(
            "FCM message received from: ${message.from}, data keys: ${message.data.keys}, notification: title=${message.notification?.title} body=${message.notification?.body?.take(80)}"
        )
        val qrCodeData =
            message.data[QR_CODE_DATA]
                ?: run {
                    Timber.w(
                        "Push notification missing qr_code_data. Available data keys: ${message.data}"
                    )
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

        val channel =
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.keysign_notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            )
        notificationManager.createNotificationChannel(channel)

        val tapIntent =
            Intent(this, MainActivity::class.java).apply {
                flags =
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
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
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.keysign_notification_title))
                .setContentText(getString(R.string.keysign_notification_body))
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

        notificationManager.notify(notificationId, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    companion object {
        const val PUSH_NOTIFICATION_ACTION = "com.vultisig.wallet.PUSH_NOTIFICATION"
        const val QR_CODE_DATA = "message"
        private const val CHANNEL_ID = "keysign_requests_channel"
        private val notificationIdCounter = AtomicInteger(1000)
    }
}
