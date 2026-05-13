package com.vultisig.wallet.data.api.models.thorchain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class ThorNameEntryJson(
    @SerialName("chain") val chain: String,
    @SerialName("address") val address: String,
)

@Serializable
internal data class ThorNameResponseJson(
    @SerialName("entries") val entries: List<ThorNameEntryJson>
)
