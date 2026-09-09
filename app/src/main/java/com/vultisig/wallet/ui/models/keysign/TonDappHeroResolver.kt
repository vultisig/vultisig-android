package com.vultisig.wallet.ui.models.keysign

import com.vultisig.wallet.data.api.chains.ton.TonApi
import com.vultisig.wallet.data.crypto.ton.TonMessageBodyDecoder
import com.vultisig.wallet.data.crypto.ton.TonMessageBodyIntent
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.ui.components.hero.HeroContent
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import timber.log.Timber
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
 * Also resolves the ticker and scale behind each jetton row's quantity ([resolveJettonRowCoins]).
 * Unlike the hero, those have to speak for a jetton the vault has never held, so they run the full
 * ladder for every jetton transfer rather than stopping at the first vault-held one.
 *
 * Extracted from `JoinKeysignViewModel.loadTonDappDisplay`; the job launching, cancellation, and
 * pushing the resolved hero into the UI state stay in the ViewModel — this use case owns the
 * suspend resolution work and delegates to the top-level `resolveTonSwapHero` /
 * `resolveTonJettonHero`.
 */
internal class TonDappHeroResolver @Inject constructor(private val tonApi: TonApi) {

    /**
     * Jetton wallet -> jetton master, memoized for the life of this resolver. The per-message rows
     * and the hero walk the same wallets, and the mapping is fixed on chain, so it is fetched once.
     */
    private val jettonMasters = ConcurrentHashMap<String, String>()

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
                    jettonMaster(wallet)?.let { master -> toUserFriendly(master) ?: master }
                }
                ?.let { HeroContent.Send(title = null, coin = it) }
    }

    /**
     * Ticker and scale for every jetton wallet the jetton transfers in [payload] target, keyed by
     * the message destination verbatim so [mapTonMessages] can look up each row by its own `to`.
     *
     * Feeds the per-message rows rather than the hero, so unlike [invoke] it does not stop at the
     * first resolvable transfer and does not require the jetton to be held in the vault — the row
     * has to state a quantity for a jetton the vault has never seen. A wallet that resolves to
     * nothing is simply absent from the map and its row falls back to raw base units.
     */
    suspend fun resolveJettonRowCoins(
        payload: KeysignPayload,
        vaultCoins: List<Coin>,
    ): Map<String, TonHeroCoin> {
        val messages = payload.signTon?.tonMessages?.filterNotNull().orEmpty()
        if (messages.isEmpty()) return emptyMap()
        return resolveJettonRowCoins(messages, vaultCoins) {
            TONAddressConverter.toUserFriendly(it, true, false)
        }
    }

    /** JNI-free core of [resolveJettonRowCoins]. */
    internal suspend fun resolveJettonRowCoins(
        messages: List<TonMessage>,
        vaultCoins: List<Coin>,
        toUserFriendly: (String) -> String?,
    ): Map<String, TonHeroCoin> =
        messages
            .filter {
                TonMessageBodyDecoder.decode(it.payload) is TonMessageBodyIntent.JettonTransfer
            }
            .mapNotNull { it.to.takeIf(String::isNotEmpty) }
            .distinct()
            .mapNotNull { wallet ->
                resolveRowCoinOrNull(wallet, vaultCoins, toUserFriendly)?.let { wallet to it }
            }
            .toMap()

    /**
     * [resolveTonCoinByWallet] for one row, tolerating a failed lookup. One unreachable jetton must
     * not cost the other rows their tickers, and the row still shows its raw quantity without one.
     */
    private suspend fun resolveRowCoinOrNull(
        wallet: String,
        vaultCoins: List<Coin>,
        toUserFriendly: (String) -> String?,
    ): TonHeroCoin? =
        try {
            resolveTonCoinByWallet(wallet, vaultCoins, toUserFriendly)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "TON jetton row metadata lookup failed for %s", wallet)
            null
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
        val master = jettonMaster(wallet) ?: return null
        return resolveTonCoinByMaster(master, vaultCoins, toUserFriendly)
    }

    /** [TonApi.getJettonMasterAddress] through [jettonMasters]. */
    private suspend fun jettonMaster(wallet: String): String? =
        jettonMasters[wallet]
            ?: tonApi.getJettonMasterAddress(wallet)?.also { jettonMasters[wallet] = it }

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
