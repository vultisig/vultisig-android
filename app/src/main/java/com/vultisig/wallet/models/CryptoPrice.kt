package com.vultisig.wallet.models

import com.google.gson.annotations.SerializedName

data class CryptoPrice(
    @SerializedName("prices") var prices: Map<String, Map<String, Double>>
)
