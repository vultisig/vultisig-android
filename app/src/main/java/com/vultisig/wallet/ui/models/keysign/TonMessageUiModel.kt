package com.vultisig.wallet.ui.models.keysign

import androidx.compose.runtime.Immutable

/** Operation surfaced by decoding a TonConnect message body. */
internal enum class TonMessageOperation {
    Swap,
    JettonTransfer,
    NftTransfer,
    ExcessGasRefund,
    Transfer,
}

/**
 * Display model for a single TonConnect message on the keysign verify screen. Built by decoding the
 * message's BOC body; [recipient] is already in user-friendly form and both amounts are
 * pre-formatted.
 *
 * [amount] is the native value the message carries — for a jetton or NFT transfer that is only the
 * forwarded gas, not what the user is parting with. [tokenAmount] carries the transferred token's
 * own quantity (e.g. `"100 USDT"`), so the quantity is on screen whether or not the jetton is held
 * in the vault and can resolve a hero.
 */
@Immutable
internal data class TonMessageUiModel(
    val operation: TonMessageOperation,
    val recipient: String?,
    val amount: String?,
    val tokenAmount: String? = null,
    val rawPayload: String?,
    val hasStateInit: Boolean,
)
