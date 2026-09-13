@file:OptIn(ExperimentalStdlibApi::class)

package com.vultisig.wallet.data.chains.helpers

import com.vultisig.wallet.data.common.add0x
import com.vultisig.wallet.data.common.convertToBigIntegerOrZero
import com.vultisig.wallet.data.common.hexToByteArrayOrNull
import com.vultisig.wallet.data.common.remove0x
import com.vultisig.wallet.data.utils.toSafeByteArray
import java.math.BigInteger
import okio.ByteString.Companion.decodeHex
import wallet.core.jni.AnyAddress
import wallet.core.jni.CoinType
import wallet.core.jni.EthereumAbi
import wallet.core.jni.EthereumAbiFunction

object EthereumFunction {
    fun transferErc20Encoder(address: String, amount: BigInteger): String {
        require(amount >= BigInteger.ZERO) { "Amount must be non-negative" }
        require(address.isNotBlank()) { "Address cannot be blank" }

        try {
            val destinationAddress = AnyAddress(address, CoinType.ETHEREUM)
            val amountOut = amount.toSafeByteArray()

            val encodedFunction =
                EthereumAbiFunction("transfer").apply {
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

            val encodedFunction =
                EthereumAbiFunction("approve").apply {
                    addParamAddress(destinationAddress.data(), false)
                    addParamUInt256(amountOut, false)
                }
            return EthereumAbi.encode(encodedFunction).toHexString().add0x()
        } catch (e: Exception) {
            throw IllegalArgumentException("Failed to encode ERC-20 approval: ${e.message}", e)
        }
    }

    // ABI-encodes the OP-stack `GasPriceOracle.getL1Fee(bytes)` call where `rlpEncodedUnsignedTx`
    // is
    // the RLP-encoded unsigned transaction whose L1 data fee we want priced.
    fun getL1FeeEncoder(rlpEncodedUnsignedTx: ByteArray): String {
        try {
            val encodedFunction =
                EthereumAbiFunction("getL1Fee").apply { addParamBytes(rlpEncodedUnsignedTx, false) }
            return EthereumAbi.encode(encodedFunction).toHexString().add0x()
        } catch (e: Exception) {
            throw IllegalArgumentException("Failed to encode getL1Fee call: ${e.message}", e)
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
        return fn.getParamUInt256(0, true).toHexString().convertToBigIntegerOrZero()
    }

    /**
     * Decodes the result of an ERC-20 `decimals()` `eth_call` into the declared decimal count.
     *
     * Returns null when the result carries no decodable `uint8`: `0x` from a contract that does not
     * implement `decimals()` (or an address that is not a token at all), a word shorter than 32
     * bytes, or non-hex. Decoding through the ABI decoder also narrows the value to `uint8`, so a
     * full-width response reads as 255 instead of wrapping to -1 the way `BigInteger(hex,
     * 16).toInt()` does.
     */
    fun decimalsErc20Decoder(hexDecimals: String): Int? {
        val encoded = hexDecimals.remove0x().hexToByteArrayOrNull() ?: return null
        // Answered before the decoder so the `0x` case — the one that used to throw — is reachable
        // without the JNI library, and so unit-testable off-device.
        if (encoded.isEmpty()) return null
        val fn = EthereumAbiFunction("decimals")
        fn.addParamUInt8(0, true)
        if (!EthereumAbi.decodeOutput(fn, encoded)) return null
        return fn.getParamUInt8(0, true).toInt() and 0xFF
    }

    fun withdrawCircleMSCA(vaultAddress: String, tokenAddress: String, amount: BigInteger): String {
        require(amount >= BigInteger.ZERO) { "Amount must be non-negative" }
        require(vaultAddress.isNotBlank()) { "Vault address cannot be blank" }
        require(tokenAddress.isNotBlank()) { "MSCA (token) address cannot be blank" }

        try {
            // Inner call: ERC20.transfer(vaultAddress, amount)
            val erc20TransferData =
                transferErc20Encoder(vaultAddress, amount).remove0x().decodeHex().toByteArray()

            val tokenAddr = AnyAddress(tokenAddress, CoinType.ETHEREUM)

            // execute(address _to, uint256 _value, bytes _data)
            val executeFn =
                EthereumAbiFunction("execute").apply {
                    addParamAddress(tokenAddr.data(), false)
                    addParamUInt256(ByteArray(32), false)
                    addParamBytes(erc20TransferData, false)
                }

            return EthereumAbi.encode(executeFn).toHexString().add0x()
        } catch (e: Exception) {
            throw IllegalArgumentException("Failed to encode Circle MSCA withdraw: ${e.message}", e)
        }
    }
}
