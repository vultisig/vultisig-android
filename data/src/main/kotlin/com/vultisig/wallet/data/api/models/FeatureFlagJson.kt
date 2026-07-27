package com.vultisig.wallet.data.api.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FeatureFlagJson(
    @SerialName("encrypt-gcm") val isEncryptGcmEnabled: Boolean = false,
    @SerialName("tss-batch") val isTssBatchEnabled: Boolean = false,
    // Gates the whole THORChain limit-swap (Place) flow. Defaults on now that the flow ships: the
    // remote payload doesn't carry the key yet, so the default is what users get, and publishing
    // `"limit-swap": false` remains the kill switch.
    @SerialName("limit-swap") val isLimitSwapEnabled: Boolean = true,
)
