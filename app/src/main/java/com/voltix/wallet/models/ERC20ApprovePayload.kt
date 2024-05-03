package com.voltix.wallet.models

import java.math.BigInteger

data class ERC20ApprovePayload(
    val amount: BigInteger,
    val spender: String,
) {
}