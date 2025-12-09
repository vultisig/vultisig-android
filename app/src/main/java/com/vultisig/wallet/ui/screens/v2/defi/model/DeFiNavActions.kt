package com.vultisig.wallet.ui.screens.v2.defi.model

import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import timber.log.Timber

internal enum class DeFiNavActions(val type: String){
    BOND("bond"), UNBOND("unbond"), WITHDRAW_RUJI("withdraw"),
    STAKE_RUJI("stake_ruji"), UNSTAKE_RUJI("unstake_ruji"),
    STAKE_TCY("stake_tcy"), UNSTAKE_TCY("unstake_tcy"), MINT_YRUNE("mint_yrune"),
    REDEEM_YRUNE("redeem_yrune"), MINT_YTCY("mint_ytcy"), REDEEM_YTCY("redeem_ytcy"),
    DEPOSIT_USDC_CIRCLE("deposit_usdc_circle"), WITHDRAW_USDC_CIRCLE("withdraw_usdc_circle")
}

internal fun parseDepositType(type: String?): DeFiNavActions? {
    return when (type?.lowercase()?.replace("_", "")?.replace("-", "")) {
        "bond" -> DeFiNavActions.BOND
        "unbond" -> DeFiNavActions.UNBOND
        "stakeruji", "stake_ruji" -> DeFiNavActions.STAKE_RUJI
        "unstakeruji", "unstake_ruji" -> DeFiNavActions.UNSTAKE_RUJI
        "withdrawruji", "withdraw_ruji", "withdraw" -> DeFiNavActions.WITHDRAW_RUJI
        "staketcy", "stake_tcy" -> DeFiNavActions.STAKE_TCY
        "unstaketcy", "unstake_tcy" -> DeFiNavActions.UNSTAKE_TCY
        "mintyrune", "mint_yrune", "receiveyrune", "receive_yrune" -> DeFiNavActions.MINT_YRUNE
        "redeemyrune", "redeem_yrune", "sellyrune", "sell_yrune" -> DeFiNavActions.REDEEM_YRUNE
        "mintytcy", "mint_ytcy", "receiveytcy", "receive_ytcy" -> DeFiNavActions.MINT_YTCY
        "redeemytcy", "redeem_ytcy", "sellytcy", "sell_ytcy" -> DeFiNavActions.REDEEM_YTCY
        "deposit_usdc_circle" -> DeFiNavActions.DEPOSIT_USDC_CIRCLE
        "withdraw_usdc_circle" -> DeFiNavActions.WITHDRAW_USDC_CIRCLE
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
        else -> error("Not supported ${this.coinType.name}")
    }
}

internal fun Coin.getUnstakeDeFiNavAction(): DeFiNavActions {
    return when (this) {
        Coins.ThorChain.RUJI -> DeFiNavActions.UNSTAKE_RUJI
        Coins.ThorChain.TCY -> DeFiNavActions.UNSTAKE_TCY
        Coins.ThorChain.yRUNE -> DeFiNavActions.REDEEM_YRUNE
        Coins.ThorChain.yTCY -> DeFiNavActions.REDEEM_YTCY
        else -> error("Not supported ${this.coinType.name}")
    }
}