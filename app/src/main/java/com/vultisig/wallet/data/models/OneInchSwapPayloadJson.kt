package com.vultisig.wallet.data.models

import com.vultisig.wallet.data.api.models.OneInchSwapQuoteJson
import com.vultisig.wallet.models.Coin
import java.math.BigDecimal
import java.math.BigInteger

internal data class OneInchSwapPayloadJson(
    val fromCoin: Coin,
    val toCoin: Coin,
    val fromAmount: BigInteger,
    val toAmountDecimal: BigDecimal,
    val quote: OneInchSwapQuoteJson,
)