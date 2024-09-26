package com.vultisig.wallet.data.models

import com.vultisig.wallet.data.models.settings.AppCurrency
import com.vultisig.wallet.data.repositories.BlockChainSpecificAndUtxo
import java.math.BigInteger

data class GasFeeParams(
    val gasLimit: BigInteger,
    val gasFee: TokenValue,
    val selectedToken: Coin,
)