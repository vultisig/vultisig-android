package com.vultisig.wallet.data.services

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import timber.log.Timber

@AndroidEntryPoint
class VultisigFirebaseMessagingService : FirebaseMessagingService() {

    @Inject lateinit var pushNotificationManager: PushNotificationManager
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        Timber.d("FCM token refreshed : $token")

        pushNotificationManager.onNewToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        Timber.d("FCM message received from: ${message.from}")
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

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    companion object {
        const val PUSH_NOTIFICATION_ACTION = "com.vultisig.wallet.PUSH_NOTIFICATION"
        const val EXTRA_QR_CODE_DATA = "qr_code_data"
    }
}
