package com.vultisig.wallet.data.api.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FeatureFlagJson(
    @SerialName("encrypt-gcm")
    val isEncryptGcmEnabled: Boolean = false,
)