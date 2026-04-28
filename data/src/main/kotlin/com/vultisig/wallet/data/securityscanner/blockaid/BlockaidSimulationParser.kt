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

    /** SPL wrapped-SOL mint, used as a sentinel for native SOL balance changes. */
    internal const val WRAPPED_SOL_MINT = "So11111111111111111111111111111111111111112"

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

        // When Blockaid returns three diffs, one of them is the native SOL
        // fee. Identify it by SHAPE (outgoing-only native SOL) rather than by
        // position: a token→SOL swap legitimately emits two SOL diffs — the
        // fee leg (outgoing only) and the swap-receive leg (incoming only) —
        // so dropping whichever native-SOL entry happens to come first would
        // silently lose the user's actual swap result.
        // Both `asset.type == "SOL"` and `assetType == "SOL"` are checked
        // because Blockaid is inconsistent about which field carries the
        // marker.
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
            else -> parseSolanaSwap(relevant)
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

        val inDiff =
            diffs.firstOrNull {
                it.incoming?.firstOrNull()?.rawValue != null &&
                    !it.asset.address.equalsIgnoreCase(outDiff.asset.address)
            } ?: diffs.firstOrNull { it.incoming?.firstOrNull()?.rawValue != null } ?: return null

        // Avoid emitting a swap when the only diffs are duplicate sides of the
        // same asset — that is a transfer with rounding noise, not a swap.
        val sameAsset =
            outDiff.asset.address.equalsIgnoreCase(inDiff.asset.address) &&
                outDiff.asset.symbol == inDiff.asset.symbol
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
        val raw = diff.outgoing?.rawValue?.toRawValueString() ?: return null
        val amount = parseRawAmount(raw) ?: return null
        val coin = buildSolanaCoin(diff.asset) ?: return null
        return BlockaidSimulationInfo.Transfer(fromCoin = coin, fromAmount = amount)
    }

    private fun parseSolanaSwap(
        diffs: List<BlockaidSolanaSimulationJson.AccountAssetDiff>
    ): BlockaidSimulationInfo? {
        // Pick by field presence rather than by position. Blockaid does not
        // contractually order diffs; the [parseEvmSwap] path also keys off
        // `outgoing/incoming` rather than index, so this keeps the two
        // platforms symmetric and robust to ordering changes upstream.
        val outSource = diffs.firstOrNull { it.outgoing?.rawValue?.toRawValueString() != null }
        val outRaw = outSource?.outgoing?.rawValue?.toRawValueString() ?: return null
        val outAmount = parseRawAmount(outRaw) ?: return null
        val fromCoin = buildSolanaCoin(outSource.asset) ?: return null

        val inSource =
            diffs.firstOrNull {
                it !== outSource && it.incoming?.rawValue?.toRawValueString() != null
            }
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

        // Blockaid's per-request logo URLs (under cdn.blockaid.io) are not
        // hot-linkable so the AsyncImage placeholder would spin forever. Native
        // SOL falls back to the chain's local logo via empty string — the UI
        // layer maps empty logo URLs to chain-native fallbacks.
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

    private const val MAX_RAW_AMOUNT_LENGTH = 80

    private fun String?.equalsIgnoreCase(other: String?): Boolean {
        if (this == null && other == null) return true
        if (this == null || other == null) return false
        return this.equals(other, ignoreCase = true)
    }

    /**
     * Bounds Blockaid-supplied `decimals` to a sane range.
     *
     * Blockaid is the trust anchor for the hero amount, but the wire payload is still untrusted
     * JSON: a hostile or buggy response with `"decimals": 999999` would cause
     * `BigDecimal.TEN.pow(999999)` in the formatter, allocating a million-digit BigDecimal on the
     * UI thread. 36 covers every existing token (max we've seen on-chain is 24); zero floor handles
     * a "negative decimals" malformation.
     */
    internal fun Int.clampDecimals(): Int = coerceIn(minimumValue = 0, maximumValue = 36)

    /**
     * Limits ticker length and strips control characters so a hostile or MITM'd Blockaid response
     * cannot inject zero-width glyphs, bidirectional overrides or arbitrarily long strings into the
     * hero. 12 chars accommodates the longest legitimate tickers (e.g. "WETH", "USDT.e", "stETH",
     * chain-specific wrapped tokens) with headroom; trim removes leading/trailing whitespace.
     *
     * Bidirectional override codepoints (`U+202A..202E`, `U+2066..2069`, etc.) are stripped because
     * they would let an attacker render a ticker that visually reads as a different token from the
     * bytes it carries (e.g. `"USDC"` reversed to `"CDSU"` on screen while bytes still match a fee
     * path). Zero-width spaces and the BOM are stripped for the same reason — they're invisible
     * characters that change layout without changing semantic equality.
     */
    internal fun String.sanitisedTicker(): String? {
        val cleaned =
            buildString {
                    this@sanitisedTicker.forEach { ch ->
                        if (!ch.isISOControl() && !ch.isUnsafeFormattingCodePoint()) {
                            append(ch)
                        }
                    }
                }
                .trim()
                .take(12)
        return cleaned.ifEmpty { null }
    }

    /**
     * Asset logo URLs come from Blockaid responses (untrusted). Coil follows any scheme it supports
     * — `file://`, `content://`, redirects to `http://` — which means a hostile response could try
     * to coerce the image loader into reading local files or downgrading to cleartext. Only HTTPS
     * URLs survive; everything else falls back to empty string and the UI shows the chain-native
     * fallback.
     */
    internal fun String?.sanitisedLogoUrl(): String =
        this?.takeIf { it.startsWith("https://", ignoreCase = true) }.orEmpty()

    private fun Char.isUnsafeFormattingCodePoint(): Boolean {
        val cp = code
        return cp == 0x200B || // ZERO WIDTH SPACE
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
