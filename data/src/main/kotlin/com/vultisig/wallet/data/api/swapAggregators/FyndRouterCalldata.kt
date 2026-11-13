package com.vultisig.wallet.data.api.swapAggregators

import java.math.BigInteger

/**
 * The parts of a TychoRouterV3 `singleSwap` / `sequentialSwap` / `splitSwap` call that decide where
 * the funds go: amounts, tokens, receiver and client fee. Decoded from calldata so a quote service
 * can't send metadata for one swap and calldata for another.
 */
internal data class FyndRouterCall(
    val amountIn: BigInteger,
    val tokenIn: String,
    val tokenOut: String,
    val expectedAmountOut: BigInteger,
    val minAmountOut: BigInteger,
    val receiver: String,
    /** Router fee units: 100,000,000 = 100%, so 1 bps = 10,000. */
    val clientFeeUnits: BigInteger,
    val clientFeeReceiver: String,
    val maxClientContribution: BigInteger,
    val clientFeeDeadline: BigInteger,
    val clientSignature: String,
    /** Byte offset of [clientSignature] in the calldata, selector included. */
    val clientSignatureOffset: Int,
)

internal object FyndRouterCalldata {

    const val FEE_UNITS_PER_BPS = 10_000

    // keccak256 of the TychoRouterV3 entrypoints with
    // ClientFeeParams = (uint32,address,uint256,uint256,bytes).
    private const val SINGLE_SWAP = "0c1a0ee7"
    private const val SEQUENTIAL_SWAP = "3c226834"
    private const val SPLIT_SWAP = "fe745a0f"

    fun decode(data: String): FyndRouterCall {
        val hex = data.removePrefix("0x").lowercase()
        require(hex.length >= 8 && hex.length % 2 == 0) { "Fynd calldata is malformed" }
        // splitSwap carries an extra `nTokens` word before the receiver.
        val shift =
            when (hex.substring(0, 8)) {
                SINGLE_SWAP,
                SEQUENTIAL_SWAP -> 0
                SPLIT_SWAP -> 1
                else -> throw IllegalArgumentException("Fynd calldata calls an unknown function")
            }
        val args = hex.substring(8)
        val feeTuple = offsetAt(args, 6 + shift)
        val signature = feeTuple + offsetAt(args, feeTuple / 32 + 4)
        val signatureLength =
            uint(args, signature / 32)
                .also { require(it < BigInteger.valueOf(args.length / 2L)) { "Fynd signature too long" } }
                .intValueExact()
        val signatureStart = signature + 32
        require((signatureStart + signatureLength) * 2 <= args.length) {
            "Fynd client fee signature runs past the calldata"
        }
        return FyndRouterCall(
            amountIn = uint(args, 0),
            tokenIn = address(args, 1),
            tokenOut = address(args, 2),
            expectedAmountOut = uint(args, 3),
            minAmountOut = uint(args, 4),
            receiver = address(args, 5 + shift),
            clientFeeUnits = uint(args, feeTuple / 32),
            clientFeeReceiver = address(args, feeTuple / 32 + 1),
            maxClientContribution = uint(args, feeTuple / 32 + 2),
            clientFeeDeadline = uint(args, feeTuple / 32 + 3),
            clientSignature = args.substring(signatureStart * 2, (signatureStart + signatureLength) * 2),
            clientSignatureOffset = 4 + signatureStart,
        )
    }

    private fun word(args: String, index: Int): String {
        val from = index * 64
        require(index >= 0 && from + 64 <= args.length) { "Fynd calldata is too short" }
        return args.substring(from, from + 64)
    }

    private fun uint(args: String, index: Int) = BigInteger(word(args, index), 16)

    private fun address(args: String, index: Int): String {
        val w = word(args, index)
        require(w.startsWith("0".repeat(24))) { "Fynd calldata has a malformed address" }
        return "0x" + w.substring(24)
    }

    /** A head word holding a byte offset into the arguments; must be word-aligned. */
    private fun offsetAt(args: String, index: Int): Int {
        val offset = uint(args, index)
        require(offset.signum() >= 0 && offset < BigInteger.valueOf(args.length / 2L)) {
            "Fynd calldata offset out of range"
        }
        return offset.intValueExact().also { require(it % 32 == 0) { "Fynd offset unaligned" } }
    }
}
