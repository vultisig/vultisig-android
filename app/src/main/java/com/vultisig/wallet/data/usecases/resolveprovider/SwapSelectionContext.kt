package com.vultisig.wallet.data.usecases.resolveprovider

import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.TokenValue

internal data class SwapSelectionContext(
    val srcToken: Coin,
    val dstToken: Coin,
    val value: TokenValue,
)