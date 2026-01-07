@file:OptIn(ExperimentalStdlibApi::class)

package com.vultisig.wallet.data.chains.helpers

import com.vultisig.wallet.data.common.add0x
import com.vultisig.wallet.data.common.convertToBigIntegerOrZero
import com.vultisig.wallet.data.common.remove0x
import com.vultisig.wallet.data.utils.toSafeByteArray
import okio.ByteString.Companion.decodeHex
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

    fun balanceErc20Decoder(hexBalance: String): BigInteger {
        val fn = EthereumAbiFunction("balanceOf")
        fn.addParamUInt256(ByteArray(32), true)
        val dataHex = hexBalance.remove0x()
        val encodedBytes = dataHex.decodeHex().toByteArray()
        if (!EthereumAbi.decodeOutput(fn, encodedBytes)) {
            throw IllegalArgumentException(": ABI decoding failed")
        }
        return fn.getParamUInt256(0, true)
            .toHexString()
            .convertToBigIntegerOrZero()
    }

    fun withdrawCircleMSCA(
        vaultAddress: String,
        tokenAddress: String,
        amount: BigInteger
    ): String {
        require(amount >= BigInteger.ZERO) { "Amount must be non-negative" }
        require(vaultAddress.isNotBlank()) { "Vault address cannot be blank" }
        require(tokenAddress.isNotBlank()) { "MSCA (token) address cannot be blank" }

        try {
            // Inner call: ERC20.transfer(vaultAddress, amount)
            val erc20TransferData = transferErc20Encoder(vaultAddress, amount)
                .remove0x()
                .decodeHex()
                .toByteArray()

            val tokenAddr = AnyAddress(tokenAddress, CoinType.ETHEREUM)

            // execute(address _to, uint256 _value, bytes _data)
            val executeFn = EthereumAbiFunction("execute").apply {
                addParamAddress(tokenAddr.data(), false)
                addParamUInt256(ByteArray(32), false)
                addParamBytes(erc20TransferData, false)
            }

            return EthereumAbi.encode(executeFn)
                .toHexString()
                .add0x()
        } catch (e: Exception) {
            throw IllegalArgumentException(
                "Failed to encode Circle MSCA withdraw: ${e.message}",
                e
            )
        }
    }
}