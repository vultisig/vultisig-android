package com.vultisig.wallet.data.chains.helpers

import com.vultisig.wallet.data.common.remove0x
import com.vultisig.wallet.data.models.Chain
import java.math.BigInteger

/**
 * Minimal [Multicall3](https://github.com/mds1/multicall) calldata builder/decoder used to collapse
 * many EVM balance reads (native + each ERC-20) for one wallet into a single `eth_call`.
 *
 * Only the `aggregate3((address,bool,bytes)[])` entry point is implemented — enough for balance
 * batching. Every sub-call is encoded with `allowFailure = true`, so a single bad/garbage token
 * contract decodes to [BigInteger.ZERO] without failing its siblings (matching the per-token
 * fallback). ABI (de)coding is hand-rolled so this stays JNI-free and unit-testable.
 */
object Multicall3 {

    /** Canonical CREATE2 deployment address, identical on every chain in [addressFor]. */
    const val CANONICAL_ADDRESS = "0xcA11bde05977b3631167028862bE2a173976CA11"

    // 4-byte function selectors (keccak256 of the signature, first 4 bytes).
    private const val AGGREGATE3_SELECTOR = "82ad56cb" // aggregate3((address,bool,bytes)[])
    private const val GET_ETH_BALANCE_SELECTOR = "4d2301cc" // getEthBalance(address)
    private const val BALANCE_OF_SELECTOR = "70a08231" // balanceOf(address)

    private const val HEX_PER_WORD = 64 // 32 bytes
    private const val BYTES_PER_WORD = 32

    /**
     * Returns the Multicall3 address for [chain] when it is verified deployed at
     * [CANONICAL_ADDRESS], or `null` to signal that the caller must fall back to per-token
     * `balanceOf` calls.
     *
     * Conservative by design — chains absent here (e.g. zkSync Era, which deploys Multicall3 at a
     * non-canonical address, and any chain we have not confirmed such as Hyperliquid) fall back
     * rather than risk an `eth_call` to a non-existent contract.
     */
    fun addressFor(chain: Chain): String? =
        when (chain) {
            Chain.Ethereum,
            Chain.BscChain,
            Chain.Avalanche,
            Chain.Base,
            Chain.Arbitrum,
            Chain.Polygon,
            Chain.Optimism,
            Chain.Mantle,
            Chain.Blast,
            Chain.CronosChain,
            Chain.Sei -> CANONICAL_ADDRESS
            else -> null
        }

    data class Call3(val target: String, val callData: String)

    data class Result(val success: Boolean, val returnData: String)

    /** One [Call3] invoking `Multicall3.getEthBalance(address)` (native balance of [address]). */
    fun getEthBalanceCall(multicallAddress: String, address: String): Call3 =
        Call3(multicallAddress, "0x$GET_ETH_BALANCE_SELECTOR${pad32(address)}")

    /** One [Call3] invoking `ERC20(token).balanceOf(address)`. */
    fun balanceOfCall(token: String, address: String): Call3 =
        Call3(token, "0x$BALANCE_OF_SELECTOR${pad32(address)}")

    /** ABI-encodes `aggregate3(Call3[])` with `allowFailure = true` for every call. */
    fun encodeAggregate3(calls: List<Call3>): String {
        // Outer param is a dynamic array: head holds the offset to its data (always 0x20).
        val body = StringBuilder()
        body.append(word(BigInteger.valueOf(32)))
        body.append(word(calls.size.toBigInteger()))

        // Each Call3 is a dynamic tuple (it carries `bytes`), so the array's element region is a
        // head of N offsets followed by the encoded tuples.
        val tuples = calls.map { encodeCall3Tuple(it) }
        val heads = StringBuilder()
        val tails = StringBuilder()
        var tailOffset = tuples.size * BYTES_PER_WORD
        for (tuple in tuples) {
            heads.append(word(tailOffset.toBigInteger()))
            tails.append(tuple)
            tailOffset += tuple.length / 2
        }
        return "0x$AGGREGATE3_SELECTOR$body$heads$tails"
    }

    /** Decodes the `(bool success, bytes returnData)[]` returned by `aggregate3`. */
    fun decodeAggregate3(result: String): List<Result> {
        val hex = result.remove0x()
        val arrayOffset = intWordAt(hex, 0)
        val count = intWordAt(hex, arrayOffset)
        val elementBase = arrayOffset + BYTES_PER_WORD

        return (0 until count).map { i ->
            val tupleStart = elementBase + intWordAt(hex, elementBase + i * BYTES_PER_WORD)
            val success = intWordAt(hex, tupleStart) != 0
            val bytesStart = tupleStart + intWordAt(hex, tupleStart + BYTES_PER_WORD)
            val length = intWordAt(hex, bytesStart)
            val dataStart = (bytesStart + BYTES_PER_WORD) * 2
            val returnData = hex.substring(dataStart, dataStart + length * 2)
            Result(success = success, returnData = "0x$returnData")
        }
    }

    /**
     * Decodes a single left-padded uint256 return word (e.g. a `balanceOf` result), or null when
     * the word is empty/malformed. A successful sub-call that carries no decodable data is a failed
     * read, not a genuine zero (a real zero balance is a full 32-byte zero word) — returning null
     * lets the caller omit it and keep the cached balance rather than persist a fake 0 (#5308).
     */
    fun decodeUint256WordOrNull(returnData: String): BigInteger? {
        val cleaned = returnData.removePrefix("0x")
        // Only a full 32-byte word is a valid uint256 result. A short/truncated word (e.g. a 4-byte
        // "0x0000000a") is malformed data — omit it (failed read) rather than decode it to a bogus
        // balance, matching the per-token `balanceErc20Decoder`, which rejects the same bytes.
        return if (cleaned.length != HEX_PER_WORD) null else cleaned.toBigIntegerOrNull(16)
    }

    private fun encodeCall3Tuple(call: Call3): String {
        val data = call.callData.remove0x()
        val dataByteLength = data.length / 2
        return buildString {
            append(pad32(call.target)) // address, left-padded
            append(word(BigInteger.ONE)) // allowFailure = true
            append(word(BigInteger.valueOf(96))) // offset to bytes within the tuple (3 words)
            append(word(dataByteLength.toBigInteger())) // bytes length
            append(padRight32(data)) // bytes data, right-padded to a word boundary
        }
    }

    /** Reads the 32-byte word at [byteOffset] as a (small) non-negative Int. */
    private fun intWordAt(hex: String, byteOffset: Int): Int {
        val start = byteOffset * 2
        return BigInteger(hex.substring(start, start + HEX_PER_WORD), 16).toInt()
    }

    private fun word(value: BigInteger): String = value.toString(16).padStart(HEX_PER_WORD, '0')

    private fun pad32(address: String): String =
        address.remove0x().lowercase().padStart(HEX_PER_WORD, '0')

    private fun padRight32(data: String): String {
        if (data.isEmpty()) return ""
        val remainder = data.length % HEX_PER_WORD
        return if (remainder == 0) data
        else data.padEnd(data.length + (HEX_PER_WORD - remainder), '0')
    }
}
