package com.vultisig.wallet.data.services

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.vultisig.wallet.data.models.Chain
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class TransactionStatusServiceManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private var serviceBinder: TransactionStatusService? = null
    private var isBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            serviceBinder = (binder as? TransactionStatusService.LocalBinder)?.getService()
            isBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            serviceBinder = null
            isBound = false
        }
    }

    fun startPolling(txHash: String, chain: Chain) {
        val intent = Intent(context, TransactionStatusService::class.java).apply {
            action = TransactionStatusService.ACTION_START_POLLING
            putExtra(TransactionStatusService.EXTRA_TX_HASH, txHash)
            putExtra(TransactionStatusService.EXTRA_CHAIN, chain.name)
        }

        context.startForegroundService(intent)

        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    fun stopPolling() {
        if (isBound) {
            try {
                context.unbindService(serviceConnection)
            } catch (e: IllegalArgumentException) {
                // Service already unbound
            }
            isBound = false
        }

        val intent = Intent(context, TransactionStatusService::class.java).apply {
            action = TransactionStatusService.ACTION_STOP_POLLING
        }
        context.startService(intent)
    }

    fun getStatusFlow(): Flow<TransactionStatusUpdate>? {
        return serviceBinder?.statusFlow
    }

    fun cleanup() {
        if (isBound) {
            try {
                context.unbindService(serviceConnection)
            } catch (e: IllegalArgumentException) {
                // Service already unbound
            }
            isBound = false
        }
    }
}