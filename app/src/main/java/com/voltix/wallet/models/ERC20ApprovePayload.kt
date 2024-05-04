package com.voltix.wallet.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.math.BigInteger

@Parcelize
data class ERC20ApprovePayload(
    val amount: BigInteger,
    val spender: String,
) : Parcelable {
}