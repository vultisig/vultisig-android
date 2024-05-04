package com.voltix.wallet.chains

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class UtxoInfo(
    val hash: String,
    val amount: ULong,
    val index: UInt,
) : Parcelable {

}