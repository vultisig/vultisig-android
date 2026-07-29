package com.vultisig.wallet.app.startup

import android.content.Context
import androidx.startup.Initializer
import com.vultisig.wallet.data.services.KeysignNotificationChannel
import com.vultisig.wallet.data.services.PushRegistrationWorker

/**
 * Startup reconcile for push delivery.
 *
 * Creates the keysign channel before any push can arrive — a backgrounded message is rendered by
 * the FCM SDK, which never reaches the app's own code, so a channel created lazily on first
 * notification does not exist when it is needed.
 *
 * Then queues a re-registration. Nothing else re-registers after the initial opt-in, so a device
 * whose token rotated while a registration failed kept a dead token on the server with no path back
 * — pushes stopped permanently. Running this every launch is the healing path.
 */
internal class PushNotificationInitializer : Initializer<Unit> {

    override fun create(context: Context) {
        val appContext = context.applicationContext
        KeysignNotificationChannel.ensureCreated(appContext)
        // KEEP: a run already in flight is registering the same token; restarting it would only
        // reset its backoff.
        PushRegistrationWorker.enqueue(appContext, replaceExisting = false)
    }

    override fun dependencies(): List<Class<out Initializer<*>>> =
        listOf(WorkManagerInitializer::class.java)
}
