package com.vultisig.wallet.data.api.models.thorchain

import kotlinx.serialization.Serializable

@Serializable data class TcyStakeResponse(val amount: String? = null, val address: String = "")
