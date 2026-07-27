package com.vultisig.wallet.ui.models.defi

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
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
 */
internal data class BondedTabUiModel(
    val isLoading: Boolean = false,
    val totalBondedAmount: String = "0 ${Chain.ThorChain.coinType.symbol}",
    val totalBondedPrice: String = "",
    val nodes: List<BondedNodeUiModel> = emptyList(),
)

internal data class StakingTabUiModel(val positions: List<StakePositionUiModel> = emptyList())

internal data class LpTabUiModel(
    val isLoading: Boolean = false,
    val positions: List<LpPositionUiModel> = emptyList(),
)

internal data class LpPositionUiModel(
    val titleLp: String,
    val totalPriceLp: String,
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
    val stakedFiatDisplay: String = "",
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
