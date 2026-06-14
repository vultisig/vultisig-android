package com.vultisig.wallet.ui.models.keysign

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.vultisig.wallet.data.api.SessionApi
import com.vultisig.wallet.data.api.models.signer.JoinKeysignRequestJson
import com.vultisig.wallet.data.mediator.MediatorService
import com.vultisig.wallet.data.repositories.VultiSignerRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import timber.log.Timber

/**
 * Owns the keysign mediator-service lifecycle and the relay/local session start.
 *
 * Extracted verbatim from `KeysignFlowViewModel` — the `BroadcastReceiver`, the `MediatorService`
 * start/stop, and the `SessionApi`/`VultiSignerRepository` session calls used to live inline in the
 * ViewModel. The ViewModel keeps the coordination (deciding when to start the service, what to do
 * once it is up); this class owns the Android service plumbing and the network calls so they are
 * testable and reusable in isolation.
 *
 * One instance per `KeysignFlowViewModel` (unscoped constructor injection): it holds the receiver
 * registration state, so [registerServiceReceiver]/[unregisterServiceReceiver] must be balanced
 * over the ViewModel lifetime.
 */
internal class KeysignSessionCoordinator
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val sessionApi: SessionApi,
    private val vultiSignerRepository: VultiSignerRepository,
) {
    private var serviceStartedReceiver: BroadcastReceiver? = null

    /**
     * Registers the broadcast receiver for [MediatorService.SERVICE_ACTION] and starts the local
     * mediator service. [onServiceStarted] is invoked on the main thread when the service signals
     * it is up — the ViewModel uses it to kick off the local session + participant discovery.
     */
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    fun startMediatorService(serviceName: String, onServiceStarted: () -> Unit) {
        registerServiceReceiver(onServiceStarted)
        MediatorService.start(context, serviceName)
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun registerServiceReceiver(onServiceStarted: () -> Unit) {
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (intent.action == MediatorService.SERVICE_ACTION) {
                        Timber.tag("KeysignFlowViewModel").d("onReceive: Mediator service started")
                        onServiceStarted()
                    }
                }
            }
        serviceStartedReceiver = receiver

        val filter = IntentFilter()
        filter.addAction(MediatorService.SERVICE_ACTION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
    }

    /** Unregisters the broadcast receiver if one was registered. Safe to call multiple times. */
    fun unregisterServiceReceiver() {
        val receiver = serviceStartedReceiver ?: return
        try {
            context.unregisterReceiver(receiver)
        } catch (_: IllegalArgumentException) {
            // receiver was already unregistered or never registered
        }
        serviceStartedReceiver = null
    }

    /** Stops the local mediator service. */
    fun stopService() {
        val intent = Intent(context, MediatorService::class.java)
        context.stopService(intent)
        Timber.d("stopService: Mediator service stopped")
    }

    /**
     * Starts a single session (relay path). When [joinRequest] is non-null (fast-sign), also
     * notifies the VultiSigner server to join the keysign. Failures are logged and swallowed —
     * matching the original relay behavior where a failed start did not surface an error state.
     */
    suspend fun startSession(
        serverAddr: String,
        sessionID: String,
        localPartyID: String,
        joinRequest: JoinKeysignRequestJson?,
    ) {
        try {
            sessionApi.startSession(serverAddr, sessionID, listOf(localPartyID))
            Timber.tag("KeysignFlowViewModel").d("startSession: Session started")
            if (joinRequest != null) {
                Timber.tag("KeysignFlowViewModel")
                    .d(
                        "joinKeysign: chain=${joinRequest.chain}, isEcdsa=${joinRequest.isEcdsa}, mldsa=${joinRequest.mldsa}, messages=${joinRequest.messages.map { it.take(16) }}"
                    )
                vultiSignerRepository.joinKeysign(joinRequest)
                Timber.tag("KeysignFlowViewModel").d("joinKeysign: server notified successfully")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.tag("KeysignFlowViewModel").e("startSession: ${e.stackTraceToString()}")
        }
    }

    /**
     * Starts the session with up to four retries and exponential backoff (local mediator path).
     * Returns `true` once the session starts, `false` when every attempt fails — the caller decides
     * whether to surface an error state.
     */
    suspend fun startSessionWithRetry(
        serverAddr: String,
        sessionID: String,
        localPartyID: String,
        joinRequest: JoinKeysignRequestJson?,
    ): Boolean {
        var delayMs = 200L
        repeat(4) { attempt ->
            try {
                Timber.tag("KeysignFlowViewModel")
                    .d("startSessionWithRetry: Attempt ${attempt + 1}")
                sessionApi.startSession(serverAddr, sessionID, listOf(localPartyID))
                Timber.tag("KeysignFlowViewModel").d("startSession: Session started")
                if (joinRequest != null) {
                    vultiSignerRepository.joinKeysign(joinRequest)
                }
                return true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.tag("KeysignFlowViewModel")
                    .e(e, "startSessionWithRetry: Attempt ${attempt + 1} failed")
                if (attempt < 3) {
                    delay(delayMs)
                    delayMs *= 2
                } else {
                    Timber.tag("KeysignFlowViewModel").e("All attempts to start session failed")
                }
            }
        }
        return false
    }

    /** Starts the keysign round with the selected [committee]. */
    suspend fun startWithCommittee(serverAddr: String, sessionID: String, committee: List<String>) {
        sessionApi.startWithCommittee(serverAddr, sessionID, committee)
    }
}
