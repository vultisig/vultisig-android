package com.vultisig.wallet.ui.models.send

import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.ui.utils.UiText
import java.math.BigInteger

/** Names the exact asset a send is short of, how much it needs and how much the vault holds. */
internal fun insufficientFundsText(
    token: Coin,
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
        listOf(token.ticker, formatAmount(required, token), formatAmount(available, token)),
    )

private fun formatAmount(value: BigInteger, token: Coin): String =
    "${TokenValue.createDecimal(value, token.decimal).stripTrailingZeros().toPlainString()} ${token.ticker}"
