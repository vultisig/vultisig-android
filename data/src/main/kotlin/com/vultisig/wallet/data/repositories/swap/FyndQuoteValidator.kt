package com.vultisig.wallet.data.repositories.swap

import com.vultisig.wallet.data.api.errors.SwapException
import com.vultisig.wallet.data.api.models.quotes.FyndOrderQuoteJson
import com.vultisig.wallet.data.api.swapAggregators.FyndRouterCalldata
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
    val tokenIn: String,
    val tokenOut: String,
    val receiver: String,
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
    private const val MAX_ROUTER_FEE_BPS = 10

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
        // Fynd's own fee defaults to 0 bps; anything above its share of our client fee plus a
        // small negotiated rate would let a quote hide an unlimited minimum behind fees.
        val grossFeeBps = expected.fee?.grossBps ?: 0
        val routerShareOfClientFee =
            amountOut * grossFeeBps.toBigInteger() / BigInteger.valueOf(10_000) -
                fyndExpectedClientFee(amountOut, grossFeeBps)
        val maxRouterFee =
            routerShareOfClientFee +
                amountOut * MAX_ROUTER_FEE_BPS.toBigInteger() / BigInteger.valueOf(10_000)
        ensure(routerFee <= maxRouterFee) { "Fynd router_fee $routerFee is above $maxRouterFee" }
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
        }
        checkCalldata(tx.data, tx.clientFeeSignatureOffset, quote.clientFeeDeadline, amountOut, minReceived, expected)

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

    /** Binds the calldata the vault will sign to the swap that was asked for and just validated. */
    private fun checkCalldata(
        data: String,
        reportedSignatureOffset: Int?,
        reportedDeadline: Long?,
        amountOut: BigInteger,
        minReceived: BigInteger,
        expected: FyndQuoteExpectation,
    ) {
        val call =
            try {
                FyndRouterCalldata.decode(data)
            } catch (e: IllegalArgumentException) {
                throw SwapException.UnkownSwapError(e.message ?: "Fynd calldata is malformed")
            } catch (e: ArithmeticException) {
                throw SwapException.UnkownSwapError("Fynd calldata is malformed")
            }
        ensure(call.amountIn == expected.amountIn) { "Fynd calldata sells ${call.amountIn}" }
        ensure(call.tokenIn.equals(expected.tokenIn, ignoreCase = true)) {
            "Fynd calldata sells ${call.tokenIn}"
        }
        ensure(call.tokenOut.equals(expected.tokenOut, ignoreCase = true)) {
            "Fynd calldata buys ${call.tokenOut}"
        }
        ensure(call.receiver.equals(expected.receiver, ignoreCase = true)) {
            "Fynd calldata pays ${call.receiver}, not the vault"
        }
        ensure(call.expectedAmountOut == amountOut) { "Fynd calldata expects ${call.expectedAmountOut}" }
        ensure(call.minAmountOut == minReceived) { "Fynd calldata minimum ${call.minAmountOut}" }

        val fee = expected.fee
        if (fee == null) {
            ensure(call.clientFeeUnits.signum() == 0) { "Fynd calldata charges ${call.clientFeeUnits}" }
            return
        }
        ensure(call.clientFeeUnits == (fee.grossBps * FyndRouterCalldata.FEE_UNITS_PER_BPS).toBigInteger()) {
            "Fynd calldata fee ${call.clientFeeUnits} is not ${fee.grossBps} bps"
        }
        ensure(call.clientFeeReceiver.equals(fee.receiver, ignoreCase = true)) {
            "Fynd calldata fee receiver ${call.clientFeeReceiver} is not ${fee.receiver}"
        }
        ensure(call.maxClientContribution.signum() == 0) {
            "Fynd calldata lets the fee receiver subsidise slippage"
        }
        ensure(reportedDeadline != null && call.clientFeeDeadline == reportedDeadline.toBigInteger()) {
            "Fynd calldata deadline ${call.clientFeeDeadline} is not the reported $reportedDeadline"
        }
        ensure(call.clientSignature.length == SIGNATURE_BYTES * 2 && call.clientSignature.any { it != '0' }) {
            "Fynd client fee signature is missing or still the zero placeholder"
        }
        ensure(reportedSignatureOffset == null || reportedSignatureOffset == call.clientSignatureOffset) {
            "Fynd signature offset $reportedSignatureOffset disagrees with the calldata"
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
