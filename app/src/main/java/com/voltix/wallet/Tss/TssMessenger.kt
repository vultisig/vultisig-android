package com.voltix.wallet.Tss

class TssMessenger(
    private val serverAddress: String,
    private val sessionID: String,
    private val encryptionHex: String,
) : tss.Messenger {
    override fun send(from: String, to: String, body: String) {

    }
}