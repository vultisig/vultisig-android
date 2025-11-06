package com.vultisig.wallet.ui.screens.v2.defi

import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.usecases.ActiveBondedNode
import com.vultisig.wallet.ui.models.defi.BondedNodeUiModel
import com.vultisig.wallet.ui.screens.v2.defi.model.BondNodeState.Companion.fromApiStatus
import wallet.core.jni.CoinType

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

internal fun ActiveBondedNode.toUiModel(): BondedNodeUiModel {
    return BondedNodeUiModel(
        address = node.address.formatAddress(),
        status = node.state.fromApiStatus(),
        apy = apy.formatPercentage(),
        bondedAmount = amount.formatAmount(CoinType.THORCHAIN),
        nextAward = nextReward.formatRuneReward(),
        nextChurn = nextChurn.formatDate(),
    )
}