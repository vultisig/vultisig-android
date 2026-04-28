package com.vultisig.wallet.data.securityscanner.blockaid

import com.vultisig.wallet.data.models.Chain
import java.math.BigInteger
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Translates a Blockaid simulation response into the minimal [BlockaidSimulationInfo] shape that
 * drives the dApp hero.
 *
 * Mirrors `parseBlockaidEvmSimulation` and `parseBlockaidSolanaSimulation` in
 * vultisig-windows/core/chain/security/blockaid/tx/simulation/api/core.ts and the iOS
 * `BlockaidSimulationParser`. Keeping the three platforms aligned is what makes the cached result
 * valid as the single source of truth.
 */
internal object BlockaidSimulationParser {

    /** Hard cap on `raw_value` length; see [parseRawAmount]. */
    private const val MAX_RAW_AMOUNT_LENGTH = 80

    /** Codepoint cap for a sanitised ticker — see [sanitisedTicker]. */
    private const val MAX_TICKER_CODEPOINTS = 12

    /** Hard cap on logo URL length to bound work passed to Coil. */
    private const val MAX_LOGO_URL_LENGTH = 2048

    /** SPL wrapped-SOL mint, used as a sentinel for native SOL balance changes. */
    private const val WRAPPED_SOL_MINT = "So11111111111111111111111111111111111111112"

    /** Some EVM responses use the zero address as a synonym for "native ETH". */
    private const val EVM_NATIVE_SENTINEL_ADDRESS = "0x0000000000000000000000000000000000000000"

    fun parseEvm(
        response: BlockaidEvmSimulationResponseJson,
        chain: Chain,
    ): BlockaidSimulationInfo? {
        val diffs =
            response.simulation?.accountSummary?.assetsDiffs.orEmpty().filter {
                // Blockaid sometimes returns diffs with neither side populated
                // (e.g. dust balance dust changes); they cannot drive the hero.
                !it.outgoing.isNullOrEmpty() || !it.incoming.isNullOrEmpty()
            }
        if (diffs.isEmpty()) return null

        return when (diffs.size) {
            1 -> parseEvmTransfer(diffs[0], chain)
            else -> parseEvmSwap(diffs, chain)
        }
    }

    fun parseSolana(response: BlockaidSolanaSimulationResponseJson): BlockaidSimulationInfo? {
        val all =
            response.result?.simulation?.accountSummary?.accountAssetsDiff.orEmpty().filter {
                it.outgoing != null || it.incoming != null
            }
        if (all.isEmpty()) return null

        // When Blockaid returns three diffs, one of them is the native SOL fee. Identify it by
        // SHAPE (outgoing-only native SOL) rather than by position: a token→SOL swap legitimately
        // emits two SOL diffs — the fee leg (outgoing-only) and the swap-receive leg
        // (incoming-only) — so dropping whichever native-SOL entry happens to come first would
        // silently lose the user's actual swap result. Both `asset.type == "SOL"` and
        // `assetType == "SOL"` are checked because Blockaid is inconsistent about which field
        // carries the marker.
        val relevant: List<BlockaidSolanaSimulationJson.AccountAssetDiff> =
            if (all.size == 3) {
                val solFeeIndex =
                    all.indexOfFirst {
                        val isNative = it.asset.type == "SOL" || it.assetType == "SOL"
                        val outgoingOnly =
                            it.outgoing?.rawValue?.toRawValueString() != null &&
                                it.incoming?.rawValue?.toRawValueString() == null
                        isNative && outgoingOnly
                    }
                if (solFeeIndex >= 0) all.filterIndexed { idx, _ -> idx != solFeeIndex } else all
            } else {
                all
            }

        return when (relevant.size) {
            0 -> null
            1 -> parseSolanaTransfer(relevant[0])
            else -> parseSolanaMulti(relevant)
        }
    }

    private fun parseEvmTransfer(
        diff: BlockaidEvmSimulationJson.AssetDiff,
        chain: Chain,
    ): BlockaidSimulationInfo? {
        val out = diff.outgoing?.firstOrNull() ?: return null
        val amount = out.rawValue?.let(::parseRawAmount) ?: return null
        val coin = buildEvmCoin(diff.asset, chain) ?: return null
        return BlockaidSimulationInfo.Transfer(fromCoin = coin, fromAmount = amount)
    }

    private fun parseEvmSwap(
        diffs: List<BlockaidEvmSimulationJson.AssetDiff>,
        chain: Chain,
    ): BlockaidSimulationInfo? {
        val outDiff =
            diffs.firstOrNull { it.outgoing?.firstOrNull()?.rawValue != null } ?: return null

        // Prefer an incoming-only diff (the user's terminal received asset on a multi-hop swap).
        // A diff that has BOTH `incoming` and `outgoing` is an intermediate router leg and must
        // not be selected as the user's "in" side, otherwise a 3-hop ETH→USDC→DAI swap would
        // display as ETH→USDC.
        val inDiff =
            diffs.firstOrNull {
                val hasIn = it.incoming?.firstOrNull()?.rawValue != null
                val hasOut = it.outgoing?.firstOrNull()?.rawValue != null
                hasIn && !hasOut && !it.asset.address.canonicalEqualsEvm(outDiff.asset.address)
            }
                ?: diffs.firstOrNull {
                    it.incoming?.firstOrNull()?.rawValue != null &&
                        !it.asset.address.canonicalEqualsEvm(outDiff.asset.address)
                }
                ?: diffs.firstOrNull { it.incoming?.firstOrNull()?.rawValue != null }
                ?: return null

        // Avoid emitting a swap when the only diffs are duplicate sides of the same asset — that
        // is a transfer with rounding noise, not a swap. Symbols are compared case-insensitively
        // because Blockaid has been observed returning "USDC" / "usdc" for bridged variants.
        val sameAsset =
            outDiff.asset.address.canonicalEqualsEvm(inDiff.asset.address) &&
                outDiff.asset.symbol.equals(inDiff.asset.symbol, ignoreCase = true)
        if (sameAsset) return null

        val outRaw = outDiff.outgoing?.firstOrNull()?.rawValue ?: return null
        val inRaw = inDiff.incoming?.firstOrNull()?.rawValue ?: return null
        val outAmount = parseRawAmount(outRaw) ?: return null
        val inAmount = parseRawAmount(inRaw) ?: return null
        val fromCoin = buildEvmCoin(outDiff.asset, chain) ?: return null
        val toCoin = buildEvmCoin(inDiff.asset, chain) ?: return null

        return BlockaidSimulationInfo.Swap(
            fromCoin = fromCoin,
            toCoin = toCoin,
            fromAmount = outAmount,
            toAmount = inAmount,
        )
    }

    private fun buildEvmCoin(
        asset: BlockaidEvmSimulationJson.Asset,
        chain: Chain,
    ): BlockaidSimulationCoin? {
        val symbol = asset.symbol?.sanitisedTicker() ?: return null
        val decimals = asset.decimals?.clampDecimals() ?: return null
        return BlockaidSimulationCoin(
            chain = chain,
            address = asset.address,
            ticker = symbol,
            logo = asset.logoUrl.sanitisedLogoUrl(),
            decimals = decimals,
        )
    }

    private fun parseSolanaTransfer(
        diff: BlockaidSolanaSimulationJson.AccountAssetDiff
    ): BlockaidSimulationInfo? {
        // Pure-incoming diffs are intentionally not represented in the hero — the hero models
        // outflows the user authorises. A "dApp will airdrop tokens to you" simulation falls back
        // to the upstream null/Title flow and the user reads the details row instead.
        val raw = diff.outgoing?.rawValue?.toRawValueString() ?: return null
        val amount = parseRawAmount(raw) ?: return null
        val coin = buildSolanaCoin(diff.asset) ?: return null
        return BlockaidSimulationInfo.Transfer(fromCoin = coin, fromAmount = amount)
    }

    private fun parseSolanaMulti(
        diffs: List<BlockaidSolanaSimulationJson.AccountAssetDiff>
    ): BlockaidSimulationInfo? {
        val outSources = diffs.filter { it.outgoing?.rawValue?.toRawValueString() != null }
        val inSources =
            diffs.filter {
                it.incoming?.rawValue?.toRawValueString() != null && !outSources.contains(it)
            }

        // Multi-recipient send: every diff is outgoing-only AND every entry references the same
        // asset (same address + same case-insensitive symbol). The hero must NOT silently take
        // only the first leg's amount — that under-represents what the user is actually signing.
        // Aggregate the legs into a single transfer.
        if (inSources.isEmpty() && outSources.size > 1) {
            val firstAsset = outSources.first().asset
            val sameAsset =
                outSources.all { diff ->
                    diff.asset.address == firstAsset.address &&
                        diff.asset.symbol.equals(firstAsset.symbol, ignoreCase = true)
                }
            if (!sameAsset) return null
            val total =
                outSources.fold(BigInteger.ZERO) { acc, diff ->
                    val raw = diff.outgoing?.rawValue?.toRawValueString()
                    val amount = raw?.let(::parseRawAmount) ?: return null
                    acc + amount
                }
            val coin = buildSolanaCoin(firstAsset) ?: return null
            return BlockaidSimulationInfo.Transfer(fromCoin = coin, fromAmount = total)
        }

        // Regular swap path: pick by field presence rather than position. Blockaid does not
        // contractually order diffs.
        val outSource = outSources.firstOrNull() ?: return null
        val outRaw = outSource.outgoing?.rawValue?.toRawValueString() ?: return null
        val outAmount = parseRawAmount(outRaw) ?: return null
        val fromCoin = buildSolanaCoin(outSource.asset) ?: return null

        val inSource = inSources.firstOrNull()
        val inRaw = inSource?.incoming?.rawValue?.toRawValueString()
        val inAmount = inRaw?.let(::parseRawAmount)
        val toCoin = inSource?.let { buildSolanaCoin(it.asset) }

        return if (inAmount != null && toCoin != null) {
            BlockaidSimulationInfo.Swap(
                fromCoin = fromCoin,
                toCoin = toCoin,
                fromAmount = outAmount,
                toAmount = inAmount,
            )
        } else {
            BlockaidSimulationInfo.Transfer(fromCoin = fromCoin, fromAmount = outAmount)
        }
    }

    private fun buildSolanaCoin(
        asset: BlockaidSolanaSimulationJson.Asset
    ): BlockaidSimulationCoin? {
        val isNative = asset.type == "SOL"
        val mint = if (isNative) WRAPPED_SOL_MINT else asset.address
        if (mint.isNullOrEmpty()) return null

        val decimals = asset.decimals?.clampDecimals() ?: return null

        val ticker =
            asset.symbol?.sanitisedTicker()
                ?: if (isNative) Chain.Solana.feeUnit else truncatedMint(mint)

        // Blockaid's per-request logo URLs (under cdn.blockaid.io) are not hot-linkable so the
        // AsyncImage placeholder would spin forever. Native SOL falls back to the chain's local
        // logo via empty string — the UI layer maps empty logo URLs to chain-native fallbacks.
        val logo = if (isNative) "" else asset.logo.sanitisedLogoUrl()

        return BlockaidSimulationCoin(
            chain = Chain.Solana,
            address = mint,
            ticker = ticker,
            logo = logo,
            decimals = decimals,
        )
    }

    private fun truncatedMint(mint: String): String {
        if (mint.length <= 8) return mint
        return "${mint.take(4)}…${mint.takeLast(4)}"
    }

    /**
     * Blockaid encodes EVM `raw_value` as a hex string (e.g. "0x75652c52418a6").
     * `BigInteger.parse(_, 10)` would return null for hex-prefixed strings, so decode the prefix
     * explicitly. Accepts decimal as a fallback because Solana sometimes returns decimal strings.
     *
     * Hard length cap on the trimmed input bounds the work BigInteger does at parse time — a
     * hostile or buggy response with a multi-megabyte `raw_value` would otherwise allocate a
     * proportional BigInteger on the UI thread. [MAX_RAW_AMOUNT_LENGTH] = 80 is well above any
     * legitimate u256 value (66 chars including `0x`) while still bounded.
     */
    internal fun parseRawAmount(raw: String): BigInteger? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_RAW_AMOUNT_LENGTH) return null
        return try {
            if (trimmed.startsWith("0x") || trimmed.startsWith("0X")) {
                BigInteger(trimmed.drop(2), 16)
            } else {
                BigInteger(trimmed)
            }
        } catch (_: NumberFormatException) {
            null
        }
    }

    /**
     * Treats an EVM asset address as the same when both sides resolve to native ETH — i.e. either
     * `null` or the all-zeros sentinel string. Non-native addresses are compared case-insensitively
     * because EIP-55 checksum casing is not semantic.
     */
    private fun String?.canonicalEqualsEvm(other: String?): Boolean {
        val a = this?.takeUnless { it.equals(EVM_NATIVE_SENTINEL_ADDRESS, ignoreCase = true) }
        val b = other?.takeUnless { it.equals(EVM_NATIVE_SENTINEL_ADDRESS, ignoreCase = true) }
        if (a == null && b == null) return true
        if (a == null || b == null) return false
        return a.equals(b, ignoreCase = true)
    }

    /**
     * Bounds Blockaid-supplied `decimals` to a sane range.
     *
     * Anchors the formatter against malformed wire data: a hostile or buggy response with
     * `"decimals": 999999` would otherwise propagate into amount formatting and produce a
     * meaningless display value. 36 covers every existing token (the largest commonly seen on
     * mainnet is 24); the zero floor handles a "negative decimals" malformation. Note that the
     * formatter at [BuildHeroContentUseCase.formatAmount] rounds beyond 18 fractional digits, so
     * tokens with more than 18 decimals can still display as "0" for sub-wei amounts — that
     * trade-off is preferable to allocating a million-digit BigDecimal on the UI thread.
     */
    internal fun Int.clampDecimals(): Int = coerceIn(minimumValue = 0, maximumValue = 36)

    /**
     * Limits ticker length and strips control characters so a hostile or MITM'd Blockaid response
     * cannot inject zero-width glyphs, bidirectional overrides or arbitrarily long strings into the
     * hero. 12 codepoints accommodates the longest legitimate tickers (e.g. "WETH", "USDT.e",
     * "stETH", chain-specific wrapped tokens) with headroom; trim removes leading/trailing
     * whitespace.
     *
     * Bidirectional override codepoints (`U+202A..202E`, `U+2066..2069`, etc.) are stripped because
     * they would let an attacker render a ticker that visually reads as a different token from the
     * bytes it carries (e.g. `"USDC"` reversed to `"CDSU"` on screen while bytes still match a fee
     * path). Zero-width spaces, joiners, and the BOM are stripped for the same reason — they're
     * invisible characters that change layout without changing semantic equality.
     *
     * Truncation operates on Unicode codepoints (not UTF-16 char units) so that a 4-byte
     * supplementary-plane character (e.g. an emoji) cannot be split mid-pair, leaving an orphaned
     * surrogate that breaks downstream rendering.
     */
    internal fun String.sanitisedTicker(): String? {
        val cleaned =
            buildString {
                    var i = 0
                    var codepointsKept = 0
                    while (
                        i < this@sanitisedTicker.length && codepointsKept < MAX_TICKER_CODEPOINTS
                    ) {
                        val cp = this@sanitisedTicker.codePointAt(i)
                        val width = Character.charCount(cp)
                        if (!isUnsafeCodePoint(cp)) {
                            appendCodePoint(cp)
                            codepointsKept++
                        }
                        i += width
                    }
                }
                .trim()
        return cleaned.ifEmpty { null }
    }

    /**
     * Asset logo URLs come from Blockaid responses (untrusted). Coil follows any scheme it supports
     * — `file://`, `content://`, redirects to `http://` — which means a hostile response could try
     * to coerce the image loader into reading local files or downgrading to cleartext. Only HTTPS
     * URLs survive; everything else falls back to empty string and the UI shows the chain-native
     * fallback.
     *
     * Additionally, the URL is rejected when it carries a userinfo segment
     * (`https://attacker.com@trustedhost/...`), which OkHttp would interpret as the userinfo being
     * `attacker.com` and the host being `trustedhost` — a classic phishing primitive that bypasses
     * scheme-only validation. The URL length is capped because Coil is fed the raw value directly.
     */
    internal fun String?.sanitisedLogoUrl(): String {
        val candidate = this ?: return ""
        if (candidate.length > MAX_LOGO_URL_LENGTH) return ""
        if (!candidate.startsWith("https://", ignoreCase = true)) return ""
        return try {
            val uri = java.net.URI(candidate)
            if (uri.userInfo != null || uri.host.isNullOrEmpty()) "" else candidate
        } catch (_: java.net.URISyntaxException) {
            ""
        }
    }

    private fun isUnsafeCodePoint(cp: Int): Boolean {
        // ISO control range covers 0x00..0x1F and 0x7F..0x9F.
        if (Character.isISOControl(cp)) return true
        // Zero-width and bidirectional formatting codepoints that change layout invisibly.
        return cp == 0x200B || // ZERO WIDTH SPACE
            cp == 0x200C || // ZERO WIDTH NON-JOINER
            cp == 0x200D || // ZERO WIDTH JOINER
            cp == 0xFEFF || // ZERO WIDTH NO-BREAK SPACE / BOM
            cp in 0x200E..0x200F || // LRM / RLM
            cp in 0x202A..0x202E || // LRE / RLE / PDF / LRO / RLO
            cp in 0x2066..0x2069 || // LRI / RLI / FSI / PDI
            cp == 0x061C // ARABIC LETTER MARK
    }
}

/** Normalises a [JsonElement] raw_value into a string the [BigInteger] parser accepts. */
private fun JsonElement.toRawValueString(): String? {
    val primitive = (this as? JsonPrimitive) ?: return null
    return primitive.contentOrNull
}
