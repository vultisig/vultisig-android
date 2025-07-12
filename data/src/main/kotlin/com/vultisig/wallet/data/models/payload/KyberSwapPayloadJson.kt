package com.vultisig.wallet.data.models.payload

import com.vultisig.wallet.data.api.models.quotes.KyberSwapQuoteJson
import com.vultisig.wallet.data.models.Coin
import java.math.BigDecimal
import java.math.BigInteger

data class KyberSwapPayloadJson(
    val fromCoin: Coin,
    val toCoin: Coin,
    val fromAmount: BigInteger,
    val toAmountDecimal: BigDecimal,
    val quote: KyberSwapQuoteJson,
)