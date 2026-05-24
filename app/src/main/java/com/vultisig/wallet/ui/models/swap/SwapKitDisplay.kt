package com.vultisig.wallet.ui.models.swap

/**
 * Render a SwapKit sub-provider id (`CHAINFLIP`, `near_intents`, `flashnet`) into a display label
 * fragment used in `SwapKit (<sub-provider>)` UI strings. Replaces `_` / `-` with spaces and
 * title-cases each token so `near_intents` → `Near Intents`; brand names already in mixed/upper
 * case are left intact (`CHAINFLIP` → `Chainflip` — first letter only is canonicalised).
 *
 * Lives at app-module scope because both [SwapQuoteManager.fetchSwapKitQuote] (initiator path) and
 * [com.vultisig.wallet.ui.models.keysign.JoinKeysignViewModel] (peer-side verify row) display the
 * sub-provider, and both rely on the same canonicalisation for visual consistency.
 */
internal fun formatSwapKitSubProvider(raw: String): String =
    raw.split('_', '-').joinToString(" ") { token ->
        token.replaceFirstChar { ch -> if (ch.isLowerCase()) ch.titlecase() else ch.toString() }
    }
