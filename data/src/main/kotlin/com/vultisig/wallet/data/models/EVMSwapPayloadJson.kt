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
    /**
     * SwapKit sub-provider (Chainflip / NEAR / Garden) carried on the persisted payload so the done
     * screen and history render `SwapKit (<sub-provider>)` instead of the collapsed canonical
     * [provider] `"SwapKit"`. Null for 1inch / Kyber / LI.FI, whose [provider] is already specific.
     */
    val subProvider: String? = null,
)
