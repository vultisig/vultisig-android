package com.vultisig.wallet.ui.models.deposit

import com.vultisig.wallet.R
import com.vultisig.wallet.data.usecases.ThorChainLpPreflightBlock
import com.vultisig.wallet.ui.models.send.InvalidTransactionDataException
import com.vultisig.wallet.ui.utils.UiText

internal fun ThorChainLpPreflightBlock.toError(): InvalidTransactionDataException =
    when (this) {
        is ThorChainLpPreflightBlock.LpPaused ->
            InvalidTransactionDataException(
                UiText.FormattedText(R.string.deposit_error_lp_paused_pool, listOf(pool))
            )
        is ThorChainLpPreflightBlock.ChainLpHalted ->
            InvalidTransactionDataException(
                UiText.FormattedText(R.string.deposit_error_lp_halted_chain, listOf(chainPrefix))
            )
        is ThorChainLpPreflightBlock.PoolNotAvailable ->
            InvalidTransactionDataException(
                UiText.FormattedText(R.string.deposit_error_pool_not_available, listOf(pool))
            )
    }
