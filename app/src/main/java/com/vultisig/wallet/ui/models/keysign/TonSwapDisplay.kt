package com.vultisig.wallet.ui.models.keysign

import com.vultisig.wallet.data.crypto.ton.TonKnownRouters
import com.vultisig.wallet.data.crypto.ton.TonMessageBodyDecoder
import com.vultisig.wallet.data.crypto.ton.TonMessageBodyIntent
import com.vultisig.wallet.ui.components.hero.HeroCoinAmount
import com.vultisig.wallet.ui.components.hero.HeroContent
import java.math.BigDecimal
import java.math.BigInteger
import vultisig.keysign.v1.TonMessage

/**
 * Display-ready coin used to build a swap hero: ticker, scale, and logo URL ("" = native fallback).
 */
internal data class TonHeroCoin(val ticker: String, val decimals: Int, val logo: String)

/**
 * Resolve a "You're swapping X → Y" hero from a TonConnect request.
 *
 * The swap is classified purely from the signed bytes by [TonMessageBodyDecoder]; this layer
 * applies the anti-spoofing [TonKnownRouters] gate — normalizing both the allow-list and the
 * candidate address through [toUserFriendly] (WalletCore) so URL-safe / raw variants compare equal
 * — before trusting the swap. An ungated or unresolvable swap returns `null`, so the verify screen
 * falls back to its plain per-message display rather than showing an unverified swap card.
 * Best-effort, mirrors [resolveTonJettonHero]: the first trusted, fully-resolved swap wins.
 *
 * @param toUserFriendly raw-or-friendly address → canonical user-friendly bounceable form (or null)
 * @param resolveCoinByWallet jetton-wallet address → its token's display coin (vault or on-chain)
 * @param nativeTon the TON coin used for the native side of a TON↔jetton swap
 */
internal suspend fun resolveTonSwapHero(
    messages: List<TonMessage>,
    nativeTon: TonHeroCoin,
    toUserFriendly: (String) -> String?,
    resolveCoinByWallet: suspend (jettonWalletAddress: String) -> TonHeroCoin?,
    resolveDedustOutputCoin: suspend (poolAddress: String) -> TonHeroCoin?,
): HeroContent.Swap? {
    val routers = TonKnownRouters.stonfiV2Routers.normalizeWith(toUserFriendly)
    val ptonWallets = TonKnownRouters.stonfiV2PtonWallets.normalizeWith(toUserFriendly)
    val nativeVaults = TonKnownRouters.dedustNativeVaults.normalizeWith(toUserFriendly)

    for (message in messages) {
        val swap =
            TonMessageBodyDecoder.decode(message.payload) as? TonMessageBodyIntent.Swap ?: continue

        // Gate: bind the opcode-based classification to a known contract.
        val gated =
            when {
                swap.provider == TonMessageBodyIntent.Provider.STONFI &&
                    swap.offerAsset == TonMessageBodyIntent.OfferAsset.JETTON ->
                    swap.inputRouterAddress.isAllowed(routers, toUserFriendly)
                swap.provider == TonMessageBodyIntent.Provider.STONFI ->
                    message.to.isAllowed(ptonWallets, toUserFriendly)
                else -> message.to.isAllowed(nativeVaults, toUserFriendly)
            }
        if (!gated) continue

        val from =
            when (swap.offerAsset) {
                TonMessageBodyIntent.OfferAsset.TON -> nativeTon
                TonMessageBodyIntent.OfferAsset.JETTON ->
                    resolveCoinByWallet(message.to) ?: continue
            }
        // Output token. STON.fi's target IS the output jetton wallet (a known pTON wallet means the
        // swap pays out native TON). DeDust's target is the liquidity pool, so the output asset is
        // resolved from the pool instead. Unresolvable output → skip (don't guess the ticker).
        val target = swap.targetAddress ?: continue
        val to =
            when (swap.provider) {
                TonMessageBodyIntent.Provider.DEDUST -> resolveDedustOutputCoin(target) ?: continue
                TonMessageBodyIntent.Provider.STONFI -> {
                    val targetFriendly = toUserFriendly(target)
                    if (targetFriendly != null && targetFriendly in ptonWallets) nativeTon
                    else resolveCoinByWallet(target) ?: continue
                }
            }

        return HeroContent.Swap(
            title = null,
            from = from.toHero(swap.offerAmount),
            to =
                HeroCoinAmount(
                    amount = swap.minOut?.let { formatTokenAmount(it, to.decimals) } ?: "",
                    ticker = to.ticker,
                    logo = to.logo,
                ),
        )
    }
    return null
}

private fun TonHeroCoin.toHero(rawAmount: BigInteger): HeroCoinAmount =
    HeroCoinAmount(amount = formatTokenAmount(rawAmount, decimals), ticker = ticker, logo = logo)

private fun String?.isAllowed(
    normalizedSet: Set<String>,
    toUserFriendly: (String) -> String?,
): Boolean {
    val candidate = this?.let(toUserFriendly) ?: return false
    return candidate in normalizedSet
}

private fun Set<String>.normalizeWith(toUserFriendly: (String) -> String?): Set<String> =
    mapNotNullTo(HashSet()) { toUserFriendly(it) }

private fun formatTokenAmount(raw: BigInteger, decimals: Int): String =
    if (decimals <= 0) raw.toString()
    else BigDecimal(raw).movePointLeft(decimals).stripTrailingZeros().toPlainString()
