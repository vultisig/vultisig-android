package com.vultisig.wallet.models.cosmos

import com.vultisig.wallet.data.models.CosmosBalance

data class CosmosBalanceResponse(
    val balances: List<CosmosBalance>?,
)