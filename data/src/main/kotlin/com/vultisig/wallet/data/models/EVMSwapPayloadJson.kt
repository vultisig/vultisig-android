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
    // SwapKit `/v3/swap` swap id for EVM/Solana routes — persisted on the tx-history row so a
    // cross-chain swap's Success is gated on the destination-leg `/track` settlement. Null for
    // direct EVM aggregators (1inch / Kyber / LiFi), which settle on the source chain.
    val swapId: String? = null,
)
