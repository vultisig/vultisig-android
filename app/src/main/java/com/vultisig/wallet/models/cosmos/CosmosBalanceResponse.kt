package com.vultisig.wallet.models.cosmos

data class CosmosBalanceResponse(
    val balances: List<CosmosBalance>,
) {
}