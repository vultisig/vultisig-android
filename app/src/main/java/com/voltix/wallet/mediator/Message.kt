package com.voltix.wallet.mediator

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

data class Message(
    @SerializedName("session_id") val sessionID: String,
    @SerializedName("from") val from: String,
    @SerializedName("to") val to: List<String>,
    @SerializedName("body") val body: String,
    @SerializedName("hash") val hash: String,
    @SerializedName("sequence_no") val sequenceNo: Int,
) {
    fun toJson(): String {
        return gson.toJson(this)
    }

    companion object {
        private val gson = Gson()
        fun fromJson(json: String): Message {
            return gson.fromJson(json, Message::class.java)
        }
    }
}