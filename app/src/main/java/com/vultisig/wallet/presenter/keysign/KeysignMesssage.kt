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
            .registerTypeAdapter(KeysignPayload::class.java, KeysignPayloadSerializer())
            .registerTypeAdapter(
                BlockChainSpecific.UTXO::class.java,
                BlockChainSpecificSerializer()
            )
            .registerTypeAdapter(
                BlockChainSpecific.Cosmos::class.java,
                BlockChainSpecificSerializer()
            )
            .registerTypeAdapter(
                BlockChainSpecific.THORChain::class.java,
                BlockChainSpecificSerializer()
            ).registerTypeAdapter(
                BlockChainSpecific.Sui::class.java,
                BlockChainSpecificSerializer()
            ).registerTypeAdapter(
                BlockChainSpecific.Polkadot::class.java,
                BlockChainSpecificSerializer()
            ).registerTypeAdapter(
                BlockChainSpecific.Solana::class.java,
                BlockChainSpecificSerializer()
            ).registerTypeAdapter(
                BlockChainSpecific.Ethereum::class.java,
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
                .registerTypeAdapter(
                    KeysignPayload::class.java,
                    KeysignPayloadDeserializer()
                )
                .create()
            return gson.fromJson(json, KeysignMesssage::class.java)
        }
    }
}