package com.vultisig.wallet.data.models

enum class SwapProvider {
    JUPITER,
    KYBER,
    LIFI,
    MAYA,
    ONEINCH,
    SWAPKIT,
    THORCHAIN,
}

fun SwapProvider.getSwapProviderId(): String {
    return when (this) {
        SwapProvider.JUPITER -> "Jupiter"
        SwapProvider.KYBER -> "KyberSwap"
        SwapProvider.LIFI -> "LI.FI"
        SwapProvider.MAYA -> "MayaChain"
        SwapProvider.ONEINCH -> "1Inch"
        SwapProvider.SWAPKIT -> "SwapKit"
        SwapProvider.THORCHAIN -> "THORChain"
    }
}

fun swapProviderFromWireId(wireId: String): SwapProvider? =
    when (wireId.lowercase().trim()) {
        "thorchain" -> SwapProvider.THORCHAIN
        "maya",
        "mayachain" -> SwapProvider.MAYA
        "li.fi" -> SwapProvider.LIFI
        "1inch" -> SwapProvider.ONEINCH
        "kyber",
        "kyberswap" -> SwapProvider.KYBER
        "jupiter" -> SwapProvider.JUPITER
        "swapkit" -> SwapProvider.SWAPKIT
        else -> null
    }
