package com.vultisig.wallet.data.api.swapAggregators

import com.vultisig.wallet.data.api.models.quotes.OneInchSwapTxJson
import com.vultisig.wallet.data.blockchain.ethereum.ERC20_TRANSFER_SELECTOR
import com.vultisig.wallet.data.blockchain.ethereum.decodeErc20TransferCallData
import com.vultisig.wallet.data.common.ZERO_ADDRESS
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.EVMSwapPayloadJson
import com.vultisig.wallet.data.models.SwapProvider
import com.vultisig.wallet.data.models.TokenStandard
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
 * @throws IllegalArgumentException when [tx] is either of those but not exactly the deposit:
 *   another token, native value attached (`tx.value` read by [nativeValue]), other calldata, or
 *   another amount.
 */
fun swapKitErc20DepositRecipient(
    tx: OneInchSwapTxJson,
    srcToken: Coin,
    amount: BigInteger,
): String? {
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
    require(tx.nativeValue().signum() == 0) {
        "SwapKit ERC-20 deposit attaches native value ${tx.value}"
    }
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
 * Recipient of a verifiable SwapKit EVM deposit, including in history for previously signed swaps.
 * Unsupported shapes have no verified recipient to show. The signing guard rejects them.
 */
fun EVMSwapPayloadJson.swapKitDepositRecipient(signingChain: Chain): String? {
    val isSwapKit = swapProviderFromWireId(provider) == SwapProvider.SWAPKIT
    if (!isSwapKit || signingChain.standard != TokenStandard.EVM) return null
    return try {
        quote.tx.requireSwapKitEvmDeposit(fromCoin, fromAmount)
    } catch (_: IllegalArgumentException) {
        null
    }
}

/** Returns the recipient from the only SwapKit EVM shapes Android can verify independently. */
internal fun OneInchSwapTxJson.requireSwapKitEvmDeposit(
    srcToken: Coin,
    amount: BigInteger,
): String {
    require(amount.signum() > 0 && amount.bitLength() <= 256) {
        "SwapKit deposit amount must be a positive uint256"
    }
    require(EVM_ADDRESS.matches(to)) { "SwapKit deposit has an invalid transaction target" }
    val recipient =
        if (srcToken.isNativeToken) {
            require(data.isEmpty() || data == "0x") {
                "Android cannot verify this SwapKit contract call"
            }
            // Empty calldata can still invoke a contract. Allow only the intrinsic gas for a
            // value transfer, so the receiving account cannot run a fallback with spare gas.
            require(gas == EVM_NATIVE_DEPOSIT_GAS) {
                "Android cannot verify a SwapKit native deposit with extra execution gas"
            }
            require(nativeValue() == amount) { "SwapKit deposit does not match the sold amount" }
            to
        } else {
            requireNotNull(swapKitErc20DepositRecipient(this, srcToken, amount)) {
                "Android cannot verify this SwapKit contract call"
            }
        }
    require(!recipient.equals(ZERO_ADDRESS, ignoreCase = true)) {
        "SwapKit deposit recipient is the zero address"
    }
    return recipient
}

internal const val EVM_NATIVE_DEPOSIT_GAS = 21_000L

private val EVM_ADDRESS = Regex("0x[0-9a-fA-F]{40}")
