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

/**
 * Canonical id carried in `OneInchSwapPayload.provider` on the wire. Lowercase, matching what iOS
 * (`SwapProviderId.rawValue`) and the SDK/extension (`generalSwapProviders`) emit — and what the
 * extension's co-signer guard enforces with an exact, case-sensitive match against a closed set.
 * [getSwapProviderId] is the display id and must never reach the wire: a peer receiving `"SwapKit"`
 * refuses to sign it as an unrecognized provider.
 */
fun SwapProvider.getWireId(): String =
    when (this) {
        SwapProvider.JUPITER -> "jupiter"
        SwapProvider.KYBER -> "kyber"
        SwapProvider.LIFI -> "li.fi"
        SwapProvider.MAYA -> "mayachain"
        SwapProvider.ONEINCH -> "1inch"
        SwapProvider.SWAPKIT -> "swapkit"
        SwapProvider.THORCHAIN -> "thorchain"
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
