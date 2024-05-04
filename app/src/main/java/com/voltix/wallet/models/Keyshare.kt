package com.voltix.wallet.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class KeyShare(
    val pubKey: String,
    val keyshare: String,
):Parcelable
