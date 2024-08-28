package com.vultisig.wallet.models

import com.google.gson.annotations.SerializedName
import java.math.BigInteger

internal data class ERC20ApprovePayload(
    @SerializedName("amount")
    val amount: BigInteger,
    @SerializedName("spender")
    val spender: String,
)