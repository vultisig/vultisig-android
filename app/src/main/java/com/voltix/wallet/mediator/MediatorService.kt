package com.voltix.wallet.mediator

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder

class MediatorService() : Service() {
    private val server: Server = Server(this as Context)
    override fun onCreate() {
        super.onCreate()
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            val name = intent.getStringExtra("name")
            name?.let {
                server.startMediator(name)
            }
        }

        return START_STICKY
    }
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
    override fun onDestroy() {
        super.onDestroy()
        server.stopServer()
    }
}