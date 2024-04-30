package com.voltix.wallet.mediator

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder

class MediatorService() : Service() {
    private var server: Server? = null
    private var isRunning: Boolean = false
    private var serverName: String = ""
    override fun onCreate() {
        super.onCreate()
        server = Server(this as Context)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            val name = intent.getStringExtra("serverName")
            name?.let {
                if(!isRunning){
                    server?.startMediator(name)
                    isRunning = true
                }

                if (serverName == name)
                    return START_STICKY

                serverName = name
                server?.stopServer()
                server?.startMediator(name)
                isRunning = true
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        server?.stopServer()
        isRunning = false
        serverName = ""
    }
}