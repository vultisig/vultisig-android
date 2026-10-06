package com.vultisig.wallet.data.api.swapAggregators

import com.vultisig.wallet.data.api.models.quotes.OneInchSwapTxJson
import com.vultisig.wallet.data.blockchain.ethereum.ERC20_TRANSFER_SELECTOR
import com.vultisig.wallet.data.blockchain.ethereum.decodeErc20TransferCallData
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.EVMSwapPayloadJson
import com.vultisig.wallet.data.models.SwapProvider
import com.vultisig.wallet.data.models.TokenStandard
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.models.payload.SwapPayload
import com.vultisig.wallet.data.models.swapProviderFromWireId
import com.vultisig.wallet.data.securityscanner.blockaid.BlockaidRpcClientContract
import java.math.BigInteger
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/**
 * True when this EVM swap tx calls the sold token [srcToken] itself: a SwapKit NEAR-Intents ERC-20
 * deposit (`txHint: simpleTransfer`), which carries the deposit address in `transfer` calldata and
 * spends no allowance.
 */
fun OneInchSwapTxJson.isErc20DepositTransfer(srcToken: Coin): Boolean =
    !srcToken.isNativeToken &&
        srcToken.contractAddress.isNotEmpty() &&
        to.equals(srcToken.contractAddress, ignoreCase = true)

/**
 * Binds a SwapKit ERC-20 deposit to the bytes that get signed. Returns the lowercase `0x` recipient
 * when [tx] is exactly `transfer(recipient, amount)` on the sold token [srcToken] with no native
 * value, where [amount] is the sold amount. Returns null when [tx] is neither addressed to the sold
 * token nor a `transfer` call. Mirrors vultisig-sdk's `getSwapKitErc20DepositRecipient`.
 *
 * @throws IllegalArgumentException when `tx.value` is not plain decimal ([parseSwapKitDecimal]), or
 *   when [tx] is either of those but not exactly the deposit: another token, native value attached,
 *   other calldata, or another amount.
 */
fun swapKitErc20DepositRecipient(
    tx: OneInchSwapTxJson,
    srcToken: Coin,
    amount: BigInteger,
): String? {
    val value = parseSwapKitDecimal(tx.value, "tx.value")
    val isTokenAddressed = tx.isErc20DepositTransfer(srcToken)
    val isTransferCall = tx.data.lowercase().removePrefix("0x").startsWith(ERC20_TRANSFER_SELECTOR)
    if (!isTokenAddressed && !isTransferCall) return null

    require(isTokenAddressed) {
        "SwapKit ERC-20 deposit calls transfer on ${tx.to}, not the sold token " +
            "'${srcToken.contractAddress}'"
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

private val PLAIN_DECIMAL = Regex("[0-9]+")

private const val UINT256_BITS = 256

/**
 * Reads a SwapKit EVM wire amount as vultisig-sdk's `parseNonNegativeBigInt` does: ASCII decimal
 * digits only. `BigInteger` alone also reads a sign (`+100`, `-0`) and non-ASCII numerals, which
 * the SDK co-signer refuses. The amount must also fit the EVM word (at most 2^256 - 1), as iOS
 * `EVMSwapTxGuard.check` requires of `tx.value`.
 *
 * @throws IllegalArgumentException when [value] is anything else.
 */
internal fun parseSwapKitDecimal(value: String, field: String): BigInteger {
    require(value.matches(PLAIN_DECIMAL)) {
        "SwapKit EVM swap $field '$value' is not a plain decimal integer"
    }
    val amount = BigInteger(value)
    require(amount.bitLength() <= UINT256_BITS) {
        "SwapKit EVM swap $field '$value' exceeds uint256"
    }
    return amount
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

/**
 * Refuses a SwapKit ERC-20 deposit whose decoded recipient lacks a Benign Blockaid verdict. A
 * Warning or Malicious verdict, a chain Blockaid does not index, and a failed scan all refuse, as
 * vultisig-sdk's `assertSwapKitAddressReputation` does.
 */
class SwapKitDepositRecipientScreen
@Inject
constructor(private val blockaid: BlockaidRpcClientContract) {

    /** @throws IllegalStateException when the deposit recipient in [payload] is refused. */
    suspend operator fun invoke(payload: KeysignPayload) {
        val swap = (payload.swapPayload as? SwapPayload.EVM)?.data ?: return
        val chain = payload.coin.chain
        val recipient = swap.swapKitDepositRecipient(chain) ?: return
        val verdict =
            try {
                blockaid.scanEVMAddress(chain = chain, address = recipient)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw IllegalStateException(
                    "SwapKit deposit recipient $recipient could not be screened on ${chain.raw}",
                    e,
                )
            }
        check(verdict.resultType == BENIGN_VERDICT) {
            "SwapKit deposit recipient $recipient received a ${verdict.resultType} Blockaid " +
                "verdict on ${chain.raw} (${verdict.features.joinToString()})"
        }
    }

    private companion object {
        const val BENIGN_VERDICT = "Benign"
    }
}
