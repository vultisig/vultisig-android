package com.voltix.wallet.models

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParseException

sealed class PeerDiscoveryPayload {
    data class Keygen(val keygenMessage: KeygenMessage) : PeerDiscoveryPayload()
    data class Reshare(val reshareMessage: ReshareMessage) : PeerDiscoveryPayload()

    fun toJson(): String {
        return Gson().toJson(this)
    }

    companion object {
        fun fromJson(json: String): PeerDiscoveryPayload {
            val gson = Gson()
            return try {
                val jsonObject = gson.fromJson(json, JsonObject::class.java)
                if (jsonObject.has("keygenMessage")) {
                    val keygenMsg =
                        gson.fromJson(jsonObject.get("keygenMessage"), KeygenMessage::class.java)
                    Keygen(keygenMsg)
                } else if (jsonObject.has("reshareMessage")) {
                    val reshareMsg =
                        gson.fromJson(jsonObject.get("reshareMessage"), ReshareMessage::class.java)
                    Reshare(reshareMsg)
                } else {
                    throw JsonParseException("Invalid JSON format for PeerDiscoveryPayload")
                }
            } catch (e: Exception) {
                throw JsonParseException("Failed to parse JSON into PeerDiscoveryPayload", e)
            }
        }
    }
}