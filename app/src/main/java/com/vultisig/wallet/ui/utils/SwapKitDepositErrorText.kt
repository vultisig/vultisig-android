package com.vultisig.wallet.ui.utils

import com.vultisig.wallet.R
import com.vultisig.wallet.data.securityscanner.SwapKitDepositRecipientException

/** The user-facing text of this refused SwapKit deposit recipient. */
val SwapKitDepositRecipientException.userText: UiText
    get() = UiText.StringResource(R.string.swap_error_swapkit_deposit_recipient_refused)

/** The user-facing text of a refused SwapKit deposit recipient, or null for any other failure. */
fun Throwable.swapKitDepositErrorTextOrNull(): UiText? =
    (this as? SwapKitDepositRecipientException)?.userText
