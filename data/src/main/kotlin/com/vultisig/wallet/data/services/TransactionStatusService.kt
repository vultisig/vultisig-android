package com.vultisig.wallet.data.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.vultisig.wallet.data.R
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.usecases.txstatus.PollingTxStatusUseCase
import com.vultisig.wallet.data.usecases.txstatus.TransactionResult
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class TransactionStatusService : Service() {

    @Inject
    lateinit var pollingTxStatus: PollingTxStatusUseCase
    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pollingJob: Job? = null

    private val _statusFlow = MutableStateFlow<TransactionResult>(TransactionResult.Pending)
    val statusFlow: StateFlow<TransactionResult> = _statusFlow.asStateFlow()

    inner class LocalBinder : Binder() {
        fun getService(): TransactionStatusService = this@TransactionStatusService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_POLLING -> {
                val txHash = intent.getStringExtra(EXTRA_TX_HASH) ?: return START_NOT_STICKY
                val chainName = intent.getStringExtra(EXTRA_CHAIN) ?: return START_NOT_STICKY
                val chain = Chain.fromRaw(chainName)

                startForeground(NOTIFICATION_ID, createNotification("Checking transaction..."))
                startPolling(txHash, chain)
            }

            ACTION_STOP_POLLING -> {
                stopPolling()
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun startPolling(txHash: String, chain: Chain) {
        pollingJob?.cancel()

        pollingJob = serviceScope.launch {
            pollingTxStatus(chain, txHash)
                .collect { result ->
                    updateNotification(result.toNotificationMessage())
                    _statusFlow.emit(result)
                    when (result) {
                        is TransactionResult.Confirmed,
                        is TransactionResult.Failed,
                        TransactionResult.NotFound -> {
                            stopSelf()
                        }

                        else -> Unit
                    }
                }
        }
    }

    private fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    private fun createNotification(contentText: String): Notification {
        createNotificationChannel()

        val openAppIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Transaction Status")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(openAppPendingIntent)
            .build()
    }

    private fun updateNotification(contentText: String) {
        val notification = createNotification(contentText)
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Transaction Status",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows transaction status updates"
        }
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        stopPolling()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "transaction_status_channel"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_START_POLLING = "com.vultisig.wallet.START_POLLING"
        const val ACTION_STOP_POLLING = "com.vultisig.wallet.STOP_POLLING"
        const val EXTRA_TX_HASH = "tx_hash"
        const val EXTRA_CHAIN = "chain"
    }

    private fun TransactionResult.toNotificationMessage() = when (this) {
        TransactionResult.Confirmed -> "Transaction confirmed"
        is TransactionResult.Failed -> "Transaction failed"
        TransactionResult.NotFound -> "Transaction not found"
        TransactionResult.Pending -> "Transaction pending..."
    }
}