package com.voltix.wallet.models

import com.google.gson.Gson

sealed class PeerDiscoveryPayload {
    data class Keygen(val keygenMessage: KeygenMessage) : PeerDiscoveryPayload()
    data class Reshare(val reshareMessage: ReshareMessage) : PeerDiscoveryPayload()
    fun toJson(): String {
        return Gson().toJson(this)
    }
}