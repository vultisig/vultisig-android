package com.vultisig.wallet.ui.screens.v2.defi

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.usecases.ActiveBondedNode
import com.vultisig.wallet.data.utils.getCoinBy
import com.vultisig.wallet.ui.models.defi.BondedNodeUiModel
import com.vultisig.wallet.ui.screens.v2.defi.model.BondNodeState.Companion.fromApiStatus
import wallet.core.jni.CoinType

internal val supportDeFiCoins: List<Coin>
    get() = listOf(
        Coins.getCoinBy(Chain.ThorChain, "ruji")!!,
        Coins.getCoinBy(Chain.ThorChain, "yrune")!!,
        Coins.getCoinBy(Chain.ThorChain, "tcy")!!,
        Coins.getCoinBy(Chain.ThorChain, "ytcy")!!,
        Coins.getCoinBy(Chain.ThorChain, "stcy")!!
    )

internal fun ActiveBondedNode.toUiModel(): BondedNodeUiModel {
    return BondedNodeUiModel(
        address = node.address.formatAddress(),
        status = node.state.fromApiStatus(),
        apy = apy.formatPercetange(),
        bondedAmount = amount.formatAmount(CoinType.THORCHAIN),
        nextAward = nextReward.formatRuneReward(),
        nextChurn = nextChurn.formatDate(),
    )
}