package com.vultisig.wallet.ui.models.keysign

import androidx.compose.runtime.Immutable
import com.vultisig.wallet.R
import com.vultisig.wallet.data.repositories.AbiParam
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.asUiText
import java.math.BigDecimal
import java.math.BigInteger
import java.util.Locale
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray

/**
 * A single labelled row produced by [decodedFunctionParams]. Renders inside the verify and
 * join-keysign expandable "Transaction Details" section in place of the raw JSON array of decoded
 * ABI arguments.
 * - [label] is the row's leading text (e.g. "Spender", "Recipient", or a positional fallback like
 *   `#3 (address)`).
 * - [value] is the trailing text. It uses [UiText] so localised values like "Unlimited USDC" can be
 *   composed lazily at render time inside `@Composable` code rather than baked in here.
 * - [copyableValue] is the unellipsised string that goes to the clipboard when the user taps the
 *   copy icon next to the row. Non-null for address-typed rows so the full 0x… stays accessible
 *   even though the rendered value is middle-ellipsised. Null suppresses the copy affordance.
 * - [secondary] supplements the trailing text — used for the friendly contract label that
 *   [com.vultisig.wallet.data.repositories.KnownEvmContracts] returns for known DEX routers.
 * - [isWarning] flips the row into the warning colour, mirroring the unlimited-approval banner.
 */
@Immutable
internal data class DecodedFunctionParam(
    val label: UiText,
    val value: UiText,
    val copyableValue: String? = null,
    val secondary: String? = null,
    val isWarning: Boolean = false,
)

/**
 * Parses a decoded EVM contract call into labelled rows for the verify-screen detail section.
 *
 * For well-known function shapes (ERC-20 approve/permit/transfer/transferFrom, ERC-721/1155
 * setApprovalForAll) the rows carry semantic labels (`Spender`, `Recipient`, etc.) so the user can
 * read the call at a glance. Unknown signatures fall back to positional `#N (type)` rows so
 * something useful still appears instead of opaque JSON.
 * - [signature] is the 4byte text signature like `approve(address,uint256)` (parameter names are
 *   not part of 4byte data, so positional types are all we get).
 * - [inputsJson] is the JSON array produced by
 *   [com.vultisig.wallet.data.repositories.FourByteRepositoryImpl.decodeFunctionArgs].
 * - [tokenSymbol] is the resolved ticker for an ERC-20 token contract, or null. Callers typically
 *   pass the approval token ticker for approve/permit so the amount row reads `Unlimited USDC`
 *   instead of bare numerals.
 * - [tokenDecimals] is the resolved decimals for [tokenSymbol]. When non-null and bounded, the raw
 *   `uint256` amount is shifted to human-readable units so `transfer(addr, 1_000_000)` on a
 *   6-decimal token renders as `"1 USDC"` rather than `"1000000 USDC"`. A null here keeps the raw
 *   integer in place to avoid silently misrepresenting an amount we cannot scale.
 * - [contractLabel] returns the human-readable label for a known EVM contract address (Uniswap V3
 *   Router, Permit2, etc.), or null when unknown.
 * - [isUnlimitedApproval] flips the amount row into the warning style and uses an `Unlimited`
 *   label, matching the existing unlimited-approval banner.
 * - [abiParams] are the parameter names recovered from the contract's verified ABI (Sourcify), used
 *   only by the generic fallback to label rows `tokenId` / `trait.name` instead of `#1` / `#3.1`.
 *   Null (the common case — unverified contract, no semantic handler attempted resolution) keeps
 *   the positional labelling. Names never reorder or reinterpret values; they are display-only.
 *
 * Returns null when [signature] or [inputsJson] is blank or unparseable — callers should hide the
 * rich-parameter UI in that case.
 */
internal fun decodedFunctionParams(
    signature: String?,
    inputsJson: String?,
    json: Json,
    tokenSymbol: String? = null,
    tokenDecimals: Int? = null,
    contractLabel: (String) -> String? = { null },
    isUnlimitedApproval: Boolean = false,
    abiParams: List<AbiParam>? = null,
): List<DecodedFunctionParam>? {
    if (signature.isNullOrBlank() || inputsJson.isNullOrBlank()) return null
    val parsed = parseFunctionSignature(signature) ?: return null
    val inputs = parseInputs(inputsJson, json) ?: return null

    val ctx =
        HandlerContext(
            inputs = inputs,
            tokenSymbol = tokenSymbol,
            tokenDecimals = tokenDecimals,
            contractLabel = contractLabel,
            isUnlimitedApproval = isUnlimitedApproval,
        )
    val functionKey = parsed.name.lowercase(Locale.ROOT)
    val handler = PARAM_HANDLERS[functionKey]
    if (handler != null && inputs.size == parsed.types.size && handler.matchesArity(inputs.size)) {
        handler
            .rows(ctx)
            ?.takeIf { it.isNotEmpty() }
            ?.let {
                return it
            }
    }
    return genericParams(parsed.types, inputs, abiParams, contractLabel)
}

/**
 * True when [signature] has a dedicated semantic handler that matches both its function name AND
 * its parameter arity (approve, transfer, …). Callers use this to skip the cost of a verified-ABI
 * name lookup for calls that already render with curated labels — only the generic positional
 * fallback benefits from recovered names. The arity gate matters: a same-name different-arity call
 * like `transfer(address,uint256,bytes)` is NOT handled by the 2-arg [transferRows] (which would
 * drop the trailing `bytes`), so it must keep the ABI lookup and fall through to the
 * name-recovering fallback.
 */
internal fun hasSemanticHandler(signature: String?): Boolean {
    if (signature.isNullOrBlank()) return false
    val parsed = parseFunctionSignature(signature) ?: return false
    val handler = PARAM_HANDLERS[parsed.name.lowercase(Locale.ROOT)] ?: return false
    return handler.matchesArity(parsed.types.size)
}

private data class ParsedSignature(val name: String, val types: List<String>)

private fun parseFunctionSignature(signature: String): ParsedSignature? {
    val openParen = signature.indexOf('(')
    val closeParen = signature.lastIndexOf(')')
    if (openParen <= 0 || closeParen <= openParen) return null
    val name = signature.substring(0, openParen).trim()
    if (name.isEmpty()) return null
    val paramString = signature.substring(openParen + 1, closeParen)
    return ParsedSignature(name = name, types = splitTopLevelParamTypes(paramString))
}

private fun splitTopLevelParamTypes(params: String): List<String> {
    if (params.isBlank()) return emptyList()
    val out = mutableListOf<String>()
    val buf = StringBuilder()
    var depth = 0
    for (ch in params) {
        when (ch) {
            '(' -> {
                depth++
                buf.append(ch)
            }
            ')' -> {
                depth--
                buf.append(ch)
            }
            ',' ->
                if (depth == 0) {
                    out += buf.toString().trim()
                    buf.clear()
                } else {
                    buf.append(ch)
                }
            else -> buf.append(ch)
        }
    }
    if (buf.isNotEmpty()) out += buf.toString().trim()
    return out
}

private fun parseInputs(inputsJson: String, json: Json): List<JsonElement>? =
    runCatching { json.parseToJsonElement(inputsJson).jsonArray.toList() }.getOrNull()

private data class HandlerContext(
    val inputs: List<JsonElement>,
    val tokenSymbol: String?,
    val tokenDecimals: Int?,
    val contractLabel: (String) -> String?,
    val isUnlimitedApproval: Boolean,
)

private typealias ParamHandler = (HandlerContext) -> List<DecodedFunctionParam>?

/**
 * A curated handler paired with the parameter arity it is designed for. Gating on arity (not just
 * the function name) keeps a same-name different-arity call — e.g. a 3-arg
 * `transfer(address,uint256,bytes)` whose trailing `bytes` the 2-arg [transferRows] would silently
 * drop — out of the curated path so it falls through to the generic, name-recovering fallback.
 */
private class SemanticHandler(val rows: ParamHandler, val matchesArity: (Int) -> Boolean)

private val PARAM_HANDLERS: Map<String, SemanticHandler> =
    mapOf(
        "approve" to SemanticHandler(::approveRows) { it == 2 },
        // ERC-2612 permit carries trailing v/r/s signature components beyond the four
        // user-meaningful fields [permitRows] renders, so it matches any arity of at least 3.
        "permit" to SemanticHandler(::permitRows) { it >= 3 },
        "transfer" to SemanticHandler(::transferRows) { it == 2 },
        "transferfrom" to SemanticHandler(::transferFromRows) { it == 3 },
        "setapprovalforall" to SemanticHandler(::setApprovalForAllRows) { it == 2 },
    )

private fun approveRows(ctx: HandlerContext): List<DecodedFunctionParam>? {
    val spender = ctx.inputs.flatString(0) ?: return null
    val amount = ctx.inputs.flatString(1).orEmpty()
    return listOf(
        addressRow(R.string.erc20_approval_spender.asUiText(), spender, ctx.contractLabel),
        amountRow(amount, ctx.tokenSymbol, ctx.tokenDecimals, ctx.isUnlimitedApproval),
    )
}

private fun permitRows(ctx: HandlerContext): List<DecodedFunctionParam>? {
    val owner = ctx.inputs.flatString(0) ?: return null
    val spender = ctx.inputs.flatString(1) ?: return null
    val value = ctx.inputs.flatString(2).orEmpty()
    val deadline = ctx.inputs.flatString(3)
    return buildList {
        add(addressRow(R.string.decoded_function_owner.asUiText(), owner, ctx.contractLabel))
        add(addressRow(R.string.erc20_approval_spender.asUiText(), spender, ctx.contractLabel))
        add(amountRow(value, ctx.tokenSymbol, ctx.tokenDecimals, ctx.isUnlimitedApproval))
        if (!deadline.isNullOrBlank()) {
            add(
                DecodedFunctionParam(
                    label = R.string.decoded_function_deadline.asUiText(),
                    value = UiText.DynamicString(deadline),
                )
            )
        }
    }
}

private fun transferRows(ctx: HandlerContext): List<DecodedFunctionParam>? {
    val recipient = ctx.inputs.flatString(0) ?: return null
    val amount = ctx.inputs.flatString(1).orEmpty()
    return listOf(
        addressRow(R.string.verify_transaction_to_title.asUiText(), recipient, ctx.contractLabel),
        amountRow(amount, ctx.tokenSymbol, ctx.tokenDecimals, isUnlimited = false),
    )
}

private fun transferFromRows(ctx: HandlerContext): List<DecodedFunctionParam>? {
    val from = ctx.inputs.flatString(0) ?: return null
    val to = ctx.inputs.flatString(1) ?: return null
    val amount = ctx.inputs.flatString(2).orEmpty()
    return listOf(
        addressRow(R.string.verify_transaction_from_title.asUiText(), from, ctx.contractLabel),
        addressRow(R.string.verify_transaction_to_title.asUiText(), to, ctx.contractLabel),
        amountRow(amount, ctx.tokenSymbol, ctx.tokenDecimals, isUnlimited = false),
    )
}

private fun setApprovalForAllRows(ctx: HandlerContext): List<DecodedFunctionParam>? {
    val operator = ctx.inputs.flatString(0) ?: return null
    val approved = ctx.inputs.flatString(1).orEmpty()
    val isApproved = approved.equals("true", ignoreCase = true)
    val statusRes =
        if (isApproved) R.string.decoded_function_status_approved
        else R.string.decoded_function_status_revoked
    return listOf(
        addressRow(R.string.decoded_function_operator.asUiText(), operator, ctx.contractLabel),
        DecodedFunctionParam(
            label = R.string.decoded_function_status.asUiText(),
            value = UiText.StringResource(statusRes),
            isWarning = isApproved,
        ),
    )
}

/**
 * Positional fallback for calls with no semantic handler. Walks the signature's type tree alongside
 * the decoded value tree and (when available) the verified-ABI name tree, emitting one flat row per
 * leaf:
 * - **Tuples expand.** Each inner field becomes its own row rather than collapsing the whole struct
 *   to one `...` line. Nested tuples recurse.
 * - **Names when known.** A leaf renders its dotted ABI name (`trait.value`) when [abiParams]
 *   resolved one for it and every ancestor; otherwise it falls back to the positional path with a
 *   type tag (`#3.2 (string)`).
 * - **Type-aware values.** `address` resolves through [contractLabel] and stays copyable; `bytes`
 *   stays full-hex-and-copyable so the middle-ellipsised row can still be copied in full; `bool`
 *   renders `true` / `false` (already normalised upstream).
 *
 * Depth and row count are capped so a hostile signature (the calldata is attacker-controlled) can't
 * force unbounded recursion or a runaway row list on the signing screen.
 */
private fun genericParams(
    types: List<String>,
    inputs: List<JsonElement>,
    abiParams: List<AbiParam>?,
    contractLabel: (String) -> String?,
): List<DecodedFunctionParam> {
    val out = mutableListOf<DecodedFunctionParam>()
    val count = maxOf(types.size, inputs.size)
    for (index in 0 until count) {
        if (out.size >= MAX_PARAM_ROWS) break
        val abi = abiParams?.getOrNull(index)
        expandParam(
            type = types.getOrNull(index)?.takeIf { it.isNotBlank() },
            element = inputs.getOrNull(index),
            abi = abi,
            positionalPath = "#${index + 1}",
            namePath = sanitizedName(abi),
            depth = 0,
            contractLabel = contractLabel,
            out = out,
        )
    }
    return out
}

private fun expandParam(
    type: String?,
    element: JsonElement?,
    abi: AbiParam?,
    positionalPath: String,
    namePath: String?,
    depth: Int,
    contractLabel: (String) -> String?,
    out: MutableList<DecodedFunctionParam>,
) {
    if (out.size >= MAX_PARAM_ROWS) return
    val normalized = type?.trim()
    if (depth < MAX_PARAM_DEPTH && normalized != null && isPlainTuple(normalized)) {
        val innerTypes = splitTopLevelParamTypes(normalized.substring(1, normalized.length - 1))
        val innerValues = (element as? JsonArray)?.toList().orEmpty()
        val innerAbis = abi?.components
        val childCount = maxOf(innerTypes.size, innerValues.size)
        for (j in 0 until childCount) {
            if (out.size >= MAX_PARAM_ROWS) return
            val childAbi = innerAbis?.getOrNull(j)
            expandParam(
                type = innerTypes.getOrNull(j),
                element = innerValues.getOrNull(j),
                abi = childAbi,
                positionalPath = "$positionalPath.${j + 1}",
                namePath = joinNamePath(namePath, sanitizedName(childAbi)),
                depth = depth + 1,
                contractLabel = contractLabel,
                out = out,
            )
        }
        return
    }
    val tupleElementType =
        if (depth < MAX_PARAM_DEPTH && normalized != null) tupleArrayElementType(normalized)
        else null
    if (tupleElementType != null) {
        // A tuple array like `(address,bytes)[]` — expand one entry per element (recursing into the
        // element tuple) instead of collapsing the whole array into one bracketed leaf, honouring
        // the per-field expansion the KDoc promises. The element's ABI components are unchanged, so
        // the same [abi] carries through.
        val elements = (element as? JsonArray)?.toList().orEmpty()
        for (k in elements.indices) {
            if (out.size >= MAX_PARAM_ROWS) return
            expandParam(
                type = tupleElementType,
                element = elements[k],
                abi = abi,
                positionalPath = "$positionalPath[$k]",
                namePath = namePath?.let { "$it[$k]" },
                depth = depth + 1,
                contractLabel = contractLabel,
                out = out,
            )
        }
        return
    }
    out += leafRow(normalized, element, positionalPath, namePath, contractLabel)
}

private fun leafRow(
    type: String?,
    element: JsonElement?,
    positionalPath: String,
    namePath: String?,
    contractLabel: (String) -> String?,
): DecodedFunctionParam {
    val value = element?.flatString().orEmpty()
    val isScalar = type != null && !type.endsWith("[]")
    val isAddress = isScalar && type.equals("address", ignoreCase = true)
    val isBytes = isScalar && type?.startsWith("bytes", ignoreCase = true) == true
    val label =
        if (namePath != null) {
            UiText.DynamicString(namePath)
        } else {
            UiText.DynamicString(
                buildString {
                    append(positionalPath)
                    if (type != null) {
                        append(" (")
                        append(type)
                        append(')')
                    }
                }
            )
        }
    return DecodedFunctionParam(
        label = label,
        // The value is attacker-controlled calldata (e.g. a `string` param), rendered verbatim, so
        // strip control/bidi codepoints and cap its length the same way [sanitizedName] guards
        // names. The copyable value below stays full so addresses/bytes can still be copied whole.
        value = UiText.DynamicString(sanitizedValue(value)),
        // Addresses and bytes are middle-ellipsised by the renderer; keep the full value copyable
        // so
        // nothing the user is signing is hidden behind the truncation.
        copyableValue = if (isAddress || isBytes) value.takeIf { it.isNotBlank() } else null,
        secondary = if (isAddress) contractLabel(value) else null,
    )
}

/** A plain tuple `(…)` — not a tuple array `(…)[]`, which is expanded per element instead. */
private fun isPlainTuple(type: String): Boolean = type.startsWith("(") && type.endsWith(")")

/**
 * For a tuple array returns the element type one array dimension shallower, so each recursion peels
 * exactly one level: `(address,bytes)[]` -> `(address,bytes)`, and a nested `(address,bytes)[][]`
 * -> `(address,bytes)[]` (which the next recursion reduces again). Returns null for plain tuples
 * and non-tuple types. Stripping only the outermost (rightmost) dimension keeps rows/values aligned
 * — dropping every `[]` at once would treat a nested array element as a bare tuple and misalign the
 * signing screen.
 */
private fun tupleArrayElementType(type: String): String? {
    if (!type.startsWith("(") || !type.endsWith("]")) return null
    var depth = 0
    var closeIndex = -1
    for (i in type.indices) {
        when (type[i]) {
            '(' -> depth++
            ')' -> {
                depth--
                if (depth == 0) {
                    closeIndex = i
                    break
                }
            }
        }
    }
    if (closeIndex < 0) return null
    val suffix = type.substring(closeIndex + 1)
    if (!suffix.startsWith("[") || !suffix.endsWith("]")) return null
    // Peel only the outermost (rightmost) array dimension, preserving any inner ones.
    val lastDimStart = suffix.dropLast(1).lastIndexOf('[')
    val remainingSuffix = if (lastDimStart > 0) suffix.substring(0, lastDimStart) else ""
    return type.substring(0, closeIndex + 1) + remainingSuffix
}

/**
 * Accepts an ABI-provided parameter name only when it is a plain solidity identifier of reasonable
 * length. The ABI is attacker-influenceable (a contract author controls their own source), and the
 * name is rendered verbatim into the signing screen, so anything with whitespace, control
 * characters, lookalike Unicode, or excessive length is rejected back to the positional label.
 */
private fun sanitizedName(abi: AbiParam?): String? =
    abi?.name?.takeIf { it.length in 1..MAX_PARAM_NAME_LENGTH && it.matches(SOLIDITY_IDENTIFIER) }

/**
 * Strips control/bidi codepoints (reusing [sanitizeDisplayString]) and caps the length of an
 * attacker-controlled leaf [raw] value before it is rendered, mirroring the guard [sanitizedName]
 * already applies to names so a hostile `string` param can't smuggle reordering, invisible content,
 * or an unbounded blob onto the signing screen.
 */
private fun sanitizedValue(raw: String): String {
    val stripped = sanitizeDisplayString(raw)
    return if (stripped.length > MAX_PARAM_VALUE_LENGTH) {
        stripped.take(MAX_PARAM_VALUE_LENGTH) + "…"
    } else {
        stripped
    }
}

private fun joinNamePath(parent: String?, child: String?): String? =
    when {
        // Gate the child on the parent having a name: an anonymous outer tuple with named inner
        // fields must not render a leaf as a bare `amount` with no `#1.1` anchor — fall back to the
        // positional path instead, which is less ambiguous than a context-free name.
        parent == null || child == null -> null
        else -> "$parent.$child"
    }

private val SOLIDITY_IDENTIFIER = Regex("[A-Za-z_][A-Za-z0-9_]*")
private const val MAX_PARAM_NAME_LENGTH = 40
private const val MAX_PARAM_VALUE_LENGTH = 256
private const val MAX_PARAM_DEPTH = 8
private const val MAX_PARAM_ROWS = 64

private fun addressRow(
    label: UiText,
    address: String,
    contractLabel: (String) -> String?,
): DecodedFunctionParam =
    DecodedFunctionParam(
        label = label,
        value = UiText.DynamicString(address),
        copyableValue = address,
        secondary = contractLabel(address),
    )

private fun amountRow(
    rawAmount: String,
    tokenSymbol: String?,
    tokenDecimals: Int?,
    isUnlimited: Boolean,
): DecodedFunctionParam {
    val value: UiText =
        when {
            isUnlimited -> {
                val ticker = tokenSymbol?.takeIf { it.isNotBlank() }
                if (ticker != null) {
                    UiText.FormattedText(R.string.decoded_function_unlimited_amount, listOf(ticker))
                } else {
                    UiText.StringResource(R.string.decoded_function_unlimited)
                }
            }
            else -> {
                val display = formatAmount(rawAmount, tokenDecimals)
                val ticker = tokenSymbol?.takeIf { it.isNotBlank() }
                UiText.DynamicString(
                    when {
                        display == null -> rawAmount
                        ticker == null -> display
                        else -> "$display $ticker"
                    }
                )
            }
        }
    return DecodedFunctionParam(
        label = R.string.decoded_function_amount.asUiText(),
        value = value,
        isWarning = isUnlimited,
    )
}

/**
 * Scales an ABI uint amount into the token's display units when [decimals] is known and bounded.
 *
 * Decimals are validated as `0..[MAX_TOKEN_DECIMALS]`; anything outside falls back to the raw
 * integer, matching [com.vultisig.wallet.data.repositories.TokenMetadataResolver]'s own decimals
 * ceiling so a hostile contract claiming `decimals() = 255` cannot make the verify screen render a
 * million-character zero-padded string. Trailing zeros are stripped from the result so a clean `1`
 * shows up instead of `1.000000`.
 */
private fun formatAmount(rawAmount: String, decimals: Int?): String? {
    val raw = parseBigInteger(rawAmount) ?: return null
    if (decimals == null || decimals !in 0..MAX_TOKEN_DECIMALS) return raw.toString()
    if (decimals == 0) return raw.toString()
    return BigDecimal(raw).movePointLeft(decimals).stripTrailingZeros().toPlainString()
}

private fun parseBigInteger(value: String): BigInteger? {
    val trimmed = value.trim()
    if (trimmed.isEmpty()) return null
    return try {
        when {
            trimmed.startsWith("0x", ignoreCase = true) -> BigInteger(trimmed.substring(2), 16)
            else -> BigInteger(trimmed)
        }
    } catch (_: NumberFormatException) {
        null
    }
}

private const val MAX_TOKEN_DECIMALS = 36

private fun List<JsonElement>.flatString(index: Int): String? =
    getOrNull(index)?.flatString()?.takeIf { it.isNotBlank() }

private fun JsonElement.flatString(): String? =
    when (this) {
        is JsonNull -> null
        is JsonPrimitive -> content.takeIf { it.isNotEmpty() }
        is JsonArray ->
            joinToString(prefix = "[", postfix = "]") { it.flatString().orEmpty() }
                .takeIf { it.isNotEmpty() }
        else -> toString()
    }
