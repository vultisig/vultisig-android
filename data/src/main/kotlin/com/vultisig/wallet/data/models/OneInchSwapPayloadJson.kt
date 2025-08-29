package com.vultisig.wallet.data.models

import com.vultisig.wallet.data.api.models.quotes.OneInchSwapQuoteJson
import java.math.BigDecimal
import java.math.BigInteger

data class OneInchSwapPayloadJson(
    val fromCoin: Coin,
    val toCoin: Coin,
    val fromAmount: BigInteger,
    val toAmountDecimal: BigDecimal,
    val quote: OneInchSwapQuoteJson,
    val provider: String,
)