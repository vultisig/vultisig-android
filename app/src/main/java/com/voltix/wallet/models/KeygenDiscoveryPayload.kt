package com.voltix.wallet.models

import com.google.gson.Gson

data class KeygenDiscoveryPayload(
    val sessionID: String,
    val hexChainCode: String,
    val serviceName: String,
    val encryptionKeyHex: String,
    val useVoltixRelay: Boolean
) {
    fun toJson(): String {
        val gson = Gson()
        return gson.toJson(this)
    }
}
