package com.vultisig.wallet.data.mediator

import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.nsd.NsdManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.vultisig.wallet.data.common.Endpoints.LOCAL_MEDIATOR_SERVER_URL
import timber.log.Timber

class MediatorService : Service() {
    private lateinit var nsdManager: NsdManager
    private var server: Server? = null
    private var runningSessionId: String? = null
    private val handler = Handler(Looper.getMainLooper())
    private val expire = Runnable { stopSelf() }

    companion object {
        const val SERVICE_ACTION: String = "com.vultisig.wallet.mediator.MediatorService.STARTED"
        private const val SERVICE_NAME = "serverName"
        private const val SESSION_ID = "sessionId"

        fun start(context: Context, serverName: String, sessionId: String, encryptionKeyHex: String) {
            val auth = requireNotNull(LocalMediatorAuth.create(sessionId, encryptionKeyHex)) {
                "Invalid local mediator session"
            }
            check(LocalMediatorCredentials.bind(LOCAL_MEDIATOR_SERVER_URL, auth))
            val intent = Intent(context, MediatorService::class.java)
            intent.putExtra(SERVICE_NAME, serverName)
            intent.putExtra(SESSION_ID, sessionId)
            try {
                context.startService(intent)
            } catch (error: RuntimeException) {
                LocalMediatorCredentials.clear(LOCAL_MEDIATOR_SERVER_URL, sessionId)
                throw error
            }
        }

        fun isStartNotificationFor(intent: Intent, sessionId: String): Boolean =
            intent.action == SERVICE_ACTION && intent.getStringExtra(SESSION_ID) == sessionId

        fun stop(context: Context) {
            context.stopService(Intent(context, MediatorService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        nsdManager = getSystemService(NSD_SERVICE) as NsdManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val name = intent?.getStringExtra(SERVICE_NAME)
        val sessionId = intent?.getStringExtra(SESSION_ID)
        val auth = LocalMediatorCredentials.get(LOCAL_MEDIATOR_SERVER_URL)?.auth
        if (name.isNullOrBlank() || auth == null || sessionId != auth.sessionId) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        if (runningSessionId != sessionId) {
            stopMediator()
            val mediator = Server(nsdManager, auth)
            server = mediator
            runningSessionId = sessionId
            try {
                mediator.startMediator(name)
                handler.postDelayed(expire, LocalMediatorAuth.SESSION_DURATION_MS)
            } catch (error: RuntimeException) {
                Timber.e(error, "Local mediator did not start")
                stopSelf(startId)
                return START_NOT_STICKY
            }
        }

        // Scope service readiness to this app and session.
        sendBroadcast(Intent(SERVICE_ACTION).setPackage(packageName).putExtra(SESSION_ID, sessionId))
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        runCatching { stopMediator() }
        super.onDestroy()
    }

    private fun stopMediator() {
        handler.removeCallbacks(expire)
        try {
            server?.stopServer()
        } finally {
            runningSessionId?.let { LocalMediatorCredentials.clear(LOCAL_MEDIATOR_SERVER_URL, it) }
            runningSessionId = null
            server = null
        }
    }
}
