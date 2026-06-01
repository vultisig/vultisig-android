package com.vultisig.wallet.data.api.models.quotes

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One element of SwapKit's TON `tx` array (`POST /v3/swap` with `meta.txType == "TON"`). [amount]
 * is raw nano-TON (decimal string, 1e9 = 1 TON), never human-readable TON units. SwapKit emits a
 * single-element array today; the list shape is preserved so a future multi-output route decodes
 * cleanly. Field order matches iOS' `SwapKitTonTransfer` so the canonical JSON re-encoding is
 * byte-identical across platforms.
 */
@Serializable
data class SwapKitTonTransfer(
    @SerialName("address") val address: String,
    @SerialName("amount") val amount: String,
)
