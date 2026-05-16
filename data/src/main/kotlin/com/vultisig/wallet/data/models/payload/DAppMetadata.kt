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
     * True when every field is empty. Use at the construction boundary (proto mapper) to avoid
     * surfacing meaningless banners.
     */
    val isEmpty: Boolean
        get() = name.isEmpty() && url.isEmpty() && iconUrl.isEmpty()

    /**
     * Safe URL to feed into an image loader for [iconUrl]. Returns `null` for empty input or
     * non-https schemes so the banner falls back to the placeholder icon instead of fetching from
     * `file://`, `content://`, `data:` etc. when a hostile dApp passes an odd URL.
     */
    val safeIconUrl: String?
        get() =
            iconUrl.takeIf {
                it.isNotEmpty() &&
                    runCatching { URI(it).scheme?.lowercase() == "https" }.getOrDefault(false)
            }

    private companion object {
        val HTTP_SCHEMES = setOf("http", "https")
    }
}
