package com.vultisig.wallet.data.chains.helpers

import com.vultisig.wallet.data.utils.add0x
import wallet.core.jni.AnyAddress
import wallet.core.jni.CoinType
import wallet.core.jni.EthereumAbi
import wallet.core.jni.EthereumAbiFunction
import java.math.BigInteger

object EthereumFunction {
    @OptIn(ExperimentalStdlibApi::class)
    fun transferErc20(address: String, amount: BigInteger): String {
        require(amount >= BigInteger.ZERO) { "Amount must be non-negative" }
        require(address.isNotBlank()) { "Address cannot be blank" }

        try {
            val destinationAddress = AnyAddress(address, CoinType.ETHEREUM)
            val amountOut = amount.toByteArray()

            val encodedFunction = EthereumAbiFunction("transfer").apply {
                addParamAddress(destinationAddress.data(), false)
                addParamUInt256(amountOut, false)
            }
            return EthereumAbi.encode(encodedFunction).toHexString().add0x()
        } catch (e: Exception) {
            throw IllegalArgumentException("Failed to encode ERC-20 transfer: ${e.message}", e)
        }
    }
}
