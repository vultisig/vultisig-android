@file:OptIn(ExperimentalStdlibApi::class)

package com.vultisig.wallet.data.chains.helpers

import com.vultisig.wallet.data.common.add0x
import com.vultisig.wallet.data.common.convertToBigIntegerOrZero
import com.vultisig.wallet.data.common.remove0x
import com.vultisig.wallet.data.utils.toSafeByteArray
import wallet.core.jni.AnyAddress
import wallet.core.jni.CoinType
import wallet.core.jni.EthereumAbi
import wallet.core.jni.EthereumAbiFunction
import java.math.BigInteger

object EthereumFunction {
    fun transferErc20Encoder(address: String, amount: BigInteger): String {
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

    fun approvalErc20Encoder(address: String, amount: BigInteger): String {
        require(amount >= BigInteger.ZERO) { "Amount must be non-negative" }
        require(address.isNotBlank()) { "Address cannot be blank" }

        try {
            val destinationAddress = AnyAddress(address, CoinType.ETHEREUM)
            val amountOut = amount.toSafeByteArray()

            val encodedFunction = EthereumAbiFunction("approve").apply {
                addParamAddress(destinationAddress.data(), false)
                addParamUInt256(amountOut, false)
            }
            return EthereumAbi.encode(encodedFunction).toHexString().add0x()
        } catch (e: Exception) {
            throw IllegalArgumentException("Failed to encode ERC-20 approval: ${e.message}", e)
        }
    }

    fun balanceDecoder(hexBalance: String): BigInteger {
        val fn = EthereumAbiFunction("balanceOf")
        fn.addParamUInt256(ByteArray(32), true)
        val dataHex = hexBalance.remove0x()
        val encodedBytes = dataHex.chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()

        if (!EthereumAbi.decodeOutput(fn, encodedBytes)) {
            throw IllegalArgumentException("parse balance: ABI decoding failed")
        }
        return fn.getParamUInt256(0, true)
            .toHexString()
            .convertToBigIntegerOrZero()
    }
}