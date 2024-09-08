package com.vultisig.wallet.data.api.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class CmcIdResponseJson(
    @SerialName("data")
    val data:  Map<String, CmcIdJson>
)

@Serializable
internal data class CmcIdJson(
    @SerialName("id")
    val id: Int
)
