package com.vultisig.wallet.data.models

import com.vultisig.wallet.data.common.DeepLinkHelper
import kotlinx.serialization.Serializable

/**
 * A `vultisig://deposit` link: a Custom deposit on [chainId] (a `Chain.raw`) with an optional coin
 * [assetTicker] (the chain's native coin when null), [amount] and [memo].
 */
@Serializable
data class DepositDeeplinkData(
    val chainId: String,
    val assetTicker: String?,
    val amount: String?,
    val memo: String?,
)

private val customDepositChains = setOf(Chain.ThorChain, Chain.MayaChain)

private val decimalAmount = Regex("""^\d+(\.\d+)?$""")

private val tickerShape = Regex("""^[A-Za-z0-9.-]{1,15}$""")

/**
 * Reads a `vultisig://deposit` link, or null when its chain has no Custom deposit, its amount is
 * not a plain decimal, or its ticker (shown back in errors) does not look like a ticker.
 */
fun DeepLinkHelper.toDepositDeeplinkData(): DepositDeeplinkData? {
    val chain =
        Chain.entries
            .find { it.raw.equals(getAssetChain(), ignoreCase = true) }
            ?.takeIf { it in customDepositChains } ?: return null
    val amount = getAmount()?.takeIf { it.isNotEmpty() }
    if (amount != null && !decimalAmount.matches(amount)) return null
    val ticker = getAssetTicker()?.takeIf { it.isNotEmpty() }
    if (ticker != null && !tickerShape.matches(ticker)) return null
    return DepositDeeplinkData(
        chainId = chain.raw,
        assetTicker = ticker,
        amount = amount,
        memo = getMemo()?.takeIf { it.isNotEmpty() },
    )
}
