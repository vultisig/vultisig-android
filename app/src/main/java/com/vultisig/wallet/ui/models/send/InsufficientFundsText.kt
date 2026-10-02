package com.vultisig.wallet.ui.models.send

import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.ui.utils.UiText
import java.math.BigInteger

/** Names the exact asset a send is short of, how much it needs and how much the vault holds. */
internal fun insufficientFundsText(
    ticker: String,
    decimals: Int,
    required: BigInteger,
    available: BigInteger,
    includesNetworkCosts: Boolean,
): UiText =
    UiText.FormattedText(
        if (includesNetworkCosts) {
            R.string.send_error_insufficient_funds_including_network_costs
        } else {
            R.string.send_error_insufficient_funds_asset
        },
        listOf(
            ticker,
            formatAmount(required, decimals, ticker),
            formatAmount(available, decimals, ticker),
        ),
    )

private fun formatAmount(value: BigInteger, decimals: Int, ticker: String): String =
    "${TokenValue.createDecimal(value, decimals).stripTrailingZeros().toPlainString()} $ticker"
