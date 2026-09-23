package com.vultisig.wallet.ui.models.sign

import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.ImageModel
import com.vultisig.wallet.ui.models.keysign.DecodedFunctionParam
import com.vultisig.wallet.ui.models.keysign.MAX_PARAM_ROWS
import com.vultisig.wallet.ui.models.keysign.sanitizedValue
import com.vultisig.wallet.ui.models.keysign.truncatedRow
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.asUiText
import java.math.BigDecimal
import java.math.BigInteger
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

/** What a permit's token address resolved to, so the rows can name and scale it. */
internal data class PermitTokenInfo(val symbol: String, val decimals: Int, val logo: ImageModel?)

/**
 * The rows for a token approval, laid out as the extension's `Eip712PermitDisplay` does: the
 * action, then each token with its amount and expiry, then who is being approved and until when.
 * [tokens] lines up with [Eip712Permit.tokens]; a null entry leaves that token as its address.
 */
internal fun permitRows(
    permit: Eip712Permit,
    tokens: List<PermitTokenInfo?>,
    spenderLabel: String?,
): List<DecodedFunctionParam> = buildList {
    add(
        DecodedFunctionParam(
            label = R.string.typed_data_action.asUiText(),
            value =
                if (permit.isTransfer) R.string.typed_data_token_transfer.asUiText()
                else R.string.typed_data_token_approval.asUiText(),
        )
    )

    permit.tokens.forEachIndexed { index, token ->
        val info = tokens.getOrNull(index)
        add(tokenRow(token, info))
        add(amountRow(permit, token, info))
        token.expiration?.let { expiration ->
            add(
                DecodedFunctionParam(
                    label = R.string.typed_data_approval_expires.asUiText(),
                    value = formatDeadline(expiration),
                )
            )
        }
    }

    add(
        DecodedFunctionParam(
            label = R.string.erc20_approval_spender.asUiText(),
            value = permit.spender.asUiText(),
            copyableValue = permit.spender,
            secondary = spenderLabel,
        )
    )

    permit.deadline?.let { deadline ->
        add(
            DecodedFunctionParam(
                label = R.string.decoded_function_deadline.asUiText(),
                value = formatDeadline(deadline),
            )
        )
    }
}

/**
 * The rows for typed data that is not a known permit: where it comes from, what it is, and each
 * top-level field of the message as written. Nested values are shown as their JSON, since a
 * generic reading cannot say what they mean. Keys and values come straight from the dApp, so they
 * are sanitized and length-capped, and the field count is capped at [MAX_PARAM_ROWS].
 */
internal fun typedDataRows(data: Eip712TypedData): List<DecodedFunctionParam> = buildList {
    data.domainName?.let { name ->
        add(
            DecodedFunctionParam(
                label = R.string.typed_data_domain.asUiText(),
                value = sanitizedValue(name).asUiText(),
            )
        )
    }
    data.domainChainId?.let { chainId ->
        add(
            DecodedFunctionParam(
                label = R.string.typed_data_chain_id.asUiText(),
                value = chainId.toString().asUiText(),
            )
        )
    }
    add(
        DecodedFunctionParam(
            label = R.string.typed_data_primary_type.asUiText(),
            value = sanitizedValue(data.primaryType).asUiText(),
        )
    )
    data.message.entries.take(MAX_PARAM_ROWS).forEach { (key, value) ->
        add(
            DecodedFunctionParam(
                label = sanitizedValue(key).asUiText(),
                value = sanitizedValue(value.flatString()).asUiText(),
            )
        )
    }
    if (data.message.size > MAX_PARAM_ROWS) add(truncatedRow())
}

private fun tokenRow(token: PermitToken, info: PermitTokenInfo?): DecodedFunctionParam =
    if (info != null) {
        DecodedFunctionParam(
            label = R.string.typed_data_token.asUiText(),
            value = info.symbol.asUiText(),
            copyableValue = token.address,
            secondary = token.address,
            logo = info.logo,
        )
    } else {
        DecodedFunctionParam(
            label = R.string.typed_data_token.asUiText(),
            value = token.address.asUiText(),
            copyableValue = token.address,
        )
    }

private fun amountRow(
    permit: Eip712Permit,
    token: PermitToken,
    info: PermitTokenInfo?,
): DecodedFunctionParam {
    val isUnlimited = permit.isUnlimited(token.amount)
    val value: UiText =
        when {
            isUnlimited && info != null ->
                UiText.FormattedText(R.string.decoded_function_unlimited_amount, listOf(info.symbol))
            isUnlimited -> R.string.decoded_function_unlimited.asUiText()
            info != null -> "${formatAmount(token.amount, info.decimals)} ${info.symbol}".asUiText()
            // Without decimals the raw integer is the only honest rendering.
            else -> token.amount.toString().asUiText()
        }
    return DecodedFunctionParam(
        label =
            if (permit.isTransfer) R.string.typed_data_transfer_amount.asUiText()
            else R.string.typed_data_approval_amount.asUiText(),
        value = value,
        isWarning = isUnlimited,
    )
}

private fun formatAmount(rawAmount: BigInteger, decimals: Int): String =
    if (decimals <= 0) rawAmount.toString()
    else BigDecimal(rawAmount).movePointLeft(decimals).stripTrailingZeros().toPlainString()

/**
 * A unix-seconds deadline as a local date and time. Zero and anything at or past the uint48
 * ceiling read as "no expiry": Permit2 stores expirations as uint48, where the max value is its
 * documented sentinel, and the extension applies the same reading to `sigDeadline`.
 */
private fun formatDeadline(seconds: BigInteger): UiText {
    if (seconds.signum() <= 0 || seconds >= MAX_UINT48) {
        return R.string.typed_data_no_expiry.asUiText()
    }
    val instant = Instant.ofEpochSecond(seconds.toLong())
    return DEADLINE_FORMAT.withZone(ZoneId.systemDefault()).format(instant).asUiText()
}

private fun JsonElement.flatString(): String =
    when (this) {
        is JsonNull -> ""
        is JsonPrimitive -> content
        else -> toString()
    }

private val MAX_UINT48: BigInteger = BigInteger.ONE.shiftLeft(48) - BigInteger.ONE

private val DEADLINE_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
