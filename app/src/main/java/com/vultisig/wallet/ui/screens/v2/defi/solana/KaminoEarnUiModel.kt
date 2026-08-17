package com.vultisig.wallet.ui.screens.v2.defi.solana

import androidx.compose.runtime.Immutable
import com.vultisig.wallet.data.blockchain.solana.kamino.KaminoRiskTier
import com.vultisig.wallet.ui.screens.v2.defi.DefiFiatTotal
import java.math.BigDecimal

/** State of the Kamino Earn segment of the Solana DeFi tab. */
@Immutable
data class KaminoEarnUiModel(
    val isLoading: Boolean = false,
    /**
     * Whether the user has switched any vault on. Distinct from [rows] being empty: an enabled
     * vault the user has never deposited into still gets a card showing a zero balance.
     */
    val hasEnabledVaults: Boolean = false,
    val rows: List<KaminoEarnRow> = emptyList(),
    /** Summed across vaults in the user's currency; null while unresolved. */
    val totalFiat: String? = null,
    /**
     * The same sum before formatting, so the chain header can add it to native staking without
     * re-parsing [totalFiat]. Zero when no vault is enabled — that is the user holding nothing
     * here, a known value — and null whenever any enabled vault is unread or unpriced, because a
     * sum that silently omits one of them is not this wallet's Kamino total.
     */
    val totalValue: DefiFiatTotal? = null,
    val isBalanceVisible: Boolean = true,
    /** Whether the last load stopped on an error, so the tab can say so rather than look empty. */
    val loadFailed: Boolean = false,
    val isShowingPicker: Boolean = false,
    /**
     * The selection being edited in the picker, which only becomes the saved one on Done — so
     * dismissing the sheet leaves the enabled vaults untouched.
     */
    val pendingSelection: Set<String> = emptySet(),
)

/** One curated vault's card. */
@Immutable
data class KaminoEarnRow(
    val vaultAddress: String,
    /** Live vault name when the API answered, otherwise the pinned fallback. */
    val name: String,
    val curator: String,
    val riskTier: KaminoRiskTier,
    val tokenLogo: String,
    val tokenTicker: String,
    /**
     * Deposited amount with its ticker. Always present — a position of zero is a real value, so
     * this is never placeheld the way [apyDisplay] and [pnlDisplay] are.
     */
    val depositedDisplay: String,
    val depositedFiat: String?,
    /** 30-day APY, already a percentage. Null while the metrics call is outstanding or failed. */
    val apyDisplay: String?,
    /** Lifetime profit in token terms. Null while the PnL call is outstanding or failed. */
    val pnlDisplay: String?,
    val pnlDirection: PnlDirection,
    /**
     * The row's fiat value, kept alongside its formatted form so the screen total can be summed
     * without re-parsing display strings. Null when the position or its price could not be read — a
     * zero there would drop the vault out of every total that adds these up, which reads as a
     * deposit having shrunk rather than as a figure that is briefly unknown.
     */
    val fiatValue: BigDecimal? = null,
    /**
     * Whether the wallet actually holds shares here. Kept separate from [fiatValue] because a
     * failed price lookup leaves that unresolved, and a position must not disappear because it
     * could not be priced.
     */
    val hasPosition: Boolean = false,
) {
    /**
     * Which way the position moved, kept out of the view so the zero case is decided once.
     *
     * Zero is deliberately its own state rather than folded into positive: every vault the user has
     * never deposited into sits at zero, and painting that green reads as a gain.
     */
    enum class PnlDirection {
        UP,
        DOWN,
        FLAT,
    }
}
