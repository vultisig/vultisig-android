package com.vultisig.wallet.ui.models.keysign

import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.repositories.TokenMetadata
import com.vultisig.wallet.data.repositories.TokenMetadataResolver
import com.vultisig.wallet.data.repositories.UniversalRouterDecoder
import com.vultisig.wallet.data.repositories.UniversalRouterSwapIntent
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.asUiText
import java.math.BigDecimal
import java.math.BigInteger

/**
 * Builds the labelled rows shown inside the verify-screen Transaction Details disclosure when the
 * decoded contract call is a Uniswap Universal Router swap.
 *
 * Each swap surfaces four rows in the user's perspective:
 * - **From Token** — `intent.fromToken`, ticker if resolved (native uses the chain's fee coin),
 *   secondary row carries the contract address so the user can copy the full hex.
 * - **Amount In** / **Max Amount In** — `intent.amountIn`, scaled by the fromToken decimals when
 *   known. The label swaps to `Max Amount In` for exact-out so the user knows it's a ceiling.
 * - **To Token** — same shape as the From row, for `intent.toToken`.
 * - **Min Amount Out** / **Amount Out** — `intent.amountOutMin`, with the symmetric exact-out label
 *   swap.
 *
 * Returns `null` when [intent] is absent — callers should then leave the generic Phase-2A rows in
 * place. The function never throws; unresolved tokens fall back to the bare contract address with
 * no symbol/decimals applied so the user still sees the on-chain truth.
 */
internal suspend fun universalRouterSwapRows(
    chain: Chain,
    intent: UniversalRouterSwapIntent?,
    allVaults: List<Vault>,
    tokenMetadataResolver: TokenMetadataResolver,
    nativeTokenLookup: suspend (Chain) -> Coin? = { null },
): List<DecodedFunctionParam>? {
    if (intent == null) return null

    val fromInfo =
        resolveTokenInfo(
            chain,
            intent.fromToken,
            allVaults,
            tokenMetadataResolver,
            nativeTokenLookup,
        )
    val toInfo =
        resolveTokenInfo(chain, intent.toToken, allVaults, tokenMetadataResolver, nativeTokenLookup)

    val amountInLabel =
        if (intent.isExactOut) R.string.decoded_function_max_amount_in
        else R.string.decoded_function_amount_in
    val amountOutLabel =
        if (intent.isExactOut) R.string.decoded_function_amount_out
        else R.string.decoded_function_min_amount_out

    return listOf(
        tokenRow(R.string.decoded_function_from_token, intent.fromToken, fromInfo),
        amountRow(amountInLabel, intent.amountIn, fromInfo),
        tokenRow(R.string.decoded_function_to_token, intent.toToken, toInfo),
        amountRow(amountOutLabel, intent.amountOutMin, toInfo),
    )
}

private data class ResolvedTokenInfo(val symbol: String?, val decimals: Int?)

private suspend fun resolveTokenInfo(
    chain: Chain,
    tokenAddress: String,
    allVaults: List<Vault>,
    tokenMetadataResolver: TokenMetadataResolver,
    nativeTokenLookup: suspend (Chain) -> Coin?,
): ResolvedTokenInfo {
    if (tokenAddress.equals(UniversalRouterDecoder.NATIVE_TOKEN_ADDRESS, ignoreCase = true)) {
        val native = nativeTokenLookup(chain)
        return ResolvedTokenInfo(symbol = native?.ticker, decimals = native?.decimal)
    }
    val vaultMatch =
        allVaults
            .asSequence()
            .flatMap { it.coins.asSequence() }
            .firstOrNull { coin ->
                coin.chain == chain && coin.contractAddress.equals(tokenAddress, ignoreCase = true)
            }
    if (vaultMatch != null && vaultMatch.ticker.isNotBlank()) {
        return ResolvedTokenInfo(symbol = vaultMatch.ticker, decimals = vaultMatch.decimal)
    }
    val resolved: TokenMetadata? = tokenMetadataResolver.resolve(chain, tokenAddress)
    return ResolvedTokenInfo(symbol = resolved?.symbol, decimals = resolved?.decimals)
}

private fun tokenRow(
    labelRes: Int,
    address: String,
    info: ResolvedTokenInfo,
): DecodedFunctionParam {
    val symbol = info.symbol?.takeIf { it.isNotBlank() }
    val isNative = address.equals(UniversalRouterDecoder.NATIVE_TOKEN_ADDRESS, ignoreCase = true)
    return if (symbol != null) {
        DecodedFunctionParam(
            label = labelRes.asUiText(),
            value = UiText.DynamicString(symbol),
            // Suppress the zero-address noise for native — the symbol already conveys the chain
            // coin and the address would just be 40 zeros.
            copyableValue = address.takeUnless { isNative },
            secondary = address.takeUnless { isNative },
        )
    } else {
        DecodedFunctionParam(
            label = labelRes.asUiText(),
            value = UiText.DynamicString(address),
            copyableValue = address.takeUnless { isNative },
        )
    }
}

private fun amountRow(
    labelRes: Int,
    rawAmount: BigInteger,
    info: ResolvedTokenInfo,
): DecodedFunctionParam {
    val display = formatAmount(rawAmount, info.decimals)
    val ticker = info.symbol?.takeIf { it.isNotBlank() }
    val text = if (ticker != null) "$display $ticker" else display
    return DecodedFunctionParam(label = labelRes.asUiText(), value = UiText.DynamicString(text))
}

/**
 * Scales an ABI uint amount into the token's display units. Decimals outside `0..[MAX_DECIMALS]`
 * fall back to the raw integer, mirroring
 * [com.vultisig.wallet.data.repositories.TokenMetadataResolver]'s own ceiling so a hostile contract
 * claiming `decimals() = 255` cannot make the verify screen render a million-character zero-padded
 * string. Trailing zeros are stripped so the row shows `1` instead of `1.000000` for a clean
 * whole-unit amount.
 */
private fun formatAmount(rawAmount: BigInteger, decimals: Int?): String {
    if (decimals == null || decimals !in 0..MAX_DECIMALS) return rawAmount.toString()
    if (decimals == 0) return rawAmount.toString()
    return BigDecimal(rawAmount).movePointLeft(decimals).stripTrailingZeros().toPlainString()
}

private const val MAX_DECIMALS = 36
