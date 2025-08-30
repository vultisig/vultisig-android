package com.vultisig.wallet.data.models

import com.vultisig.wallet.data.api.models.quotes.EVMSwapQuoteJson
import java.math.BigDecimal
import java.math.BigInteger

data class EVMSwapPayloadJson(
    val fromCoin: Coin,
    val toCoin: Coin,
    val fromAmount: BigInteger,
    val toAmountDecimal: BigDecimal,
    val quote: EVMSwapQuoteJson,
    val provider: String,
)