package com.vultisig.wallet.data.api.models.thorchain

import kotlinx.serialization.Serializable

/** Response containing a staker's TCY stake amount and address. */
@Serializable data class TcyStakeResponse(val amount: String? = null, val address: String = "")
