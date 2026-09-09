package com.vultisig.wallet.data.keygen

import com.vultisig.wallet.data.tss.TssMessenger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import timber.log.Timber

/**
 * Sends one outbound protocol message to every receiver of the round and waits for the relay to
 * accept all of them, throwing if any send exhausts its retry budget.
 *
 * Concurrently, deliberately. [TssMessenger.sendAwait] now spends up to a full
 * [com.vultisig.wallet.data.api.RelaySendBudget] — 39 s — before it gives up, and a serial loop
 * would multiply that by the committee size, so a three-signer round could burn past the 60 s
 * ceremony stall on retries alone. Fanning out in parallel keeps one round's worst case at a single
 * budget however many peers there are, which is what makes the awaited send affordable at all.
 *
 * A failure cancels the sibling sends and propagates: the round did not happen, and the attempt
 * wrapper retries the ceremony rather than waiting out a reply that can never arrive.
 */
internal suspend fun TssMessenger.fanOut(
    from: String,
    receivers: List<String>,
    encodedMessage: String,
    deadlineNanos: Long? = null,
) {
    if (receivers.isEmpty()) return
    coroutineScope {
        receivers
            .map { receiver ->
                async {
                    Timber.d("sending message from %s to: %s", from, receiver)
                    sendAwait(from, receiver, encodedMessage, deadlineNanos)
                }
            }
            .awaitAll()
    }
}
