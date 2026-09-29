package com.vultisig.wallet.data.services

import java.util.concurrent.atomic.AtomicReference

/**
 * In-process handoff for a tapped keysign notification's payload. MainActivity is exported (it's
 * the launcher and deep-link target), so a payload carried as an intent extra could come from any
 * installed app. Only the non-exported [com.vultisig.wallet.app.activity.PushNotificationTapActivity]
 * writes here, so MainActivity reads a payload that can only have come from a notification this
 * app posted.
 */
internal object PendingPushPayload {

    private val payload = AtomicReference<String?>(null)

    fun set(qrCodeData: String) = payload.set(qrCodeData)

    /** Returns the pending payload once and clears it. */
    fun take(): String? = payload.getAndSet(null)
}
