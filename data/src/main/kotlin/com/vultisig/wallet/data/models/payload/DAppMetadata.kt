package com.vultisig.wallet.data.models.payload

import androidx.compose.runtime.Immutable
import java.net.URI

/**
 * Identity of the dApp that produced a keysign request.
 *
 * Surfaced on the verify and done screens so signers can see which dApp originated the transaction.
 * Trust decisions stay with Blockaid and the independently-decoded calldata; this is informational
 * only.
 *
 * Mirrors the `DAppMetadata` proto on `KeysignPayload`. Proto strings are non-nullable, so empty
 * strings are treated as missing.
 */
@Immutable
data class DAppMetadata(val name: String, val url: String, val iconUrl: String) {

    /**
     * Hostname extracted from [url] when [url] is a well-formed http(s) URI. Returns `""` for empty
     * input, malformed input, or non-http(s) schemes (`javascript:`, `data:`, `file:`, …) so the UI
     * never echoes attacker-controlled text as if it were a hostname. The banner hides the host
     * line when this is empty.
     *
     * **Deliberate divergence from iOS/Windows:** both other platforms fall back to the raw [url]
     * string when host parsing fails. Android returns `""` in those cases, which is stricter — a
     * hostile dApp can't surface `javascript:alert(1)` as if it were a hostname — at the cost of
     * silently hiding legitimate-but-unparseable URIs (e.g. onion services that don't normalise
     * through [URI]). If that case turns out to matter, narrow the empty-return to non-http(s)
     * schemes only rather than re-introducing the unconditional fallback.
     */
    val host: String by lazy {
        runCatching {
                if (url.isEmpty()) return@runCatching ""
                val uri = URI(url)
                if (uri.scheme?.lowercase() in HTTP_SCHEMES) uri.host.orEmpty() else ""
            }
            .getOrDefault("")
    }

    /**
     * True when the banner has nothing meaningful to show. Use at the construction boundary (proto
     * mapper) to avoid surfacing identity-less banners.
     *
     * Only [name] and [url] count — [iconUrl] alone is not enough identity to render the banner (it
     * would be a circle with empty text), and gating on it would also let a hostile dApp force a
     * network fetch to an attacker-controlled origin via icon-only metadata.
     */
    val isEmpty: Boolean
        get() = name.isEmpty() && url.isEmpty()

    private companion object {
        val HTTP_SCHEMES = setOf("http", "https")
    }
}
