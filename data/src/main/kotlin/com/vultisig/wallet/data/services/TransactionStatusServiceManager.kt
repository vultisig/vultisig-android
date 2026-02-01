package com.vultisig.wallet.data.services

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.usecases.txstatus.TransactionResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

class TransactionStatusServiceManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private var serviceBinder: TransactionStatusService? = null
    private val _serviceReady = MutableStateFlow(false)
    val serviceReady: StateFlow<Boolean> = _serviceReady.asStateFlow()

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            serviceBinder = (binder as? TransactionStatusService.LocalBinder)?.getService()
            _serviceReady.value = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            serviceBinder = null
            _serviceReady.value = false
        }
    }

    fun startPolling(txHash: String, chain: Chain) {
        val intent = Intent(context, TransactionStatusService::class.java).apply {
            action = TransactionStatusService.ACTION_START_POLLING
            putExtra(TransactionStatusService.EXTRA_TX_HASH, txHash)
            putExtra(TransactionStatusService.EXTRA_CHAIN, chain.raw)
        }

        context.startForegroundService(intent)
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    fun stopPolling() {
        if (_serviceReady.value) {
            try {
                context.unbindService(serviceConnection)
            } catch (_: IllegalArgumentException) {
                // Service already unbound
            }
        }
        _serviceReady.value = false

        val intent = Intent(context, TransactionStatusService::class.java).apply {
            action = TransactionStatusService.ACTION_STOP_POLLING
        }
        context.startService(intent)
    }

    fun cancelPollingAndRemoveNotification() {
        serviceBinder?.cancelPollingAndRemoveNotification()

        if (_serviceReady.value) {
            try {
                context.unbindService(serviceConnection)
            } catch (_: IllegalArgumentException) {
                // Service already unbound
            }
        }
        _serviceReady.value = false
    }

    fun getStatusFlow(): Flow<TransactionResult>? {
        return serviceBinder?.statusFlow
    }

    fun cleanup() {
        if (_serviceReady.value) {
            try {
                context.unbindService(serviceConnection)
            } catch (_: IllegalArgumentException) {
                // Service already unbound
            }
        }
        _serviceReady.value = false
    }
}