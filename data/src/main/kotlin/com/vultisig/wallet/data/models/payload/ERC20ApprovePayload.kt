package com.vultisig.wallet.data.models.payload

import java.math.BigInteger

data class ERC20ApprovePayload(
    val amount: BigInteger,
    val spender: String,
)