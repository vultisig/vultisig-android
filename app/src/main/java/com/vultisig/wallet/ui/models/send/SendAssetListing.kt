package com.vultisig.wallet.ui.models.send

import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.matchesSearch
import java.math.BigDecimal

/** What the inline Send asset step lists: the visible accounts and the chain chips, in order. */
internal data class SendAssetListing(val rows: List<Account>, val chains: List<Chain>)

// The tickers iOS keeps out of Send as DeFi positions (`CoinMeta.defiOnlyTickers`).
private val DEFI_ONLY_TICKERS = setOf("STCY", "YBRUNE")

/**
 * Builds the asset step from the form's [accounts], which arrive in vault order, with the same
 * rules as the iOS asset step.
 *
 * Sendable chains are the vault's chains that hold their native coin, in the order those native
 * coins appear; rows are their coins minus DeFi-only positions. A non-blank [query] searches every
 * sendable chain and ignores [chainFilter]; otherwise [chainFilter] narrows the rows to one chain
 * (null means all). Rows run by fiat value, largest first, then by ticker, then by the iOS chain
 * order, with the [selectedTokenId] row pinned on top when it is visible. Chains run by the summed
 * fiat of their rows, largest first; ties keep vault order. A coin with no balance or price is
 * worth zero.
 */
internal fun buildSendAssetListing(
    accounts: List<Account>,
    chainFilter: Chain?,
    query: String,
    selectedTokenId: String?,
): SendAssetListing {
    val needle = query.trim()
    val sendableChains =
        accounts.filter { it.token.isNativeToken }.map { it.token.chain }.distinct()
    val holdings = sendableChains.associateWithTo(LinkedHashMap()) { BigDecimal.ZERO }
    val visible = mutableListOf<Pair<Account, BigDecimal>>()
    for (account in accounts) {
        val token = account.token
        val chainHolding = holdings[token.chain] ?: continue
        // A send opened on a DeFi receipt (the ybRUNE Transfer) keeps that receipt's row.
        val isSelected = token.id.equals(selectedTokenId, true)
        if (token.ticker.uppercase() in DEFI_ONLY_TICKERS && !isSelected) continue
        val fiat = account.fiatAmount
        holdings[token.chain] = chainHolding + fiat
        val isVisible =
            if (needle.isEmpty()) chainFilter == null || token.chain == chainFilter
            else token.matchesSearch(needle) || token.chain.matchesSearch(needle)
        if (isVisible) visible += account to fiat
    }
    val sorted =
        visible
            .sortedWith(
                compareByDescending<Pair<Account, BigDecimal>> { it.second }
                    .thenBy { it.first.token.ticker }
                    .thenBy { it.first.token.chain.iosIndex }
            )
            .map { it.first }
    val selectedIndex = sorted.indexOfFirst { it.token.id.equals(selectedTokenId, true) }
    val rows =
        if (selectedIndex > 0) {
            listOf(sorted[selectedIndex]) + sorted.filterIndexed { i, _ -> i != selectedIndex }
        } else {
            sorted
        }
    val chains = holdings.entries.sortedByDescending { it.value }.map { it.key }
    return SendAssetListing(rows = rows, chains = chains)
}

/**
 * Balance times unit price, unrounded like iOS; the stored, cent-truncated value without a price.
 */
private val Account.fiatAmount: BigDecimal
    get() {
        val balance = tokenValue?.decimal
        val unitPrice = price?.value
        return if (balance != null && unitPrice != null) balance.multiply(unitPrice)
        else fiatValue?.value ?: BigDecimal.ZERO
    }

/** The chain's position in iOS `Chain.allCases`, the last tie-break of the iOS asset list. */
private val Chain.iosIndex: Int
    get() =
        when (this) {
            Chain.ThorChain -> 0
            Chain.Solana -> 3
            Chain.Ethereum -> 4
            Chain.Avalanche -> 5
            Chain.Base -> 6
            Chain.Blast -> 7
            Chain.Arbitrum -> 8
            Chain.Polygon -> 9
            Chain.Optimism -> 11
            Chain.BscChain -> 12
            Chain.Bitcoin -> 13
            Chain.BitcoinCash -> 14
            Chain.Litecoin -> 15
            Chain.Dogecoin -> 16
            Chain.Dash -> 17
            Chain.Cardano -> 18
            Chain.GaiaChain -> 19
            Chain.MayaChain -> 21
            Chain.CronosChain -> 22
            Chain.Sui -> 23
            Chain.Polkadot -> 24
            Chain.ZkSync -> 25
            Chain.Dydx -> 26
            Chain.Ton -> 27
            Chain.Osmosis -> 28
            Chain.Terra -> 29
            Chain.TerraClassic -> 30
            Chain.Noble -> 31
            Chain.Ripple -> 32
            Chain.Akash -> 33
            Chain.Tron -> 34
            Chain.Zcash -> 36
            Chain.Mantle -> 37
            Chain.Hyperliquid -> 38
            Chain.Sei -> 39
            Chain.Robinhood -> 40
            Chain.Qbtc -> 41
            Chain.Bittensor -> 42
            Chain.Near -> 43
        }
