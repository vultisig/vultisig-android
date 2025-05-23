package com.vultisig.wallet.data.mediator

import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.nsd.NsdManager
import android.os.IBinder
import timber.log.Timber

class MediatorService : Service() {
    private lateinit var nsdManager: NsdManager
    private var server: Server? = null
    private var isRunning: Boolean = false
    private var serverName: String = ""

    companion object {
        const val SERVICE_ACTION: String = "com.vultisig.wallet.mediator.MediatorService.STARTED"

        fun start(context: Context, serverName: String) {
            val intent = Intent(context, MediatorService::class.java)
            intent.putExtra("serverName", serverName)
            context.startService(intent)
            Timber.d("startMediatorService: Mediator service started")
        }

        fun stop(context: Context) {
            val intent = Intent(context, MediatorService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        nsdManager = getSystemService(NSD_SERVICE) as NsdManager
        server = Server(nsdManager)
        Timber.d("onCreate")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            val name = intent.getStringExtra("serverName")
            name?.let {
                if (isRunning) {
                    if (serverName == name) {// when the same server is started again,
                        broadcastServiceStarted()
                        return START_NOT_STICKY
                    } else {
                        server?.stopServer()
                    }
                }
                serverName = name
                server?.startMediator(name)
                isRunning = true
            }
        }
        Timber.d("onStartCommand")
        broadcastServiceStarted()
        return START_NOT_STICKY
    }

    private fun broadcastServiceStarted() {
        val intent = Intent()
        intent.setAction(SERVICE_ACTION)
        sendBroadcast(intent)
        Timber.d("broadcast service started")
    }

    override fun onBind(intent: Intent?): IBinder? {
        Timber.d("onBind")
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        server?.stopServer()
        isRunning = false
        serverName = ""
        server = null
    }
}