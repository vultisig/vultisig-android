package com.vultisig.wallet.ui.models.keysign

import androidx.compose.runtime.Immutable
import com.vultisig.wallet.R
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
    if (handler != null && inputs.size == parsed.types.size) {
        handler(ctx)
            ?.takeIf { it.isNotEmpty() }
            ?.let {
                return it
            }
    }
    return genericParams(parsed.types, inputs, contractLabel)
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

private val PARAM_HANDLERS: Map<String, ParamHandler> =
    mapOf(
        "approve" to ::approveRows,
        "permit" to ::permitRows,
        "transfer" to ::transferRows,
        "transferfrom" to ::transferFromRows,
        "setapprovalforall" to ::setApprovalForAllRows,
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

private fun genericParams(
    types: List<String>,
    inputs: List<JsonElement>,
    contractLabel: (String) -> String?,
): List<DecodedFunctionParam> {
    val rowCount = maxOf(types.size, inputs.size)
    return (0 until rowCount).map { index ->
        val type = types.getOrNull(index)?.takeIf { it.isNotBlank() }
        val element = inputs.getOrNull(index)
        val value = element?.flatString().orEmpty()
        val labelText = buildString {
            append('#')
            append(index + 1)
            if (type != null) {
                append(" (")
                append(type)
                append(')')
            }
        }
        val isAddress = type != null && type.equals("address", ignoreCase = true)
        DecodedFunctionParam(
            label = UiText.DynamicString(labelText),
            value = UiText.DynamicString(value),
            copyableValue = if (isAddress) value.takeIf { it.isNotBlank() } else null,
            secondary = if (isAddress) contractLabel(value) else null,
        )
    }
}

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
