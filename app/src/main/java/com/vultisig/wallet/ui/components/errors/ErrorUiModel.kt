package com.vultisig.wallet.ui.components.errors

import com.vultisig.wallet.ui.utils.UiText

/**
 * UI model for the shared full-screen [ErrorView].
 *
 * @param title human-readable error name (never a raw trace).
 * @param description plain-language explanation plus the action the user should take.
 * @param errorState selects the hero icon variant — [ErrorState.CRITICAL] (red ✕, hard failure) or
 *   [ErrorState.WARNING] (amber ⚠, recoverable / precondition error).
 * @param rawError the underlying technical error text. When non-null, the screen shows a "Show
 *   exact error" disclosure that opens a sheet with this text, copyable and reportable.
 */
data class ErrorUiModel(
    val title: UiText,
    val description: UiText,
    val errorState: ErrorState = ErrorState.WARNING,
    val rawError: String? = null,
)
