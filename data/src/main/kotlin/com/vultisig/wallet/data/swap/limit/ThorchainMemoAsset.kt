package com.vultisig.wallet.data.swap.limit

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.isSecuredAsset
import com.vultisig.wallet.data.models.swapAssetName

/**
 * Number of trailing contract-address characters a THORChain memo uses to disambiguate a token from
 * others sharing its ticker (`ETH.USDC-06EB48`).
 *
 * Deliberately NOT the full contract address: memo bytes are scarce (UTXO sources cap at 80 bytes),
 * and THORChain resolves the abbreviated form against its pool list.
 */
private const val CONTRACT_SUFFIX_LENGTH = 6

/**
 * THORChain memo-asset chain prefix (`BTC`, `ETH`, `THOR`, …) for every chain routable through
 * THORChain.
 *
 * Limit swaps (`=<`) share THORChain's regular-swap chain universe — `=` vs `=<` only selects
 * execution behavior (price/queue/TTL), not a different set of chains.
 *
 * This is a dedicated table rather than a reuse of [com.vultisig.wallet.data.models.swapAssetName]:
 * that helper maps [Chain.Noble] to `USDC` (Noble's fee ticker), whereas THORChain routes Noble
 * under the `NOBLE` prefix. The memo *is* the order, so the prefix must be THORChain's, not the fee
 * ticker's. Chains absent from this table (MayaChain, Cardano, Optimism, Polygon, …) are not
 * THORChain-routable and must fail before a memo is built.
 */
val thorchainMemoAssetChainPrefix: Map<Chain, String> =
    mapOf(
        Chain.Avalanche to "AVAX",
        Chain.Base to "BASE",
        Chain.BitcoinCash to "BCH",
        Chain.BscChain to "BSC",
        Chain.Bitcoin to "BTC",
        Chain.Dash to "DASH",
        Chain.Dogecoin to "DOGE",
        Chain.Ethereum to "ETH",
        Chain.GaiaChain to "GAIA",
        Chain.Litecoin to "LTC",
        Chain.ThorChain to "THOR",
        Chain.Tron to "TRON",
        Chain.Ripple to "XRP",
        Chain.Arbitrum to "ARB",
        Chain.Zcash to "ZEC",
        Chain.Solana to "SOL",
        Chain.Noble to "NOBLE",
    )

/**
 * Reverse of [thorchainMemoAssetChainPrefix]: THORChain asset prefix → [Chain].
 *
 * Derived by inversion rather than hand-maintained so the two directions cannot drift. Drift here
 * is a fund-safety bug: a prefix one direction accepts and the other rejects either blocks a valid
 * order or builds one that routes somewhere unintended.
 */
val thorchainAssetPrefixToChain: Map<String, Chain> =
    thorchainMemoAssetChainPrefix.entries.associate { (chain, prefix) -> prefix to chain }

/**
 * Whether a chain can be encoded as a THORChain memo asset — i.e. whether it is routable through
 * THORChain at all. Use this to filter coin pickers so a user cannot select a chain that would only
 * fail later, at memo-build time.
 */
fun isThorchainRoutable(chain: Chain): Boolean = thorchainMemoAssetChainPrefix.containsKey(chain)

/** Every chain the memo builder can encode, ignoring live network state. */
val staticLimitSwapSupportedChains: List<Chain> = thorchainMemoAssetChainPrefix.keys.toList()

/**
 * Build the THORChain memo-asset string for a coin — the `source_asset` / `target_asset` a
 * limit-swap memo is built from.
 * - native → `CHAIN.TICKER` (`BTC.BTC`, `THOR.RUNE`; Cosmos native is always `GAIA.ATOM`)
 * - THORChain tokens → `THOR.TICKER` (`THOR.TCY`)
 * - THORChain secured assets → the raw denom (`eth-usdc-0x…`)
 * - any other token → `CHAIN.TICKER-<last 6 of contract, uppercased>` (`ETH.USDC-06EB48`)
 *
 * Throws for chains THORChain cannot route, empty tickers, and contract segments too short to
 * abbreviate — a malformed asset segment must fail here rather than at broadcast time, once funds
 * are committed.
 */
fun Coin.thorchainMemoAsset(): String {
    val prefix =
        requireNotNull(thorchainMemoAssetChainPrefix[chain]) {
            "thorchainMemoAsset: $chain is not routable through THORChain"
        }

    val normalizedTicker = ticker.trim()
    require(normalizedTicker.isNotEmpty()) {
        "thorchainMemoAsset: ticker must be a non-empty string for $chain"
    }

    if (isNativeToken) {
        // Cosmos (GaiaChain) always routes as GAIA.ATOM regardless of the native coin's ticker,
        // matching the market-swap asset notation.
        return if (chain == Chain.GaiaChain) "$prefix.ATOM" else "$prefix.$normalizedTicker"
    }

    if (chain == Chain.ThorChain) {
        // Secured denoms identify the asset by their trailing address; leave them un-abbreviated.
        return if (isSecuredAsset()) contractAddress else "$prefix.$normalizedTicker"
    }

    val contract = contractAddress.trim()
    require(contract.length >= CONTRACT_SUFFIX_LENGTH) {
        "thorchainMemoAsset: contract segment \"$contract\" is shorter than " +
            "$CONTRACT_SUFFIX_LENGTH characters"
    }
    return "$prefix.$normalizedTicker-${contract.takeLast(CONTRACT_SUFFIX_LENGTH).uppercase()}"
}

/**
 * The same asset spelled the way a **cancel** memo must spell it: an EVM token carries its FULL
 * contract address rather than [thorchainMemoAsset]'s 6-character abbreviation.
 *
 * `ModifyLimitSwapMemo` (`m=<`) is the one inbound memo type THORChain does not run through
 * `fuzzyAssetMatch`, so the asset string is used verbatim to build the order's lookup key. A cancel
 * that repeats the placement memo's abbreviation is accepted by the chain, costs a fee, addresses a
 * bucket no order was ever indexed under, and cancels nothing — a failure indistinguishable from
 * success on the client. The abbreviation is not reversible, so the long form has to be captured at
 * placement time while the contract address is still in hand.
 *
 * Every other flavour spells identically to [thorchainMemoAsset]: a native leg (`BTC.BTC`,
 * `THOR.RUNE`) and a secured denom (`eth-usdc-0x…`) carry no truncated identifier to begin with.
 */
fun Coin.thorchainCancelMemoAsset(): String {
    if (isNativeToken || chain == Chain.ThorChain) return thorchainMemoAsset()

    val prefix =
        requireNotNull(thorchainMemoAssetChainPrefix[chain]) {
            "thorchainCancelMemoAsset: $chain is not routable through THORChain"
        }
    val normalizedTicker = ticker.trim()
    require(normalizedTicker.isNotEmpty()) {
        "thorchainCancelMemoAsset: ticker must be a non-empty string for $chain"
    }
    val contract = contractAddress.trim()
    require(contract.isNotEmpty()) {
        "thorchainCancelMemoAsset: $normalizedTicker on $chain has no contract address"
    }
    return "$prefix.$normalizedTicker-${contract.uppercase()}"
}

/** How the asset a memo names compares to a coin this app holds — see [compareToMemoAsset]. */
enum class MemoAssetMatch {
    /** The same asset, allowing for the spellings THORChain accepts as equivalent. */
    SAME,

    /** Confidently a different asset: another chain, another ticker, or another contract. */
    DIFFERENT,

    /** Not spelled in a form this app can resolve — a short code, or an unknown chain prefix. */
    UNREADABLE,
}

/**
 * Chain codes a memo asset may open with and this app can resolve. Both tables are included because
 * neither alone covers both protocols: [thorchainMemoAssetChainPrefix] carries THORChain's `NOBLE`,
 * [com.vultisig.wallet.data.models.swapAssetName] carries MayaChain's own `MAYA` and `DASH`.
 *
 * A prefix outside this set is not a mismatch — THORChain also accepts single-letter short codes
 * (`e.eth`), which resolve against its live pool list rather than any table an app can hold.
 */
private val memoAssetChainCodes: Set<String> =
    (thorchainAssetPrefixToChain.keys + Chain.entries.map { it.swapAssetName() }).mapTo(
        mutableSetOf()
    ) {
        it.uppercase()
    }

/** Chain / symbol / contract of an asset name, whichever notation it was written in. */
private data class MemoAssetParts(val chain: String, val symbol: String, val contract: String?)

/**
 * Separators THORChain puts between an asset's chain and the rest of its name: `.` for a layer 1,
 * `/` for a synth, `~` for a trade asset, `-` for a secured denom.
 */
private const val MEMO_ASSET_CHAIN_SEPARATORS = "./~-"

/**
 * Whether [memoAsset] — a target asset read out of a signed memo — names this coin.
 *
 * The comparison is deliberately one-sided: it exists to catch a memo that names something *other*
 * than the coin a screen is about to label with it, so anything it cannot resolve is [UNREADABLE]
 * rather than [DIFFERENT]. THORChain accepts several spellings of one asset — an abbreviated
 * contract tail (`ETH.USDC-06EB48`), a secured denom (`eth-usdc-0x…`), a synth or trade prefix, a
 * single-letter chain code — and treating an unfamiliar-but-valid one as a mismatch would hide a
 * guarantee the signer really has.
 */
fun Coin.compareToMemoAsset(memoAsset: String): MemoAssetMatch {
    val memo = parseMemoAssetParts(memoAsset) ?: return MemoAssetMatch.UNREADABLE
    if (memo.chain !in memoAssetChainCodes) return MemoAssetMatch.UNREADABLE
    val coin = memoAssetParts() ?: return MemoAssetMatch.UNREADABLE

    if (memo.chain != coin.chain || memo.symbol != coin.symbol) return MemoAssetMatch.DIFFERENT

    // Only a contract present on both sides can disagree: the memo may abbreviate it to its last
    // characters, and may drop it entirely for an asset THORChain can resolve by ticker alone.
    val memoContract = memo.contract
    val coinContract = coin.contract
    if (
        memoContract != null &&
            coinContract != null &&
            !memoContract.endsWith(coinContract) &&
            !coinContract.endsWith(memoContract)
    ) {
        return MemoAssetMatch.DIFFERENT
    }
    return MemoAssetMatch.SAME
}

private fun parseMemoAssetParts(asset: String): MemoAssetParts? {
    val trimmed = asset.trim()
    val separator = trimmed.indexOfFirst { it in MEMO_ASSET_CHAIN_SEPARATORS }
    if (separator <= 0 || separator == trimmed.lastIndex) return null

    val rest = trimmed.substring(separator + 1)
    val contractStart = rest.indexOf('-')
    val symbol = (if (contractStart < 0) rest else rest.substring(0, contractStart)).uppercase()
    if (symbol.isEmpty()) return null

    return MemoAssetParts(
        chain = trimmed.substring(0, separator).uppercase(),
        symbol = symbol,
        contract =
            if (contractStart < 0) null
            else rest.substring(contractStart + 1).uppercase().takeIf(String::isNotEmpty),
    )
}

/**
 * This coin written as the same three parts a memo asset parses into.
 *
 * A THORChain secured asset is read straight out of its denom, which already carries the underlying
 * chain and contract (`eth-usdc-0x…`) — its `THOR` chain would otherwise disagree with every memo
 * naming it. A THORChain token's `contractAddress` is a denom, not a contract, so it is left out of
 * the comparison entirely.
 */
private fun Coin.memoAssetParts(): MemoAssetParts? {
    if (isSecuredAsset()) return parseMemoAssetParts(contractAddress)

    val symbol =
        if (chain == Chain.GaiaChain && isNativeToken) "ATOM" else ticker.trim().uppercase()
    if (symbol.isEmpty()) return null

    return MemoAssetParts(
        chain = (thorchainMemoAssetChainPrefix[chain] ?: chain.swapAssetName()).uppercase(),
        symbol = symbol,
        contract =
            contractAddress
                .trim()
                .takeIf { it.isNotEmpty() && !isNativeToken && chain != Chain.ThorChain }
                ?.uppercase(),
    )
}
