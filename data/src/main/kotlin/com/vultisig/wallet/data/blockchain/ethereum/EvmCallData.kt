package com.vultisig.wallet.data.blockchain.ethereum

import com.vultisig.wallet.data.common.isHex
import com.vultisig.wallet.data.common.toHexBytes
import com.vultisig.wallet.data.utils.Numeric
import java.math.BigInteger

/**
 * ERC-20 `transfer(address,uint256)` calldata, built with plain hex concatenation (no JNI) so the
 * fee services stay unit-testable. Mirrors what an ERC-20 send actually broadcasts — WalletCore's
 * `ERC20Transfer` in `ERC20Helper`, and `EvmApi.constructERC20TransferData`.
 */
internal fun erc20TransferCallData(recipient: String, amount: BigInteger): ByteArray {
    val paddedAddress = recipient.removePrefix("0x").padStart(64, '0')
    val paddedValue = amount.toString(16).padStart(64, '0')
    return Numeric.hexStringToByteArray(ERC20_TRANSFER_SELECTOR + paddedAddress + paddedValue)
}

/** ERC-20 `transfer(address,uint256)` 4-byte selector (hex, no `0x`). */
internal const val ERC20_TRANSFER_SELECTOR = "a9059cbb"

/** An ERC-20 `transfer` call: the lowercase `0x` [recipient] and the raw token [amount]. */
internal data class Erc20Transfer(val recipient: String, val amount: BigInteger)

private val ERC20_TRANSFER_CALL_DATA =
    Regex("^${ERC20_TRANSFER_SELECTOR}0{24}([0-9a-f]{40})([0-9a-f]{64})$")

/**
 * Decodes [data] when it is exactly `transfer(address,uint256)` as [erc20TransferCallData] encodes
 * it: the selector, a zero-padded address word and an amount word, nothing after. Returns null for
 * any other calldata, including a dirty address word or trailing bytes.
 */
internal fun decodeErc20TransferCallData(data: String): Erc20Transfer? {
    val match = ERC20_TRANSFER_CALL_DATA.matchEntire(data.lowercase().removePrefix("0x"))
    val (recipient, amount) = match?.destructured ?: return null
    return Erc20Transfer(recipient = "0x$recipient", amount = BigInteger(amount, 16))
}

/**
 * The calldata a memo contributes to a native send. Mirrors `String.toByteStringOrHex`, the
 * encoding the signing path applies: a hex-looking memo is decoded to its bytes (half the
 * characters), any other text is UTF-8 encoded, and an absent memo contributes no calldata at all.
 * Fee estimates that price calldata by the byte have to size it the same way the signed transaction
 * will.
 */
internal fun memoCallData(memo: String?): ByteArray =
    memo?.takeIf { it.isNotEmpty() }?.let { if (it.isHex()) it.toHexBytes() else it.toByteArray() }
        ?: ByteArray(0)
