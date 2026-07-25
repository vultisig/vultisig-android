package com.vultisig.wallet.data.services

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.usecases.txstatus.TransactionResult
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

/**
 * Bound to the running foreground [TransactionStatusService], so it must be a singleton: every
 * injection site (the poller that starts it and the screen that completes it) needs to observe and
 * cancel the same bound service, not a fresh, never-bound instance.
 */
@Singleton
class TransactionStatusServiceManager
@Inject
constructor(@param:ApplicationContext private val context: Context) {
    private var serviceBinder: TransactionStatusService? = null
    private var isBound = false
    private val _serviceReady = MutableStateFlow(false)
    val serviceReady: StateFlow<Boolean> = _serviceReady.asStateFlow()

    private val serviceConnection =
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                serviceBinder = (binder as? TransactionStatusService.LocalBinder)?.getService()
                isBound = true
                _serviceReady.value = true
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                serviceBinder = null
                isBound = false
                _serviceReady.value = false
            }
        }

    /**
     * Starts the foreground status service for [txHash] on [chain] and binds to it.
     *
     * @return `false` when the system rejected the binding: `onServiceConnected()` never fires in
     *   that case, so [serviceReady] stays `false` and callers must not wait on it.
     */
    fun startPolling(txHash: String, chain: Chain): Boolean {
        val intent =
            Intent(context, TransactionStatusService::class.java).apply {
                action = TransactionStatusService.ACTION_START_POLLING
                putExtra(TransactionStatusService.EXTRA_TX_HASH, txHash)
                putExtra(TransactionStatusService.EXTRA_CHAIN, chain.raw)
            }
        context.startForegroundService(intent)
        if (isBound) return true

        // Marked bound on request, not on connect: a binding that has not connected yet still
        // holds the service alive and must be released by stopPolling().
        isBound = true
        val bound =
            runCatching { context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE) }
                .onFailure { Timber.w(it, "Failed to bind transaction status service") }
                .getOrDefault(false)
        if (!bound) {
            // A rejected bindService still registers the connection, so release it and drop back to
            // unbound; otherwise the guard above would suppress every later bind attempt.
            unbindIfBound()
        }
        return bound
    }

    /**
     * Tears the status service down without starting it: [Context.startService] is background
     * restricted since Android 8 and this runs from `onCleared()`, which may fire while the process
     * has no foreground state. The bound service is stopped through its binder (which also clears a
     * notification left behind by `STOP_FOREGROUND_DETACH`), with [Context.stopService] as the
     * fallback when the binding never connected. Never throws.
     */
    fun stopPolling() {
        runCatching { serviceBinder?.cancelPollingAndRemoveNotification() }
            .onFailure { Timber.w(it, "Failed to cancel transaction status polling") }

        unbindIfBound()
        serviceBinder = null
        _serviceReady.value = false

        runCatching { context.stopService(Intent(context, TransactionStatusService::class.java)) }
            .onFailure { Timber.w(it, "Failed to stop transaction status service") }
    }

    fun cancelPollingAndRemoveNotification() {
        serviceBinder?.cancelPollingAndRemoveNotification()
        unbindIfBound()
        _serviceReady.value = false
    }

    fun getStatusFlow(): Flow<TransactionResult>? {
        return serviceBinder?.statusFlow
    }

    fun cleanup() {
        unbindIfBound()
        _serviceReady.value = false
    }

    /**
     * Releases the binding if one is held. [isBound] is cleared up front so a failed unbind can't
     * strand the manager as permanently bound and block later [startPolling] calls. Never throws.
     */
    private fun unbindIfBound() {
        if (!isBound) return
        isBound = false
        runCatching { context.unbindService(serviceConnection) }
            .onFailure { Timber.w(it, "Failed to unbind transaction status service") }
    }
}
