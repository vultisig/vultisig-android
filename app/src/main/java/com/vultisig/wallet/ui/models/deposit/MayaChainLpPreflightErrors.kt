package com.vultisig.wallet.ui.models.deposit

import com.vultisig.wallet.R
import com.vultisig.wallet.data.usecases.MayaChainLpPreflightBlock
import com.vultisig.wallet.ui.models.send.InvalidTransactionDataException
import com.vultisig.wallet.ui.utils.UiText

internal fun MayaChainLpPreflightBlock.toError(): InvalidTransactionDataException =
    when (this) {
        is MayaChainLpPreflightBlock.LpPaused ->
            InvalidTransactionDataException(
                UiText.FormattedText(R.string.deposit_error_maya_lp_paused_pool, listOf(pool))
            )
        is MayaChainLpPreflightBlock.ChainHalted ->
            InvalidTransactionDataException(
                UiText.FormattedText(R.string.deposit_error_maya_lp_halted_chain, listOf(chainPrefix))
            )
        is MayaChainLpPreflightBlock.PoolNotAvailable ->
            InvalidTransactionDataException(
                UiText.FormattedText(R.string.deposit_error_pool_not_available, listOf(pool))
            )
        is MayaChainLpPreflightBlock.StagedPoolRequiresPairedAdd ->
            InvalidTransactionDataException(
                UiText.FormattedText(R.string.deposit_error_maya_pool_staged_unpaired, listOf(pool))
            )
    }
