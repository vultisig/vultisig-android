package com.vultisig.wallet.data.keygen

import com.vultisig.wallet.data.api.SessionApi
import com.vultisig.wallet.data.mediator.Message
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import timber.log.Timber

/**
 * Polls the relay for one signing attempt's inbound messages and hands each batch to the caller,
 * which applies it to its own native session. Shared by the DKLS, Schnorr and ML-DSA keysign
 * helpers, whose loops are otherwise identical.
 *
 * @param onWaitingForPeers Invoked when no inbound messages have arrived for ~10 s; receives the
 *   silent peer IDs.
 * @param onPeersResumed Invoked when messages resume after [onWaitingForPeers] was called.
 * @param nanoTime Monotonic clock, injectable so the deadlines are testable.
 */
internal class KeysignMessagePoller(
    private val sessionApi: SessionApi,
    private val mediatorURL: String,
    private val sessionID: String,
    private val localPartyID: String,
    private val keysignCommittee: List<String>,
    private val onWaitingForPeers: ((List<String>) -> Unit)?,
    private val onPeersResumed: (() -> Unit)?,
    private val nanoTime: () -> Long = System::nanoTime,
) {
    private val heardFromThisWindow = mutableSetOf<String>()
    private val heardFromEver = mutableSetOf<String>()
    private var waitingNotified = false

    /** Whether any peer has answered since [resetForNewMessage]; the retry budget depends on it. */
    val hasHeardFromAnyPeer: Boolean
        get() = heardFromEver.isNotEmpty()

    /**
     * Drops the state that spans one message's retries, including a silent-peer hint the previous
     * message left raised. Call before a message's first attempt.
     */
    fun resetForNewMessage() {
        heardFromEver.clear()
        clearWaitingForPeers()
    }

    /** Records that [from] sent a message this device applied to its session. */
    fun recordPeerHeard(from: String) {
        heardFromThisWindow += from
        heardFromEver += from
    }

    /** Lowers the silent-peer flag, reporting the resumption if the flag had been raised. */
    fun clearWaitingForPeers() {
        if (waitingNotified) {
            waitingNotified = false
            onPeersResumed?.invoke()
        }
    }

    /**
     * Polls until [applyMessages] reports the protocol complete, or until the attempt's deadline.
     *
     * That deadline is absolute. A message this device cannot clear — one whose relay delete
     * failed, or whose body will not decrypt — is re-served by every poll, so extending the
     * deadline whenever a batch arrives would keep a doomed attempt alive until the relay expires
     * the message, stranding the user on the signing screen (#5488). Only the silent-peer hint
     * reads the resettable clock.
     */
    suspend fun poll(
        messageID: String,
        applyMessages: suspend (List<Message>) -> Boolean,
    ): Boolean {
        Timber.d("start pulling inbound messages")

        heardFromThisWindow.clear()
        val deadlineStart = nanoTime()
        var lastBatchNano = deadlineStart
        while (true) {
            try {
                val msgs =
                    sessionApi.getTssMessages(mediatorURL, sessionID, localPartyID, messageID)
                if (msgs.isNotEmpty()) {
                    clearWaitingForPeers()
                    lastBatchNano = nanoTime()
                    heardFromThisWindow.clear()
                    if (applyMessages(msgs)) {
                        return true
                    }
                } else {
                    delay(POLL_INTERVAL_MS)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to get messages")
                delay(POLL_INTERVAL_MS)
            }

            if (!waitingNotified && secondsSince(lastBatchNano) > PEER_SILENCE_HINT_SECONDS) {
                waitingNotified = true
                val silent = peersOutside(heardFromThisWindow)
                if (silent.isNotEmpty()) {
                    onWaitingForPeers?.invoke(silent)
                }
            }
            if (secondsSince(deadlineStart) > TIMEOUT_SECONDS) {
                error(timeoutMessage())
            }
        }
    }

    private fun secondsSince(nanos: Long): Double = (nanoTime() - nanos) / NANOS_PER_SECOND

    private fun peersOutside(heard: Set<String>): List<String> =
        keysignCommittee.filter { it != localPartyID && it !in heard }

    /**
     * Reads [heardFromEver] rather than the silence window, which holds only the most recent batch:
     * the deadline can now expire while peers are still answering, and naming one of them as absent
     * would send support down the wrong path.
     */
    private fun timeoutMessage(): String {
        val absent = peersOutside(heardFromEver)
        return if (absent.isEmpty()) {
            "keysign timed out after ${TIMEOUT_SECONDS}s: " +
                "all peers responded but the protocol did not complete"
        } else {
            "keysign timed out after ${TIMEOUT_SECONDS}s: no messages from ${absent.joinToString()}"
        }
    }

    private companion object {
        const val POLL_INTERVAL_MS = 100L
        const val PEER_SILENCE_HINT_SECONDS = 10
        const val TIMEOUT_SECONDS = 60
        const val NANOS_PER_SECOND = 1_000_000_000.0
    }
}
