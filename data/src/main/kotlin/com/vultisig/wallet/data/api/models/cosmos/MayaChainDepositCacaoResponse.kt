package com.vultisig.wallet.data.api.models.cosmos

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class MayaChainDepositCacaoResponse(
    @SerialName("cacaoDeposit")
    val cacaoDeposit: String
)