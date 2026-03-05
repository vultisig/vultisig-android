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

@AndroidEntryPoint
class VultisigFirebaseMessagingService : FirebaseMessagingService() {

    @Inject lateinit var notificationTokenRepository: NotificationTokenRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        serviceScope.launch { notificationTokenRepository.setToken(token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        // TODO: Handle incoming messages if needed. For now, we rely on the system to display
        // notifications based on the payload.
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
