package com.vultisig.wallet.models

import com.google.gson.annotations.SerializedName
import java.math.BigDecimal

data class CryptoPrice(
    @SerializedName("prices") var prices: Map<String, BigDecimal>
)
