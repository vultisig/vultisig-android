package com.vultisig.wallet.data.api.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class DeviceType {
    @SerialName("android") Android,
    @SerialName("apple") Apple,
    @SerialName("web") Web,
}

@Serializable
data class RegisterDeviceRequestJson(
    @SerialName("vault_id") val vaultId: String,
    @SerialName("party_name") val partyName: String,
    @SerialName("token") val token: String,
    @SerialName("device_type") val deviceType: DeviceType,
)

@Serializable
data class UnregisterDeviceRequestJson(
    @SerialName("vault_id") val vaultId: String,
    @SerialName("party_name") val partyName: String,
    @SerialName("token") val token: String? = null,
)

@Serializable
data class NotifyRequestJson(
    @SerialName("vault_id") val vaultId: String,
    @SerialName("vault_name") val vaultName: String,
    @SerialName("local_party_id") val localPartyId: String,
    @SerialName("qr_code_data") val qrCodeData: String,
)
