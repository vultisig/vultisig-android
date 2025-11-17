package com.vultisig.wallet.data.chains.helpers

import com.vultisig.wallet.data.common.stripHexPrefix
import java.math.BigInteger

object TronFunctions {
    fun buildTrc20TransferParameters(recipientBaseHex: String, amount: BigInteger): String {
        val paddedAddressHex = recipientBaseHex.stripHexPrefix().drop(2).padStart(64, '0')
        val paddedAmountHex = amount.toString(16).padStart(64, '0')
        return paddedAddressHex + paddedAmountHex
    }
}