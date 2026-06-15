package com.vultisig.wallet.ui.models.peer

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.vultisig.wallet.data.mediator.MediatorService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import timber.log.Timber

/**
 * Owns the Android lifecycle plumbing for the local [MediatorService]: registering the
 * service-started [BroadcastReceiver], starting the service, and unregistering on teardown.
 *
 * Extracted out of [KeygenPeerDiscoveryViewModel] so the ViewModel carries no framework
 * registration/`unregister` concerns and stays unit-testable — callers mock this controller and
 * assert on the [onServiceStarted] callback instead of standing up real receivers.
 */
internal class MediatorServiceController
@Inject
constructor(@ApplicationContext private val context: Context) {

    private var receiver: BroadcastReceiver? = null

    /**
     * Registers the service-started receiver and starts [MediatorService]. [onServiceStarted] is
     * invoked on the main thread once the service broadcasts that it is up. Safe to call once per
     * session; [stop] must be called to release the receiver.
     */
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    fun start(serviceName: String, onServiceStarted: () -> Unit) {
        val filter = IntentFilter().apply { addAction(MediatorService.SERVICE_ACTION) }
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (intent.action == MediatorService.SERVICE_ACTION) {
                        Timber.d("onReceive: Mediator service started")
                        onServiceStarted()
                    }
                }
            }
        this.receiver = receiver

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }

        MediatorService.start(context, serviceName)
    }

    /** Unregisters the receiver if it is still registered. Idempotent. */
    fun stop() {
        receiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (_: IllegalArgumentException) {
                // Already unregistered
            }
        }
        receiver = null
    }
}
