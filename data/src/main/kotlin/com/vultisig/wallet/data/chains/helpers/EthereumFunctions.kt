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
     * Decodes the result of an ERC-20 `symbol()` `eth_call` into the ticker.
     *
     * The standard return is an ABI dynamic `string`, read through the ABI decoder so the head
     * offset is honoured rather than assumed to be 32. Legacy DSToken-style contracts (mainnet MKR,
     * SAI) declare `symbol()` as `bytes32` instead and answer a single 32-byte word, which the
     * string decoder rejects; that word is read as zero-padded ASCII. Either text then goes through
     * [decodeBytes32HexOrSelf] for the bridged deployments that re-encode the `bytes32` as a hex
     * `string`.
     *
     * Returns null when no ticker decodes: `0x` from a contract without `symbol()` (or an address
     * that is not a contract at all), non-hex, a result that is neither a well-formed `string` nor
     * a 32-byte word, a `bytes32` holding no printable text, or a blank `string`.
     */
    fun symbolErc20Decoder(hexSymbol: String): String? {
        val encoded = hexSymbol.remove0x().hexToByteArrayOrNull() ?: return null
        // Answered before the decoder so the `0x` case is reachable without the JNI library, and so
        // unit-testable off-device.
        if (encoded.isEmpty()) return null
        val fn = EthereumAbiFunction("symbol")
        fn.addParamString("", true)
        val text =
            if (EthereumAbi.decodeOutput(fn, encoded)) fn.getParamString(0, true)
            else encoded.bytes32TextOrNull() ?: return null
        return text.decodeBytes32HexOrSelf().takeIf { it.isNotBlank() }
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

/**
 * Reads a 32-byte ABI word as the zero-padded ASCII text a `bytes32` name/symbol holds. Returns
 * null when it is not one: any other size, all zeros, or a byte outside printable ASCII before the
 * first zero.
 */
internal fun ByteArray.bytes32TextOrNull(): String? {
    if (size != 32) return null
    val text = takeWhile { it.toInt() != 0 }
    if (text.isEmpty() || text.any { it.toInt() !in 0x20..0x7E }) return null
    return String(text.toByteArray(), Charsets.US_ASCII)
}

/**
 * Decodes a value that is the 64-char hex of a `bytes32` (right-padded with zeros) back to text,
 * trimming the zero padding. Returns the receiver unchanged when it is not such a value — so it is
 * safe to apply to any name/symbol string (a normal ticker like `MKR` passes straight through, and
 * so does a genuine 64-hex-char symbol whose bytes are not printable text). Some legacy tokens
 * (e.g. MKR) declare `name()`/`symbol()` as `bytes32`; bridged deployments re-encode that as a
 * `string` whose content is the hex of the original word, which would otherwise surface to the UI
 * as raw hex (`MKR` → `4d4b52…00`). Used both for ABI `eth_call` results
 * ([EthereumFunction.symbolErc20Decoder]) and for aggregator token metadata that already arrives as
 * the bare `bytes32` hex (issue #4873).
 */
internal fun String.decodeBytes32HexOrSelf(): String {
    if (length != 64 || any { it !in '0'..'9' && it !in 'a'..'f' && it !in 'A'..'F' }) return this
    return hexToByteArrayOrNull()?.bytes32TextOrNull() ?: this
}
