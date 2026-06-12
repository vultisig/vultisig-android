package com.vultisig.wallet.ui.models.keysign

import java.math.BigInteger
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * ERC-20 approval functions that can grant unlimited token allowances. Mirrors iOS
 * `ContractCallExtractor.unlimitedApprovalFunctions` and the Windows companion list.
 */
private val UNLIMITED_APPROVAL_FUNCTIONS = setOf("approve", "permit", "permitsingle", "permitbatch")

/**
 * Any allowance at or above 2^96 tokens is treated as effectively unlimited. This threshold covers
 * MAX_UINT256, `type(uint160).max` (used by Permit2), and any other value that would exhaust a
 * realistic token supply many times over, while not firing on ordinary large-but-bounded amounts.
 */
private val UNLIMITED_APPROVAL_THRESHOLD: BigInteger = BigInteger.ONE.shiftLeft(96)

/**
 * Returns the index of the first `uint` or `uint256` parameter in [signature], or null if none.
 * Parsing the signature dynamically means this works for `approve(address,uint256)` (index 1) and
 * `permit(address,address,uint256,…)` (index 2) without hard-coding positions.
 */
internal fun firstUintParamIndex(signature: String): Int? {
    val params =
        signature
            .substringAfter('(', "")
            .substringBefore(')')
            .takeIf { it.isNotEmpty() }
            ?.split(',') ?: return null
    return params.indexOfFirst { it.trim().startsWith("uint") }.takeIf { it >= 0 }
}

/**
 * Returns the args-array index of the spender address for known approval call shapes, or null when
 * the spender cannot be read as a simple flat element (e.g., Permit2 tuples).
 * - `approve(address spender, uint256)` → 0
 * - `permit(address owner, address spender, uint256, …)` → 1
 * - `permitSingle` / `permitBatch` → null (spender is inside a `PermitDetails` tuple)
 */
internal fun approvalSpenderArgIndex(signature: String): Int? {
    return when (signature.replace(" ", "").lowercase(Locale.ROOT).substringBefore('(')) {
        "approve" -> 0
        "permit" -> 1
        else -> null
    }
}

/**
 * Returns true when the contract at [toAddress] is the ERC-20 token itself. For `approve` and
 * EIP-2612 `permit`, the call target is the token contract. For Permit2 (`permitSingle` /
 * `permitBatch`), the call target is the Permit2 router — not the token.
 */
internal fun isTokenContractApproval(signature: String): Boolean {
    val name = signature.replace(" ", "").lowercase(Locale.ROOT).substringBefore('(')
    return name == "approve" || name == "permit"
}

/**
 * Returns true when [signature] is an ERC-20 approval function (`approve`, `permit`,
 * `permitSingle`, or `permitBatch`) and the first uint parameter in [inputs] is at or above
 * [UNLIMITED_APPROVAL_THRESHOLD], indicating an effectively unbounded allowance.
 */
internal fun isUnlimitedApproval(signature: String, inputs: String?, json: Json): Boolean {
    val normalized = signature.replace(" ", "").lowercase(Locale.ROOT)
    if (normalized.substringBefore('(') !in UNLIMITED_APPROVAL_FUNCTIONS) return false
    val amountIndex = firstUintParamIndex(normalized) ?: return false
    val amount =
        runCatching {
                val rawAmount =
                    json
                        .parseToJsonElement(inputs ?: "[]")
                        .jsonArray
                        .getOrNull(amountIndex)
                        ?.jsonPrimitive
                        ?.content
                        ?.trim()
                when {
                    rawAmount.isNullOrEmpty() -> null
                    rawAmount.startsWith("0x", ignoreCase = true) ->
                        BigInteger(rawAmount.substring(2), 16)
                    else -> BigInteger(rawAmount)
                }
            }
            .onFailure { if (it is CancellationException) throw it }
            .getOrNull() ?: return false
    return amount >= UNLIMITED_APPROVAL_THRESHOLD
}

/** camelCase + acronym→word boundary, e.g. `supplyWithPermit` and `WBTCSwap`. */
private val EVM_FUNCTION_NAME_BOUNDARY = Regex("(?<=[a-z])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])")

/**
 * Codepoints that 4byte registry submissions must not smuggle into the signing UI: ISO controls,
 * bidi/zero-width formatting, variation selectors, interlinear annotations, and tag codepoints.
 *
 * 4byte.directory is open-submit and orders entries by `created_at` — an attacker can register a
 * crafted text for an unclaimed selector. Without this filter, an entry containing U+202E
 * (RIGHT-TO-LEFT OVERRIDE) or a tag-codepoint payload could surface as a misleading hero title.
 *
 * MUST stay aligned with `BlockaidSimulationParser.isUnsafeCodePoint` so the same hero sanitisation
 * applies whether the title comes from the 4byte fallback or a Blockaid response.
 */
private fun isUnsafeDisplayCodePoint(cp: Int): Boolean {
    if (Character.isISOControl(cp)) return true
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

/**
 * Strips bidi/zero-width/control codepoints from [input] so attacker-controlled text relayed via
 * the keysign payload (e.g. a crafted ticker carrying U+202E) cannot smuggle reordering or
 * invisible content into the signing UI. Mirrors `BlockaidSimulationParser.sanitisedTicker`.
 */
internal fun sanitizeDisplayString(input: String): String =
    buildString(input.length) {
        var i = 0
        while (i < input.length) {
            val cp = input.codePointAt(i)
            if (!isUnsafeDisplayCodePoint(cp)) appendCodePoint(cp)
            i += Character.charCount(cp)
        }
    }

/**
 * Extract a display-friendly function name from an ABI signature.
 * - `supplyWithPermit(...)` → `"Supply With Permit"`
 * - `WBTCSwap(...)` → `"WBTC Swap"`
 *
 * Digit-prefixed names like `ERC20transferFrom` are left as-is — splitting on digits produces
 * uglier output than it fixes for the long tail of selectors. Returns null when the signature has
 * no `(` or is empty before it.
 *
 * Iterates by codepoint, not [Char], so non-BMP unsafe codepoints (e.g. tag codepoints in plane 14)
 * are stripped rather than ignored.
 */
internal fun prettifyEvmFunctionName(signature: String): String? {
    val before = signature.substringBefore('(', missingDelimiterValue = "")
    val rawName =
        buildString(before.length) {
                var i = 0
                while (i < before.length) {
                    val cp = before.codePointAt(i)
                    if (!isUnsafeDisplayCodePoint(cp)) appendCodePoint(cp)
                    i += Character.charCount(cp)
                }
            }
            .trim()
    if (rawName.isEmpty()) return null
    return rawName.replace(EVM_FUNCTION_NAME_BOUNDARY, " ").split(' ').joinToString(" ") {
        it.replaceFirstChar { ch -> ch.titlecase(Locale.ROOT) }
    }
}
