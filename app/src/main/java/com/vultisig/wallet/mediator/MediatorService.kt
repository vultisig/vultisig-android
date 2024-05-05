package com.vultisig.wallet.mediator

import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.nsd.NsdManager
import android.os.IBinder
import android.util.Log

class MediatorService() : Service() {
    private lateinit var nsdManager: NsdManager
    private var server: Server? = null
    private var isRunning: Boolean = false
    private var serverName: String = ""

    companion object{
        const val SERVICE_ACTION = "com.vultisig.wallet.mediator.MediatorService.STARTED"
    }
    override fun onCreate() {
        super.onCreate()
        nsdManager = getSystemService(Context.NSD_SERVICE) as NsdManager
        server = Server(nsdManager)
        Log.d("MediatorService", "onCreate")
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
        Log.d("MediatorService", "onStartCommand")
        broadcastServiceStarted()
        return START_NOT_STICKY
    }

    private fun broadcastServiceStarted() {
        val intent = Intent()
        intent.setAction(SERVICE_ACTION)
        sendBroadcast(intent)
        Log.d("MediatorService", "broadcast service started")
    }

    override fun onBind(intent: Intent?): IBinder? {
        Log.d("MediatorService", "onBind")
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