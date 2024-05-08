package com.vultisig.wallet.presenter.keysign

import android.os.Parcelable
import com.google.gson.GsonBuilder
import kotlinx.parcelize.Parcelize

@Parcelize
data class KeysignMesssage(
    val sessionID: String,
    val serviceName: String,
    val payload: KeysignPayload,
    val encryptionKeyHex: String,
    val usevultisigRelay: Boolean,
) : Parcelable {
    fun toJson(): String {
        val gson = GsonBuilder()
            .registerTypeAdapter(
                BlockChainSpecific::class.java,
                BlockChainSpecificSerializer()
            )
            .create()
        return gson.toJson(this)
    }

    companion object {
        fun fromJson(json: String): KeysignMesssage {
            val gson = GsonBuilder()
                .registerTypeAdapter(
                    BlockChainSpecific::class.java,
                    BlockChainSpecificDeserializer()
                )
                .create()
            return gson.fromJson(json, KeysignMesssage::class.java)
        }
    }
}