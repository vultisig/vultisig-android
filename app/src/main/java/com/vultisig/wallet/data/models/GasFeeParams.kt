package com.vultisig.wallet.data.models

import java.math.BigInteger

data class GasFeeParams(
    val gasLimit: BigInteger,
    val gasFee: TokenValue,
    val selectedToken: Coin,
    val perUnit: Boolean = false,
)

internal data class EstimatedGasFee(
    val formattedTokenValue: String,
    val formattedFiatValue: String,
    val fiatValue: FiatValue
)