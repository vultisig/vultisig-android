package com.vultisig.wallet.data.api.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TcyStakerResponse(
    @SerialName("address")
    val address: String = "",
    
    @SerialName("amount")
    val unstakable: String? = null // Amount as string, 8 decimals, may be null if not available
)
