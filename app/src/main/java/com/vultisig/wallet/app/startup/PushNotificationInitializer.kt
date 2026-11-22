package com.vultisig.wallet.app.startup

import android.app.NotificationManager
import android.content.Context
import androidx.startup.Initializer
import com.vultisig.wallet.data.services.KeysignNotificationChannel
import com.vultisig.wallet.data.services.PushRegistrationWorker

/**
 * Startup reconcile for push delivery.
 *
 * Creates the keysign channel and removes any Google Play services notification delegation set by
 * a previous app version. The Firebase manifest setting applies when delegation first initializes.
 *
 * Then queues a re-registration. Nothing else re-registers after the initial opt-in, so a device
 * whose token rotated while a registration failed kept a dead token on the server with no path back
 * — pushes stopped permanently. Running this every launch is the healing path.
 */
internal class PushNotificationInitializer : Initializer<Unit> {

    override fun create(context: Context) {
        val appContext = context.applicationContext
        KeysignNotificationChannel.ensureCreated(appContext)
        val manager = appContext.getSystemService(NotificationManager::class.java)
        if (manager?.notificationDelegate == PLAY_SERVICES_PACKAGE) {
            manager.notificationDelegate = null
        }
        // KEEP: a run already in flight is registering the same token; restarting it would only
        // reset its backoff.
        PushRegistrationWorker.enqueue(appContext, replaceExisting = false)
    }

    override fun dependencies(): List<Class<out Initializer<*>>> =
        listOf(WorkManagerInitializer::class.java)

    private companion object {
        const val PLAY_SERVICES_PACKAGE = "com.google.android.gms"
    }
}
