package com.vultisig.wallet.data.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import com.vultisig.wallet.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * The high-importance channel keysign requests are delivered on.
 *
 * The id is duplicated in `AndroidManifest.xml` as
 * `com.google.firebase.messaging.default_notification_channel_id`, both reading the same string
 * resource. That meta-data is what makes a backgrounded push land here: when a message carries a
 * `notification` block the FCM SDK renders the tray entry itself, `onMessageReceived` never runs,
 * and without a declared default the SDK falls back to an auto-created "Miscellaneous" channel at
 * default importance — no heads-up peek, trivially muted, for a time-critical signing request.
 *
 * The channel must therefore exist *before* any push arrives, which is why [ensureCreated] runs at
 * app start rather than at first notification.
 */
internal object KeysignNotificationChannel {

    fun id(context: Context): String = context.getString(R.string.keysign_notification_channel_id)

    /** Idempotent — re-creating an existing channel is a no-op apart from name/description. */
    fun ensureCreated(context: Context) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(
            NotificationChannel(
                id(context),
                context.getString(R.string.keysign_notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            )
        )
    }

    /**
     * Whether a keysign push would actually surface: the app holds notification permission *and*
     * the user has not blocked this specific channel. Either can be revoked in system settings long
     * after opt-in, with no callback to the app — so the in-app toggle reads ON while every push is
     * silently dropped.
     */
    fun areNotificationsEnabled(context: Context): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = notificationManager.getNotificationChannel(id(context)) ?: return true
        return channel.importance != NotificationManager.IMPORTANCE_NONE
    }
}

/**
 * Injectable view of [KeysignNotificationChannel.areNotificationsEnabled], so callers can be
 * exercised without a live `NotificationManager`.
 */
interface SystemNotificationStatus {
    fun areNotificationsEnabled(): Boolean
}

internal class AndroidSystemNotificationStatus
@Inject
constructor(@ApplicationContext private val context: Context) : SystemNotificationStatus {
    override fun areNotificationsEnabled(): Boolean =
        KeysignNotificationChannel.areNotificationsEnabled(context)
}
