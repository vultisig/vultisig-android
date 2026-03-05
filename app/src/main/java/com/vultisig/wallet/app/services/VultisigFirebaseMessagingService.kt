package com.vultisig.wallet.app.services

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.vultisig.wallet.data.notifications.PushNotificationManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import timber.log.Timber

@AndroidEntryPoint
class VultisigFirebaseMessagingService : FirebaseMessagingService() {

    @Inject lateinit var pushNotificationManager: PushNotificationManager

    override fun onNewToken(token: String) {
        Timber.d("FCM token refreshed")
        pushNotificationManager.onNewToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        Timber.d("FCM message received from ${message.from}")
        val qrCodeData =
            message.data[EXTRA_QR_CODE_DATA]
                ?: run {
                    Timber.w("Push notification missing qr_code_data")
                    return
                }

        val intent =
            android.content.Intent(PUSH_NOTIFICATION_ACTION).apply {
                putExtra(EXTRA_QR_CODE_DATA, qrCodeData)
                setPackage(packageName)
            }
        sendBroadcast(intent)
    }

    companion object {
        const val PUSH_NOTIFICATION_ACTION = "com.vultisig.wallet.PUSH_NOTIFICATION"
        const val EXTRA_QR_CODE_DATA = "qr_code_data"
    }
}
