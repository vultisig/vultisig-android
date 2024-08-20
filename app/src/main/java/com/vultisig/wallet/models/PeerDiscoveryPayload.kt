package com.vultisig.wallet.models

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.annotations.SerializedName

internal data class KeygenWrapper(
    @SerializedName("_0")
    val keygenMessage: KeygenMessage
)
internal data class ReshareWrapper(
    @SerializedName("_0")
    val reshareMessage: ReshareMessage
)
internal sealed class PeerDiscoveryPayload {
    data class Keygen(val keygenMessage: KeygenMessage) : PeerDiscoveryPayload()
    data class Reshare(val reshareMessage: ReshareMessage) : PeerDiscoveryPayload()

    fun toJson(gson: Gson): String {
        return when (this) {
            is Keygen -> gson.toJson(mapOf("Keygen" to KeygenWrapper(this.keygenMessage)))
            is Reshare -> gson.toJson(mapOf("Reshare" to ReshareWrapper(this.reshareMessage)))
        }
    }

    companion object {
        fun fromJson(gson: Gson, json: String): PeerDiscoveryPayload {
            return try {
                val jsonObject = gson.fromJson(json, JsonObject::class.java)
                if (jsonObject.has("Keygen")) {
                    val keygenMsg =
                        gson.fromJson(
                            jsonObject.get("Keygen").asJsonObject.get("_0"),
                            KeygenMessage::class.java
                        )
                    Keygen(keygenMsg)
                } else if (jsonObject.has("Reshare")) {
                    val reshareMsg =
                        gson.fromJson(
                            jsonObject.get("Reshare").asJsonObject.get("_0"),
                            ReshareMessage::class.java
                        )
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