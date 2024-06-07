package com.vultisig.wallet.data.models

import kotlin.time.Duration

internal data class SwapQuote(
    val expectedDstValue: TokenValue,
    val fees: TokenValue,
    val estimatedTime: Duration?,
    val inboundAddress: String?,
    val routerAddress: String?,
)