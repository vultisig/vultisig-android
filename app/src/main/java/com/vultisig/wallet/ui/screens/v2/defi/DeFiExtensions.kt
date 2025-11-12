package com.vultisig.wallet.ui.screens.v2.defi

import com.vultisig.wallet.data.blockchain.model.BondedNodePosition
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.coinType
import com.vultisig.wallet.data.models.getCoinLogo
import com.vultisig.wallet.data.utils.symbol
import com.vultisig.wallet.ui.models.defi.BondedNodeUiModel
import com.vultisig.wallet.ui.models.defi.BondedTabUiModel
import com.vultisig.wallet.ui.models.defi.StakingTabUiModel
import com.vultisig.wallet.ui.screens.v2.defi.model.BondNodeState.Companion.fromApiStatus
import com.vultisig.wallet.ui.screens.v2.defi.model.PositionUiModelDialog
import wallet.core.jni.CoinType

internal fun defaultPositionsBondDialog(): List<PositionUiModelDialog> =
    supportsBonDeFi.toPositionDialogModels()

internal fun defaultPositionsStakingDialog(): List<PositionUiModelDialog> =
    supportStakingDeFi.toPositionDialogModels()

internal fun List<Coin>.toPositionDialogModels(): List<PositionUiModelDialog> =
    map { coin ->
        PositionUiModelDialog(
            logo = getCoinLogo(coin.logo),
            ticker = coin.ticker,
            isSelected = true,
        )
    }

internal val supportStakingDeFi: List<Coin>
    get() = listOf(
        Coins.ThorChain.RUJI,
        Coins.ThorChain.TCY,
        Coins.ThorChain.sTCY,
        Coins.ThorChain.yRUNE,
        Coins.ThorChain.yTCY,
    )

internal val supportsBonDeFi: List<Coin>
    get() = listOf(
        Coins.ThorChain.RUNE,
    )

internal fun defaultSelectedPositionsDialog(): List<String> = 
    (supportsBonDeFi + supportStakingDeFi).map { it.ticker }

internal fun List<String>.hasBondPositions(): Boolean = 
    any { ticker -> supportsBonDeFi.any { it.ticker == ticker } }

internal fun List<String>.hasStakingPositions(): Boolean = 
    any { ticker -> supportStakingDeFi.any { it.ticker == ticker } }

internal fun emptyBondedTabUiModel() = BondedTabUiModel(
    isLoading = false,
    totalBondedAmount = "0 ${Chain.ThorChain.coinType.symbol}",
    nodes = emptyList()
)

internal fun emptyStakingTabUiModel() = StakingTabUiModel(
    positions = emptyList()
)

internal fun BondedNodePosition.toUiModel(): BondedNodeUiModel {
    return BondedNodeUiModel(
        address = node.address.formatAddress(),
        fullAddress = node.address,
        status = node.state.fromApiStatus(),
        apy = apy.formatPercentage(),
        bondedAmount = amount.formatAmount(CoinType.THORCHAIN),
        nextAward = nextReward.formatRuneReward(),
        nextChurn = nextChurn.formatDate(),
    )
}

enum class DeFiNavActions(val type: String){
    BOND("bond"), UNBOND("unbond"), WITHDRAW_RUJI("withdraw"),
    STAKE_RUJI("stake_ruji"), UNSTAKE_RUJI("unstake_ruji"),
    STAKE_TCY("stake_tcy"), UNSTAKE_TCY("unstake_tcy"), MINT_YRUNE("mint_yrune"),
    REDEEM_YRUNE("redeem_yrune"), MINT_YTCY("mint_ytcy"), REDEEM_YTCY("redeem_ytcy"),
}