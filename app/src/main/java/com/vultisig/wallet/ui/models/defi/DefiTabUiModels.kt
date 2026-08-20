package com.vultisig.wallet.ui.models.defi

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.models.ImageModel
import com.vultisig.wallet.data.models.coinType
import com.vultisig.wallet.data.utils.symbol
import com.vultisig.wallet.ui.screens.v2.defi.model.BondNodeState
import com.vultisig.wallet.ui.utils.UiText
import java.math.BigDecimal

/**
 * UI models for the Bonded / Staking / LP tabs, shared by every DeFi positions screen. They used to
 * live inside ThorchainDefiPositionsViewModel.kt, which made them look Thorchain-specific even
 * though the Maya screen renders the same tabs from the same types.
 *
 * Fiat fields are nullable on purpose. `null` means "we could not resolve a price", which the UI
 * renders as an explicit unavailable marker; a resolved price is always a formatted string, so a
 * genuine zero balance stays distinguishable from a pricing failure. Neither is ever hidden.
 */
/**
 * What the LP tab contributes to the header total, on the DeFi screens that sum several legs into
 * one figure.
 *
 * LP joins the total already converted to fiat rather than as a raw chain amount, so [Priced]
 * carries the currency it was priced in — the raw legs re-convert on every total, but a bare LP
 * magnitude would just be relabelled with whatever currency is active now, mixing two currencies
 * into one sum.
 *
 * [Unavailable] is a *reported* state, not a pending one. A pool whose fetch failed reads as zero
 * liquidity, so adding it in would understate the total with nothing on screen to say so; the
 * header shows the unavailable marker instead of a confident wrong number. `null` — the absence of
 * either — still means "this leg has not reported yet".
 */
internal sealed interface LpLegTotal {
    data class Priced(val fiatValue: FiatValue) : LpLegTotal

    data object Unavailable : LpLegTotal
}

internal data class BondedTabUiModel(
    val isLoading: Boolean = false,
    val totalBondedAmount: String = "0 ${Chain.ThorChain.coinType.symbol}",
    val totalBondedPrice: String? = null,
    val nodes: List<BondedNodeUiModel> = emptyList(),
)

internal data class StakingTabUiModel(val positions: List<StakePositionUiModel> = emptyList())

internal data class LpTabUiModel(
    val isLoading: Boolean = false,
    val positions: List<LpPositionUiModel> = emptyList(),
    /**
     * Half-finished symmetric adds, listed above the positions. They are deliberately not gated on
     * the pool being selected in the Manage-Positions dialog: a deposit the user cannot see is the
     * one that silently gets refunded.
     */
    val pendingDeposits: List<PendingLpDepositUiModel> = emptyList(),
    /**
     * Flips true once the pending scan settles, success or failure. An empty [pendingDeposits] is
     * only meaningful after that: before it, "no pending deposits" is indistinguishable from "not
     * asked yet", and the tab would flash its no-positions state at the one user who has to act.
     */
    val pendingDepositsLoaded: Boolean = false,
)

/**
 * A symmetric add-liquidity that THORChain is still holding because only one side has arrived.
 * Mirrors [com.vultisig.wallet.data.models.ThorChainPendingLpDeposit] for display.
 */
internal data class PendingLpDepositUiModel(
    val poolId: String,
    val icon: ImageModel,
    val chainLogo: Int? = null,
    /** Ticker of the side still missing — the one the user has to send to complete the add. */
    val awaitedTicker: String,
    /** What already arrived, e.g. `"2 RUNE"`. */
    val depositedAmount: String,
    /** Address the missing side must be sent from, shortened for display. */
    val pairedAddress: String?,
    /** Time left before THORChain refunds the deposit, or `null` when it could not be resolved. */
    val refundsIn: UiText?,
    /** False when the missing side is on a chain this vault has no account for. */
    val canComplete: Boolean = true,
)

internal data class LpPositionUiModel(
    val titleLp: String,
    val totalPriceLp: String? = null,
    // The unformatted value behind totalPriceLp. The header total sums LP alongside the bond and
    // stake legs, and re-parsing a localised currency string to get there would be lossy.
    val totalFiatValue: BigDecimal = BigDecimal.ZERO,
    val icon: ImageModel,
    val assetTicker: String,
    val apr: String?,
    val position: String,
    val positionKey: String = "",
    val canRemove: Boolean = true,
    val chainLogo: Int? = null,
)

internal data class StakePositionUiModel(
    val coin: Coin,
    val stakeAssetHeader: UiText,
    val stakeAmount: BigDecimal = BigDecimal.ZERO,
    val stakedAmountDisplay: String,
    val stakedFiatDisplay: String? = null,
    val apy: String?,
    val isLoading: Boolean = false,
    val supportsMint: Boolean = false,
    val canWithdraw: Boolean = false,
    val canTransfer: Boolean = false,
    val canStake: Boolean = true,
    val canUnstake: Boolean = false,
    val rewards: String? = null,
    val nextReward: String? = null,
    val nextPayout: String? = null,
    // Maya CACAO pool only: remaining time until the position becomes unstake-eligible.
    // Drives the "Unlocks in N days, H hours" hint next to the disabled Unstake button.
    val unstakeUnlocksInSeconds: Long? = null,
    // Maya CACAO pool only: true when the maturity RPC returned UNKNOWN so the staking tab can
    // surface "Couldn't verify position" instead of an unexplained disabled Unstake button.
    val isUnstakeMaturityUnknown: Boolean = false,
)

internal data class BondedNodeUiModel(
    val address: String,
    val fullAddress: String,
    val status: BondNodeState,
    val apy: String,
    val bondedAmount: String,
    val nextAward: String,
    val nextChurn: String,
)
