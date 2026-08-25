package com.vultisig.wallet.ui.screens.v2.defi

import com.vultisig.wallet.data.blockchain.model.BondedNodePosition
import com.vultisig.wallet.data.blockchain.thorchain.ThorchainStakingContracts
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
import com.vultisig.wallet.ui.screens.v2.defi.model.DeFiNavActions
import com.vultisig.wallet.ui.screens.v2.defi.model.PositionUiModelDialog
import wallet.core.jni.CoinType

internal fun defaultPositionsBondDialog(): List<PositionUiModelDialog> =
    thorchainSupportsBonDeFi.toPositionDialogModels()

internal fun defaultPositionsStakingDialog(): List<PositionUiModelDialog> =
    thorchainSupportStakingDeFi.toPositionDialogModels()

internal fun List<Coin>.toPositionDialogModels(): List<PositionUiModelDialog> = map { coin ->
    PositionUiModelDialog(logo = getCoinLogo(coin.logo), ticker = coin.ticker, isSelected = true)
}

val defiSupportedChains: List<Chain>
    get() = listOf(Chain.ThorChain, Chain.MayaChain, Chain.Ethereum)

internal val thorchainSupportStakingDeFi: List<Coin>
    get() =
        listOf(
            Coins.ThorChain.RUJI,
            Coins.ThorChain.TCY,
            Coins.ThorChain.sTCY,
            Coins.ThorChain.yRUNE,
            Coins.ThorChain.yTCY,
            Coins.ThorChain.ybRUNE,
        )

internal val thorchainSupportsBonDeFi: List<Coin>
    get() = listOf(Coins.ThorChain.RUNE)

internal val mayachainSupportsBondDeFi: List<Coin>
    get() = listOf(Coins.MayaChain.CACAO)

internal val mayachainSupportStakingDeFi: List<Coin>
    get() = listOf(Coins.MayaChain.CACAO)

internal const val MAYA_BOND_CACAO_KEY = "maya:bond:CACAO"
internal const val MAYA_STAKE_CACAO_KEY = "maya:stake:CACAO"

internal enum class DeFiProviders {
    CIRCLE
}

internal fun defaultSelectedPositionsDialog(): List<String> =
    (thorchainSupportsBonDeFi + thorchainSupportStakingDeFi).map { it.ticker }

internal fun List<String>.hasBondPositions(): Boolean = any { key ->
    thorchainSupportsBonDeFi.any { it.ticker == key } || key == MAYA_BOND_CACAO_KEY
}

internal fun List<String>.hasStakingPositions(): Boolean = any { key ->
    thorchainSupportStakingDeFi.any { it.ticker == key } || key == MAYA_STAKE_CACAO_KEY
}

internal fun List<String>.hasMayaStakingPositions(): Boolean = any { key ->
    key == MAYA_STAKE_CACAO_KEY
}

internal fun List<String>.hasLpPositions(lpPositionsDialog: List<PositionUiModelDialog>): Boolean =
    any { key ->
        lpPositionsDialog.any { it.positionKey == key }
    }

/**
 * The Bonded tab with nothing bonded. [totalBondedPrice] is passed in already formatted because the
 * zero has to render in the user's currency — leaving it unset showed no fiat line at all.
 */
internal fun emptyBondedTabUiModel(totalBondedPrice: String? = null) =
    BondedTabUiModel(
        isLoading = false,
        totalBondedAmount = "0 ${Chain.ThorChain.coinType.symbol}",
        totalBondedPrice = totalBondedPrice,
        nodes = emptyList(),
    )

internal fun emptyStakingTabUiModel() = StakingTabUiModel(positions = emptyList())

internal fun DeFiNavActions.getContractByDeFiAction(): String? {
    return when (this) {
        // Both RUJI positions live on the same rujira-staking contract; they differ only in the
        // execute message (`account.*` vs `liquid.*`).
        DeFiNavActions.WITHDRAW_RUJI,
        DeFiNavActions.STAKE_RUJI,
        DeFiNavActions.UNSTAKE_RUJI,
        DeFiNavActions.STAKE_SRUJI,
        DeFiNavActions.UNSTAKE_SRUJI -> STAKING_RUJI_CONTRACT

        DeFiNavActions.MINT_YTCY,
        DeFiNavActions.MINT_YRUNE -> YRUNE_YTCY_AFFILIATE_CONTRACT

        DeFiNavActions.REDEEM_YTCY -> YTCY_CONTRACT
        DeFiNavActions.REDEEM_YRUNE -> YRUNE_CONTRACT

        DeFiNavActions.STAKE_TCY,
        DeFiNavActions.UNSTAKE_TCY,
        DeFiNavActions.STAKE_STCY,
        DeFiNavActions.UNSTAKE_STCY -> STAKING_TCY_COMPOUND_CONTRACT

        DeFiNavActions.STAKE_YBRUNE,
        DeFiNavActions.UNSTAKE_YBRUNE -> STAKING_BRUNE_CONTRACT

        else -> null
    }
}

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

internal const val STAKING_RUJI_CONTRACT =
    "thor13g83nn5ef4qzqeafp0508dnvkvm0zqr3sj7eefcn5umu65gqluusrml5cr"

internal const val STAKING_TCY_COMPOUND_CONTRACT =
    "thor1z7ejlk5wk2pxh9nfwjzkkdnrq4p2f5rjcpudltv0gh282dwfz6nq9g2cr0"

// Rujira's liquid-bond contract for bRUNE: `liquid.bond` mints the ybRUNE receipt, `liquid.unbond`
// redeems it. Aliased rather than spelled out, because the pricing repository reads the same
// address for the contract's NAV — see [ThorchainStakingContracts].
internal const val STAKING_BRUNE_CONTRACT = ThorchainStakingContracts.BRUNE_LIQUID_BOND

internal const val YRUNE_CONTRACT =
    "thor1mlphkryw5g54yfkrp6xpqzlpv4f8wh6hyw27yyg4z2els8a9gxpqhfhekt"

internal const val YTCY_CONTRACT = "thor1h0hr0rm3dawkedh44hlrmgvya6plsryehcr46yda2vj0wfwgq5xqrs86px"

internal const val YRUNE_YTCY_AFFILIATE_CONTRACT =
    "thor1v3f7h384r8hw6r3dtcgfq6d5fq842u6cjzeuu8nr0cp93j7zfxyquyrfl8"
