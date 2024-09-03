package com.vultisig.wallet.data.models

data class CoinCmcPrice(
    val tokenId: String,
    val cmcId: Int?,
    val chain: Chain,
    val contractAddress: String,
) {
    val isNativeToken = contractAddress.isBlank()
}