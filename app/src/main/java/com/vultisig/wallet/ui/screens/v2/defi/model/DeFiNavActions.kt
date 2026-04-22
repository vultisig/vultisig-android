package com.vultisig.wallet.ui.screens.v2.defi.model

import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import timber.log.Timber

internal enum class DeFiNavActions(val type: String) {
    BOND("bond"),
    UNBOND("unbond"),
    WITHDRAW_RUJI("withdraw"),
    STAKE_RUJI("stake_ruji"),
    UNSTAKE_RUJI("unstake_ruji"),
    STAKE_TCY("stake_tcy"),
    UNSTAKE_TCY("unstake_tcy"),
    MINT_YRUNE("mint_yrune"),
    REDEEM_YRUNE("redeem_yrune"),
    MINT_YTCY("mint_ytcy"),
    REDEEM_YTCY("redeem_ytcy"),
    STAKE_STCY("stake_stcy"),
    UNSTAKE_STCY("unstake_stcy"),
    DEPOSIT_USDC_CIRCLE("deposit_usdc_circle"),
    WITHDRAW_USDC_CIRCLE("withdraw_usdc_circle"),
    STAKE_CACAO("stake_cacao"),
    UNSTAKE_CACAO("unstake_cacao"),
    ADD_LP("add_lp"),
    REMOVE_LP("remove_lp"),
    FREEZE_TRX("freeze_trx"),
    UNFREEZE_TRX("unfreeze_trx"),
}

internal fun parseDepositType(type: String?): DeFiNavActions? {
    return when (type?.lowercase()?.replace("_", "")?.replace("-", "")) {
        "bond" -> DeFiNavActions.BOND
        "unbond" -> DeFiNavActions.UNBOND
        "stakeruji",
        "stake_ruji" -> DeFiNavActions.STAKE_RUJI
        "unstakeruji",
        "unstake_ruji" -> DeFiNavActions.UNSTAKE_RUJI
        "withdrawruji",
        "withdraw_ruji",
        "withdraw" -> DeFiNavActions.WITHDRAW_RUJI
        "staketcy",
        "stake_tcy" -> DeFiNavActions.STAKE_TCY
        "unstaketcy",
        "unstake_tcy" -> DeFiNavActions.UNSTAKE_TCY
        "mintyrune",
        "mint_yrune",
        "receiveyrune",
        "receive_yrune" -> DeFiNavActions.MINT_YRUNE
        "redeemyrune",
        "redeem_yrune",
        "sellyrune",
        "sell_yrune" -> DeFiNavActions.REDEEM_YRUNE
        "mintytcy",
        "mint_ytcy",
        "receiveytcy",
        "receive_ytcy" -> DeFiNavActions.MINT_YTCY
        "redeemytcy",
        "redeem_ytcy",
        "sellytcy",
        "sell_ytcy" -> DeFiNavActions.REDEEM_YTCY
        "stake_stcy",
        "stakestcy" -> DeFiNavActions.STAKE_STCY
        "unstakestcy",
        "unstake_stcy" -> DeFiNavActions.UNSTAKE_STCY
        "deposit_usdc_circle" -> DeFiNavActions.DEPOSIT_USDC_CIRCLE
        "withdraw_usdc_circle" -> DeFiNavActions.WITHDRAW_USDC_CIRCLE
        "stakecacao",
        "stake_cacao" -> DeFiNavActions.STAKE_CACAO
        "unstakecacao",
        "unstake_cacao" -> DeFiNavActions.UNSTAKE_CACAO
        "addlp",
        "add_lp" -> DeFiNavActions.ADD_LP
        "removelp",
        "remove_lp" -> DeFiNavActions.REMOVE_LP
        "freezetrx",
        "freeze_trx" -> DeFiNavActions.FREEZE_TRX
        "unfreezetrx",
        "unfreeze_trx" -> DeFiNavActions.UNFREEZE_TRX
        else -> {
            try {
                type?.let { DeFiNavActions.valueOf(it.uppercase()) }
            } catch (e: IllegalArgumentException) {
                Timber.w("Unknown deposit type: $type")
                null
            }
        }
    }
}

internal fun Coin.getStakeDeFiNavAction(): DeFiNavActions {
    return when (this) {
        Coins.ThorChain.RUJI -> DeFiNavActions.STAKE_RUJI
        Coins.ThorChain.TCY -> DeFiNavActions.STAKE_TCY
        Coins.ThorChain.yRUNE -> DeFiNavActions.MINT_YRUNE
        Coins.ThorChain.yTCY -> DeFiNavActions.MINT_YTCY
        Coins.ThorChain.sTCY -> DeFiNavActions.STAKE_STCY
        Coins.MayaChain.CACAO -> DeFiNavActions.STAKE_CACAO
        else -> error("Not supported ${this.coinType.name}")
    }
}

internal fun Coin.getUnstakeDeFiNavAction(): DeFiNavActions {
    return when (this) {
        Coins.ThorChain.RUJI -> DeFiNavActions.UNSTAKE_RUJI
        Coins.ThorChain.TCY -> DeFiNavActions.UNSTAKE_TCY
        Coins.ThorChain.yRUNE -> DeFiNavActions.REDEEM_YRUNE
        Coins.ThorChain.yTCY -> DeFiNavActions.REDEEM_YTCY
        Coins.ThorChain.sTCY -> DeFiNavActions.UNSTAKE_STCY
        Coins.MayaChain.CACAO -> DeFiNavActions.UNSTAKE_CACAO
        else -> error("Not supported ${this.coinType.name}")
    }
}
