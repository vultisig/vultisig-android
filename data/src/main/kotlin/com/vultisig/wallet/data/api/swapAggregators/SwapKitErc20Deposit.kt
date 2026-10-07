package com.vultisig.wallet.data.api.swapAggregators

import com.vultisig.wallet.data.api.models.quotes.OneInchSwapTxJson
import com.vultisig.wallet.data.blockchain.ethereum.ERC20_TRANSFER_SELECTOR
import com.vultisig.wallet.data.blockchain.ethereum.decodeErc20TransferCallData
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.EVMSwapPayloadJson
import com.vultisig.wallet.data.models.SwapProvider
import com.vultisig.wallet.data.models.TokenStandard
import com.vultisig.wallet.data.models.parseSwapKitDecimal
import com.vultisig.wallet.data.models.swapProviderFromWireId
import java.math.BigInteger

/**
 * True when this EVM swap tx calls the sold token [srcToken] itself, whatever the provider. For
 * SwapKit that is a NEAR-Intents ERC-20 deposit (`txHint: simpleTransfer`), which carries the
 * deposit address in `transfer` calldata and spends no allowance.
 */
fun OneInchSwapTxJson.callsSoldToken(srcToken: Coin): Boolean =
    !srcToken.isNativeToken &&
        srcToken.contractAddress.isNotEmpty() &&
        to.equals(srcToken.contractAddress, ignoreCase = true)

/**
 * Binds a SwapKit ERC-20 deposit to the bytes that get signed. Returns the lowercase `0x` recipient
 * when [tx] is exactly `transfer(recipient, amount)` on the sold token [srcToken] with no native
 * value, where [amount] is the sold amount. Returns null when [tx] is neither addressed to the sold
 * token nor a `transfer` call. Mirrors vultisig-sdk's `getSwapKitErc20DepositRecipient`.
 *
 * @throws IllegalArgumentException when `tx.value` is not a plain decimal uint256
 *   ([parseSwapKitDecimal]), or when [tx] is either of those but not exactly the deposit: another
 *   token, native value attached, other calldata, or another amount.
 */
fun swapKitErc20DepositRecipient(
    tx: OneInchSwapTxJson,
    srcToken: Coin,
    amount: BigInteger,
): String? {
    val value = parseSwapKitDecimal(tx.value, "tx.value")
    val isTokenAddressed = tx.callsSoldToken(srcToken)
    val isTransferCall = tx.data.lowercase().removePrefix("0x").startsWith(ERC20_TRANSFER_SELECTOR)
    if (!isTokenAddressed && !isTransferCall) return null

    require(isTokenAddressed) {
        if (srcToken.isNativeToken) {
            "SwapKit swap of native ${srcToken.ticker} carries an ERC-20 transfer call to ${tx.to}"
        } else {
            "SwapKit ERC-20 deposit calls transfer on ${tx.to}, not the sold token " +
                "${srcToken.ticker} (${srcToken.contractAddress})"
        }
    }
    require(value.signum() == 0) { "SwapKit ERC-20 deposit attaches native value ${tx.value}" }
    val transfer =
        requireNotNull(decodeErc20TransferCallData(tx.data)) {
            "SwapKit ERC-20 deposit is not exactly an ERC-20 transfer(address,uint256) call"
        }
    require(transfer.amount == amount) {
        "SwapKit ERC-20 deposit transfers ${transfer.amount}, not the sold amount $amount"
    }
    return transfer.recipient
}

/**
 * The recipient of the SwapKit ERC-20 deposit this payload signs on [signingChain] (the keysign
 * coin's chain), decoded from its calldata by [swapKitErc20DepositRecipient]; null when it is not a
 * SwapKit swap, [signingChain] is not EVM, or it is not a deposit.
 *
 * @throws IllegalArgumentException when it has the deposit shape but is not exactly the deposit.
 */
fun EVMSwapPayloadJson.swapKitDepositRecipient(signingChain: Chain): String? {
    val isSwapKit = swapProviderFromWireId(provider) == SwapProvider.SWAPKIT
    if (!isSwapKit || signingChain.standard != TokenStandard.EVM) return null
    return swapKitErc20DepositRecipient(quote.tx, fromCoin, fromAmount)
}
