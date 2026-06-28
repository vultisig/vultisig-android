package com.vultisig.wallet.ui.models.keysign

import com.vultisig.wallet.data.api.chains.ton.TonApi
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.ui.components.hero.HeroContent
import javax.inject.Inject
import vultisig.keysign.v1.TonMessage
import wallet.core.jni.TONAddressConverter

/**
 * Resolves the TON dApp hero for a TonConnect keysign request from the decoded message bodies.
 *
 * Blockaid doesn't cover TON, so this is the TON equivalent of the Blockaid hero: it surfaces the
 * "You're swapping X → Y" hero for a gated DEX swap, or the first vault-held jetton transfer's real
 * amount + ticker + logo in place of the misleading gas value. Best-effort — a network failure or
 * an unrecognised jetton resolves to `null` so the verify screen keeps its existing display.
 *
 * Extracted from `JoinKeysignViewModel.loadTonDappHero`; the job launching, cancellation, and
 * pushing the resolved hero into the UI state stay in the ViewModel — this use case owns the
 * suspend resolution work and delegates to the top-level `resolveTonSwapHero` /
 * `resolveTonJettonHero`.
 */
internal class TonDappHeroResolver @Inject constructor(private val tonApi: TonApi) {

    /**
     * Resolves the hero for [payload]'s TonConnect messages, looking tokens up against [vaultCoins]
     * first. Returns `null` when [payload] carries no TON messages or no hero resolves.
     */
    suspend operator fun invoke(payload: KeysignPayload, vaultCoins: List<Coin>): HeroContent? {
        val messages = payload.signTon?.tonMessages?.filterNotNull().orEmpty()
        if (messages.isEmpty()) return null
        return resolveHero(messages, vaultCoins) {
            TONAddressConverter.toUserFriendly(it, true, false)
        }
    }

    /**
     * JNI-free core of [invoke]: resolves the swap hero first, then the single-sided
     * jetton-transfer hero. [toUserFriendly] canonicalizes raw/URL-safe addresses (WalletCore in
     * production, a fake in tests).
     */
    internal suspend fun resolveHero(
        messages: List<TonMessage>,
        vaultCoins: List<Coin>,
        toUserFriendly: (String) -> String?,
    ): HeroContent? {
        // Prefer the "You're swapping" hero for a gated DEX swap; otherwise fall back to the
        // single-sided jetton-transfer hero. Both are best-effort.
        return resolveTonSwapHero(
            messages = messages,
            nativeTon =
                TonHeroCoin(
                    ticker = Coins.Ton.TON.ticker,
                    decimals = Coins.Ton.TON.decimal,
                    logo = Coins.Ton.TON.logo,
                ),
            toUserFriendly = toUserFriendly,
            resolveCoinByWallet = { wallet ->
                resolveTonCoinByWallet(wallet, vaultCoins, toUserFriendly)
            },
            resolveDedustOutputCoin = { pool ->
                resolveTonDedustOutputCoin(pool, vaultCoins, toUserFriendly)
            },
        )
            ?: resolveTonJettonHero(messages, vaultCoins) { wallet ->
                    tonApi.getJettonMasterAddress(wallet)?.let { master ->
                        toUserFriendly(master) ?: master
                    }
                }
                ?.let { HeroContent.Send(title = null, coin = it) }
    }

    /**
     * Resolve a jetton wallet to its display coin for a swap leg: vault-tracked tokens first
     * (richest metadata), then the on-chain jetton master. Returns `null` when the wallet maps to
     * no known token, so the swap hero degrades rather than mislabelling the asset.
     */
    private suspend fun resolveTonCoinByWallet(
        wallet: String,
        vaultCoins: List<Coin>,
        toUserFriendly: (String) -> String?,
    ): TonHeroCoin? {
        val master = tonApi.getJettonMasterAddress(wallet) ?: return null
        return resolveTonCoinByMaster(master, vaultCoins, toUserFriendly)
    }

    /**
     * Resolve a DeDust swap's output token. The swap addresses the liquidity **pool**, not the
     * output jetton wallet, so the output master is read from the pool's `get_assets`.
     */
    private suspend fun resolveTonDedustOutputCoin(
        poolAddress: String,
        vaultCoins: List<Coin>,
        toUserFriendly: (String) -> String?,
    ): TonHeroCoin? {
        val master = tonApi.getDedustPoolOutputMaster(poolAddress) ?: return null
        return resolveTonCoinByMaster(master, vaultCoins, toUserFriendly)
    }

    /**
     * Resolve a jetton master to its display coin: vault-tracked tokens first (richest metadata),
     * then the built-in [Coins] registry, then on-chain metadata. Returns `null` when nothing
     * resolves, so the swap hero degrades rather than mislabelling the asset. [masterAddress] may
     * be raw or user-friendly; it is canonicalized via [toUserFriendly] for comparison against the
     * friendly-form contract addresses the registry/vault store.
     */
    private suspend fun resolveTonCoinByMaster(
        masterAddress: String,
        vaultCoins: List<Coin>,
        toUserFriendly: (String) -> String?,
    ): TonHeroCoin? {
        val master = toUserFriendly(masterAddress) ?: masterAddress
        (vaultCoins.asSequence() + Coins.coins[Chain.Ton].orEmpty().asSequence())
            .firstOrNull {
                it.chain == Chain.Ton && !it.isNativeToken && it.contractAddress == master
            }
            ?.let {
                return TonHeroCoin(ticker = it.ticker, decimals = it.decimal, logo = it.logo)
            }
        return tonApi.getJettonMetadata(master)?.let {
            TonHeroCoin(ticker = it.ticker, decimals = it.decimals, logo = it.logo ?: "")
        }
    }
}
