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

    /**
     * Hard cap on input codepoints scanned by [sanitisedTicker]. Without this an adversarial input
     * stuffed with stripped codepoints (e.g. megabytes of zero-width characters) would still be
     * walked end-to-end on the UI thread. 128 is generous for legitimate input — the longest token
     * symbol seen in the wild is under 20 codepoints.
     */
    private const val MAX_TICKER_SCANNED_CODEPOINTS = 128

    /** Hard cap on logo URL length to bound work passed to Coil. */
    private const val MAX_LOGO_URL_LENGTH = 2048

    /** SPL wrapped-SOL mint, used as a sentinel for native SOL balance changes. */
    private const val WRAPPED_SOL_MINT = "So11111111111111111111111111111111111111112"

    /** Marker Blockaid uses on native-SOL diffs in either `asset.type` or `assetType`. */
    private const val SOLANA_NATIVE_ASSET_TYPE = "SOL"

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

    /**
     * Nets every Solana balance leg per mint before classifying. Native SOL and WSOL share the
     * wrapped-SOL mint, so a wrap-then-spend (or an unwrap that closes the temp account) collapses
     * into the single movement it really is instead of surfacing as a SOL/WSOL "swap". Nothing is
     * dropped as a presumed fee: neither position, diff count nor magnitude proves a native leg is
     * gas rather than principal, and a hero that hides or reverses a spend is worse than no hero.
     * Anything other than one net spend, or one net spend paired with one net receive, returns null
     * so the caller falls back to the generic title.
     */
    fun parseSolana(response: BlockaidSolanaSimulationResponseJson): BlockaidSimulationInfo? {
        val diffs = response.result?.simulation?.accountSummary?.accountAssetsDiff.orEmpty()
        if (diffs.isEmpty()) return null
        val balances = solanaMintBalances(diffs) ?: return null

        val outgoing = mutableListOf<SolanaNetChange>()
        val incoming = mutableListOf<SolanaNetChange>()
        for (balance in balances.values) {
            when {
                balance.outgoing > balance.incoming ->
                    outgoing +=
                        SolanaNetChange(
                            coin = balance.outgoingCoin ?: return null,
                            amount = balance.outgoing - balance.incoming,
                        )
                balance.incoming > balance.outgoing ->
                    incoming +=
                        SolanaNetChange(
                            coin = balance.incomingCoin ?: return null,
                            amount = balance.incoming - balance.outgoing,
                        )
            }
        }

        // Pure-incoming results are intentionally not represented in the hero — it models
        // outflows the user authorises. A "dApp will airdrop tokens to you" simulation falls back
        // to the upstream null/Title flow and the user reads the details row instead.
        return when {
            outgoing.size == 1 && incoming.isEmpty() ->
                BlockaidSimulationInfo.Transfer(
                    fromCoin = outgoing[0].coin,
                    fromAmount = outgoing[0].amount,
                )
            outgoing.size == 1 && incoming.size == 1 ->
                BlockaidSimulationInfo.Swap(
                    fromCoin = outgoing[0].coin,
                    toCoin = incoming[0].coin,
                    fromAmount = outgoing[0].amount,
                    toAmount = incoming[0].amount,
                )
            else -> null
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

    private class SolanaMintBalance(val decimals: Int) {
        var incoming: BigInteger = BigInteger.ZERO
        var outgoing: BigInteger = BigInteger.ZERO
        var incomingCoin: BlockaidSimulationCoin? = null
        var outgoingCoin: BlockaidSimulationCoin? = null
    }

    private class SolanaNetChange(val coin: BlockaidSimulationCoin, val amount: BigInteger)

    /**
     * Sums incoming and outgoing legs per resolved mint. Returns null on any leg that cannot be
     * trusted (unparsable amount, unresolvable asset, decimals disagreeing across rows of the same
     * mint) because a partial total would be presented as an authoritative one.
     *
     * When native SOL and WSOL rows land in the same bucket, the native row's metadata wins for
     * whichever direction it moves in, so the hero reads "SOL" with the chain logo rather than
     * whatever symbol Blockaid attached to the wrapped account.
     */
    private fun solanaMintBalances(
        diffs: List<BlockaidSolanaSimulationJson.AccountAssetDiff>
    ): Map<String, SolanaMintBalance>? {
        val balances = LinkedHashMap<String, SolanaMintBalance>()
        for (diff in diffs) {
            val incoming = parseSolanaLeg(diff.incoming) ?: return null
            val outgoing = parseSolanaLeg(diff.outgoing) ?: return null
            if (incoming.signum() == 0 && outgoing.signum() == 0) continue

            val coin = buildSolanaCoin(diff.asset, diff.assetType) ?: return null
            val mint = coin.address ?: return null
            val balance = balances.getOrPut(mint) { SolanaMintBalance(coin.decimals) }
            if (balance.decimals != coin.decimals) return null

            balance.incoming += incoming
            balance.outgoing += outgoing
            val isNative = diff.isNativeSol()
            if (incoming.signum() > 0 && (balance.incomingCoin == null || isNative)) {
                balance.incomingCoin = coin
            }
            if (outgoing.signum() > 0 && (balance.outgoingCoin == null || isNative)) {
                balance.outgoingCoin = coin
            }
        }
        return balances
    }

    /**
     * A missing leg is zero; a present leg must decode to a non-negative magnitude because
     * direction is carried by the `in`/`out` field itself, not by a sign.
     */
    private fun parseSolanaLeg(leg: BlockaidSolanaSimulationJson.BalanceChange?): BigInteger? {
        if (leg == null) return BigInteger.ZERO
        val raw = leg.rawValue?.toRawValueString() ?: return null
        return parseRawAmount(raw)
    }

    private fun buildSolanaCoin(
        asset: BlockaidSolanaSimulationJson.Asset,
        assetType: String?,
    ): BlockaidSimulationCoin? {
        // Blockaid sometimes marks native SOL via `asset.type == "SOL"`, sometimes via the sibling
        // `assetType == "SOL"` field on the diff. Without the second check, native-SOL transfers
        // surface with a null mint (the wire `asset.address` is absent) and the parser silently
        // drops them.
        val isNative =
            asset.type == SOLANA_NATIVE_ASSET_TYPE || assetType == SOLANA_NATIVE_ASSET_TYPE
        val mint = if (isNative) WRAPPED_SOL_MINT else asset.address
        if (mint.isNullOrEmpty()) return null

        val decimals = asset.decimals?.clampDecimals() ?: return null

        // Sanitize every fallback path: a hostile/MITM'd response could replace `asset.address`
        // with a string containing bidi/zero-width codepoints, which `truncatedMint` would then
        // forward into the hero ticker.
        val ticker =
            (asset.symbol ?: if (isNative) Chain.Solana.feeUnit else truncatedMint(mint))
                .sanitisedTicker() ?: return null

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

    private fun BlockaidSolanaSimulationJson.AccountAssetDiff.isNativeSol(): Boolean =
        asset.type == SOLANA_NATIVE_ASSET_TYPE || assetType == SOLANA_NATIVE_ASSET_TYPE

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
        // Reject signed inputs: direction is encoded by `outgoing` vs `incoming`, and `BigInteger`
        // happily parses `-1` / `+1` / hex with a leading sign. A negative amount in the hero
        // would misrepresent the call.
        if (trimmed.first() == '-' || trimmed.first() == '+') return null
        val parsed =
            try {
                if (trimmed.startsWith("0x", ignoreCase = true)) {
                    BigInteger(trimmed.drop(2), 16)
                } else {
                    BigInteger(trimmed)
                }
            } catch (_: NumberFormatException) {
                return null
            }
        return parsed.takeIf { it.signum() >= 0 }
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
                    var codepointsScanned = 0
                    while (
                        i < this@sanitisedTicker.length &&
                            codepointsKept < MAX_TICKER_CODEPOINTS &&
                            codepointsScanned < MAX_TICKER_SCANNED_CODEPOINTS
                    ) {
                        val cp = this@sanitisedTicker.codePointAt(i)
                        if (!isUnsafeCodePoint(cp)) {
                            appendCodePoint(cp)
                            codepointsKept++
                        }
                        i += Character.charCount(cp)
                        codepointsScanned++
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
        // Zero-width and bidirectional formatting codepoints that change layout invisibly,
        // plus three ranges that can swap or hide glyphs without showing in the kept length:
        //   - U+FE00..FE0F variation selectors swap a base glyph for an alt presentation
        //     (e.g. emoji vs text style); a hostile ticker could prefix a letter to render
        //     as a different visual.
        //   - U+E0000..E007F tag codepoints render as nothing on most platforms but stay
        //     in the string, allowing invisible content to ride along.
        //   - U+FFF9..FFFB interlinear annotation anchors/separators/terminators alter
        //     rendering in some text engines.
        return cp == 0x200B || // ZERO WIDTH SPACE
            cp == 0x200C || // ZERO WIDTH NON-JOINER
            cp == 0x200D || // ZERO WIDTH JOINER
            cp == 0x2060 || // WORD JOINER
            cp == 0xFEFF || // ZERO WIDTH NO-BREAK SPACE / BOM
            cp in 0x200E..0x200F || // LRM / RLM
            cp in 0x202A..0x202E || // LRE / RLE / PDF / LRO / RLO
            cp in 0x2066..0x2069 || // LRI / RLI / FSI / PDI
            cp == 0x061C || // ARABIC LETTER MARK
            cp in 0xFE00..0xFE0F || // VARIATION SELECTOR-1..16
            cp in 0xFFF9..0xFFFB || // INTERLINEAR ANNOTATION ANCHOR/SEPARATOR/TERMINATOR
            cp in 0xE0000..0xE007F // TAG codepoints (incl. LANGUAGE TAG)
    }
}

/** Normalises a [JsonElement] raw_value into a string the [BigInteger] parser accepts. */
private fun JsonElement.toRawValueString(): String? {
    val primitive = (this as? JsonPrimitive) ?: return null
    return primitive.contentOrNull
}
