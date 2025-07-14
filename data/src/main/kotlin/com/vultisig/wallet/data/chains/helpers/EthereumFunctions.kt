package com.vultisig.wallet.data.chains.helpers

import com.vultisig.wallet.data.common.add0x
import com.vultisig.wallet.data.utils.toSafeByteArray
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
            val amountOut = amount.toSafeByteArray()

            val encodedFunction = EthereumAbiFunction("transfer").apply {
                addParamAddress(destinationAddress.data(), false)
                addParamUInt256(amountOut, false)
            }
            return EthereumAbi.encode(encodedFunction).toHexString().add0x()
        } catch (e: Exception) {
            throw IllegalArgumentException("Failed to encode ERC-20 transfer: ${e.message}", e)
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    fun createDepositWithExpiryFunction(
        vaultAddress: String,
        contractAddress: String,
        fromAmount: BigInteger,
        memo: String,
        expirationTime: BigInteger
    ): String {
        val encodedFunction = EthereumAbiFunction("depositWithExpiry").apply {
            addParamAddress(vaultAddress.data(), false)
            addParamAddress(contractAddr.data(), false)
            addParamUInt256(thorChainSwapPayload.data.fromAmount.toByteArray(), false)
            addParamString(keysignPayload.memo, false)
            addParamUInt256(
                BigInteger(thorChainSwapPayload.data.expirationTime.toString()).toByteArray(),
                false
            )
        }

        // Encode the function and return the hex string
        return EthereumAbi.encode(encodedFunction).toHexString().add0x()
    }
}