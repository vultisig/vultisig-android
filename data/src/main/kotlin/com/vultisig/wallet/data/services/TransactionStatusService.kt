package com.vultisig.wallet.data.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.vultisig.wallet.data.R
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.usecases.txstatus.PollingTxStatusUseCase
import com.vultisig.wallet.data.usecases.txstatus.TransactionResult
import com.vultisig.wallet.data.usecases.txstatus.TxStatusConfigurationProvider
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import kotlin.time.Duration.Companion.minutes

@AndroidEntryPoint
class TransactionStatusService : Service() {

    @Inject
    lateinit var pollingTxStatus: PollingTxStatusUseCase

    @Inject
    lateinit var txStatusConfigurationProvider: TxStatusConfigurationProvider

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pollingJob: Job? = null

    private val _statusFlow = MutableSharedFlow<TransactionStatusUpdate>(
        replay = 1,
        extraBufferCapacity = 10
    )
    val statusFlow: SharedFlow<TransactionStatusUpdate> = _statusFlow.asSharedFlow()

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
        
        val config = txStatusConfigurationProvider.getConfigurationForChain(chain)
        val startTime = Instant.now()
        handleResult(TransactionResult.Pending, txHash, "Transaction confirmed")

        pollingJob = serviceScope.launch {
            pollingTxStatus(chain, txHash)
                .collect { result ->
                    val elapsed = Duration.between(startTime, Instant.now()).toMillis()
                    val maxWaitMillis = config.maxWaitMinutes.minutes.inWholeMilliseconds

                    when (result) {
                        is TransactionResult.Confirmed -> {
                            handleResult(result, txHash, "Transaction confirmed")
                            stopSelf()
                        }
                        is TransactionResult.Failed -> {
                            handleResult(result, txHash, "Transaction failed: ${result.reason}")
                            stopSelf()
                        }
                        TransactionResult.NotFound -> {
                            if (elapsed > maxWaitMillis) {
                                handleResult(result, txHash, "Transaction not found")
                                stopSelf()
                            } else {
                                updateNotification("Still searching for transaction...")
                                _statusFlow.emit(TransactionStatusUpdate(txHash, result))
                            }
                        }
                        TransactionResult.Pending -> {
                            updateNotification("Transaction pending...")
                            _statusFlow.emit(TransactionStatusUpdate(txHash, result))
                        }
                    }
                }
        }
    }

    private fun handleResult(result: TransactionResult, txHash: String, message: String) {
        updateNotification(message)
        _statusFlow.tryEmit(TransactionStatusUpdate(txHash, result))
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
            .setSmallIcon(R.drawable.aave)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(openAppPendingIntent)
            .build()
    }

    private fun updateNotification(contentText: String) {
        val notification = createNotification(contentText)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
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
}

data class TransactionStatusUpdate(
    val txHash: String,
    val result: TransactionResult
)