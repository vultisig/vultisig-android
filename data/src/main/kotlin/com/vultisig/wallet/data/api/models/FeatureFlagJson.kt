package com.vultisig.wallet.data.api.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FeatureFlagJson(
    @SerialName("encrypt-gcm") val isEncryptGcmEnabled: Boolean = false,
    @SerialName("tss-batch") val isTssBatchEnabled: Boolean = false,
    // Gates the whole THORChain limit-swap (Place) flow. Defaults off so the Limit tab stays a
    // placeholder until the flow ships and the remote flag is flipped on.
    @SerialName("limit-swap") val isLimitSwapEnabled: Boolean = false,
)
