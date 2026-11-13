package com.vultisig.wallet.data.repositories.swap

import com.vultisig.wallet.data.api.errors.SwapException
import com.vultisig.wallet.data.api.models.quotes.FyndOrderQuoteJson
import com.vultisig.wallet.data.api.swapAggregators.fyndStatusException
import java.math.BigDecimal
import java.math.BigInteger

/** Fynd keeps 20% of the client fee: request the gross bps that nets [netBps] for Vultisig. */
internal fun fyndGrossClientFeeBps(netBps: Int): Int {
    require(netBps >= 0) { "negative fee bps $netBps" }
    return (netBps * 5 + 3) / 4
}

/**
 * The integrator share of a Fynd client fee, with the router's on-chain `FeeCalculator` integer
 * arithmetic: `amountOut·bps/10⁴ − amountOut·bps·2000/10⁸`.
 */
internal fun fyndExpectedClientFee(amountOut: BigInteger, grossBps: Int): BigInteger {
    val bps = grossBps.toBigInteger()
    val total = amountOut * bps / BigInteger.valueOf(10_000)
    val routerShare = amountOut * bps * BigInteger.valueOf(2_000) / BigInteger.valueOf(100_000_000)
    return total - routerShare
}

internal data class FyndExpectedFee(val grossBps: Int, val receiver: String)

/** What the app asked Fynd for; every field of a quote is checked against it before keysign. */
internal data class FyndQuoteExpectation(
    val router: String,
    val amountIn: BigInteger,
    val sellsNative: Boolean,
    val slippageBps: Int,
    val fee: FyndExpectedFee?,
    val nowEpochSeconds: Long,
)

internal data class ValidatedFyndQuote(
    val amountOut: BigInteger,
    val routerFee: BigInteger,
    val clientFee: BigInteger,
    val minAmountReceived: BigInteger,
    val to: String,
    val value: BigInteger,
    val data: String,
    val gasEstimate: Long,
    val gasPrice: String,
) {
    val amountAfterFees: BigInteger
        get() = amountOut - routerFee - clientFee
}

internal object FyndQuoteValidator {

    // Keysign across devices takes a while; a fee signature that expires sooner reverts on-chain.
    const val MIN_FEE_DEADLINE_SECONDS = 15 * 60L
    private const val SIGNATURE_BYTES = 65

    fun validate(quote: FyndOrderQuoteJson, expected: FyndQuoteExpectation): ValidatedFyndQuote {
        if (quote.status != "success") throw fyndStatusException(quote.status)
        val tx = invalid(quote.transaction, "Fynd quote has no transaction")
        val fees = invalid(quote.feeBreakdown, "Fynd quote has no fee breakdown")

        ensure(tx.to.equals(expected.router, ignoreCase = true)) {
            "Fynd router ${tx.to} is not the pinned router ${expected.router}"
        }
        val amountIn = int(quote.amountIn, "amount_in")
        ensure(amountIn == expected.amountIn) {
            "Fynd quoted amount_in $amountIn, requested ${expected.amountIn}"
        }
        val value = int(tx.value, "transaction.value")
        val expectedValue = if (expected.sellsNative) expected.amountIn else BigInteger.ZERO
        ensure(value == expectedValue) { "Fynd tx.value $value, expected $expectedValue" }

        val amountOut = int(quote.amountOut, "amount_out")
        val routerFee = int(fees.routerFee, "router_fee")
        val clientFee = int(fees.clientFee, "client_fee")
        val minReceived = int(fees.minAmountReceived, "min_amount_received")
        val afterFees = amountOut - routerFee - clientFee
        ensure(minReceived.signum() > 0 && minReceived <= afterFees) {
            "Fynd min_amount_received $minReceived outside (0, $afterFees]"
        }
        val slippageFloor =
            afterFees * (10_000 - expected.slippageBps).toBigInteger() / BigInteger.valueOf(10_000)
        ensure(minReceived >= slippageFloor - BigInteger.ONE) {
            "Fynd min_amount_received $minReceived below the ${expected.slippageBps} bps floor"
        }

        val fee = expected.fee
        if (fee == null) {
            ensure(clientFee.signum() == 0) { "Fynd charged an unrequested client fee $clientFee" }
        } else {
            val wanted = fyndExpectedClientFee(amountOut, fee.grossBps)
            ensure(clientFee == wanted) { "Fynd client_fee $clientFee, expected $wanted" }
            ensure(quote.clientFeeReceiver.equals(fee.receiver, ignoreCase = true)) {
                "Fynd fee receiver ${quote.clientFeeReceiver} is not ${fee.receiver}"
            }
            val deadline = invalid(quote.clientFeeDeadline, "Fynd fee quote has no deadline")
            ensure(deadline >= expected.nowEpochSeconds + MIN_FEE_DEADLINE_SECONDS) {
                "Fynd fee signature expires too soon ($deadline)"
            }
            checkSignaturePatched(tx.data, tx.clientFeeSignatureOffset)
        }

        return ValidatedFyndQuote(
            amountOut = amountOut,
            routerFee = routerFee,
            clientFee = clientFee,
            minAmountReceived = minReceived,
            to = tx.to,
            value = value,
            data = tx.data,
            gasEstimate = quote.gasEstimate.toLongOrNull() ?: 0L,
            gasPrice = quote.gasPrice?.takeIf { it.toBigIntegerOrNull() != null } ?: "0",
        )
    }

    private fun checkSignaturePatched(data: String, offset: Int?) {
        val start = invalid(offset, "Fynd fee quote has no signature offset")
        val hex = data.removePrefix("0x")
        val from = start * 2
        val to = from + SIGNATURE_BYTES * 2
        ensure(start >= 0 && to <= hex.length) { "Fynd signature offset $start out of calldata" }
        ensure(hex.substring(from, to).any { it != '0' }) {
            "Fynd client fee signature is still the zero placeholder"
        }
    }

    private fun int(value: String, field: String): BigInteger =
        value.toBigIntegerOrNull()?.takeIf { it.signum() >= 0 }
            ?: throw SwapException.UnkownSwapError("Fynd $field '$value' is not a non-negative integer")

    private fun <T : Any> invalid(value: T?, message: String): T =
        value ?: throw SwapException.UnkownSwapError(message)

    private inline fun ensure(condition: Boolean, message: () -> String) {
        if (!condition) throw SwapException.UnkownSwapError(message())
    }
}

/** A bps slippage as the decimal fraction string Fynd expects ("0.005" for 50 bps). */
internal fun fyndSlippage(bps: Int): String =
    BigDecimal(bps).movePointLeft(4).stripTrailingZeros().toPlainString()
