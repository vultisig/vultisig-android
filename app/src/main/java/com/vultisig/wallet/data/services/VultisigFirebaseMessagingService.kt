package com.vultisig.wallet.data.services

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.vultisig.wallet.data.repositories.NotificationTokenRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class VultisigFirebaseMessagingService : FirebaseMessagingService() {

    @Inject lateinit var notificationTokenRepository: NotificationTokenRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        Timber.d("FCM token refreshed : $token")

        serviceScope.launch { notificationTokenRepository.setToken(token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        Timber.d("FCM message received from: ${message.from}")
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
