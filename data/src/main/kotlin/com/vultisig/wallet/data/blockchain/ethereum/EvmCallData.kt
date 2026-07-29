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
    val methodId = "a9059cbb"
    val paddedAddress = recipient.removePrefix("0x").padStart(64, '0')
    val paddedValue = amount.toString(16).padStart(64, '0')
    return Numeric.hexStringToByteArray(methodId + paddedAddress + paddedValue)
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
