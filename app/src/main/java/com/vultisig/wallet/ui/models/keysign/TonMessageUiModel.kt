package com.vultisig.wallet.ui.models.keysign

import androidx.compose.runtime.Immutable

/** Operation surfaced by decoding a TonConnect message body. */
internal enum class TonMessageOperation {
    JettonTransfer,
    NftTransfer,
    ExcessGasRefund,
    Transfer,
}

/**
 * Display model for a single TonConnect message on the keysign verify screen. Built by decoding the
 * message's BOC body; [recipient] is already in user-friendly form and [amount] is pre-formatted
 * (e.g. `"0.05 TON"`).
 */
@Immutable
internal data class TonMessageUiModel(
    val operation: TonMessageOperation,
    val recipient: String?,
    val amount: String?,
    val rawPayload: String?,
    val hasStateInit: Boolean,
)
