package com.vultisig.wallet.data.models

import com.vultisig.wallet.data.crypto.ThorChainHelper.Companion.SECURE_ASSETS_TICKERS
import wallet.core.jni.CoinType

typealias TokenId = String

data class Coin(
    val chain: Chain,
    val ticker: String,
    val logo: String,
    val address: String,
    val decimal: Int,
    val hexPublicKey: String,
    val priceProviderID: String,
    val contractAddress: String,
    val isNativeToken: Boolean,
) {
    val id: TokenId
        get() = "${ticker}-${chain.id}"

    val coinType: CoinType
        get() = chain.coinType

    companion object {
        val EMPTY = Coin(
            chain = Chain.ThorChain,
            ticker = "",
            logo = "",
            address = "",
            decimal = 0,
            hexPublicKey = "",
            priceProviderID = "",
            contractAddress = "",
            isNativeToken = false,
        )
    }

}

fun Coin.allowZeroGas(): Boolean {
    return this.chain == Chain.Polkadot || this.chain == Chain.Tron
}
fun Coin.getNotNativeTicker(): String {
    return this.ticker.uppercase().removePrefix("X/")
}
fun Coin.isSecuredAsset(): Boolean {
    return SECURE_ASSETS_TICKERS.contains(ticker.uppercase()) && (isNativeToken || contractAddress== "${ticker.lowercase()}-${ticker.lowercase()}")
}