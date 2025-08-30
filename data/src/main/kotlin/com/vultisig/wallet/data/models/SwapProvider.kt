package com.vultisig.wallet.data.models

enum class SwapProvider {
    JUPITER,
    KYBER,
    LIFI,
    MAYA,
    ONEINCH,
    THORCHAIN,
}

fun SwapProvider.getSwapProviderId(): String {
    return when(this) {
        SwapProvider.JUPITER -> "Jupiter"
        SwapProvider.KYBER -> "KyberSwap"
        SwapProvider.LIFI -> "LI.FI"
        SwapProvider.MAYA -> "Maya"
        SwapProvider.ONEINCH -> "1Inch"
        SwapProvider.THORCHAIN -> "Thorchain"
    }
}