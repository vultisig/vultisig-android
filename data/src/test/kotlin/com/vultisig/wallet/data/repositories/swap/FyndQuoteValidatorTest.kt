package com.vultisig.wallet.data.repositories.swap

import com.vultisig.wallet.data.api.errors.SwapException
import com.vultisig.wallet.data.api.models.quotes.FyndFeeBreakdownJson
import com.vultisig.wallet.data.api.models.quotes.FyndOrderQuoteJson
import com.vultisig.wallet.data.api.models.quotes.FyndTransactionJson
import io.kotest.assertions.throwables.shouldThrow
import java.math.BigInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class FyndQuoteValidatorTest {

    @Test
    fun `grosses each VULT tier's net fee up so Vultisig keeps it after Fynd's 20 percent`() {
        // Net bps = 50 minus the tier discount (none, bronze … diamond, ultimate).
        val netToGross = mapOf(50 to 63, 45 to 57, 40 to 50, 30 to 38, 25 to 32, 15 to 19, 0 to 0)
        netToGross.forEach { (net, gross) -> assertEquals(gross, fyndGrossClientFeeBps(net)) }
        netToGross.keys.forEach { net ->
            val kept = fyndExpectedClientFee(BigInteger.valueOf(10_000_000), fyndGrossClientFeeBps(net))
            // Never below the net fee, and over it by less than one gross bps of rounding.
            assert(kept >= BigInteger.valueOf(1_000L * net)) { "net $net kept $kept" }
            assert(kept < BigInteger.valueOf(1_000L * net + 1_000)) { "net $net kept $kept" }
        }
    }

    @Test
    fun `expected client fee follows the router's integer arithmetic`() {
        // Fynd's documented example: 1,000,000 out at 50 bps leaves 4,000 to the client.
        assertEquals(BigInteger.valueOf(4_000), fyndExpectedClientFee(BigInteger.valueOf(1_000_000), 50))
        assertEquals(BigInteger.ZERO, fyndExpectedClientFee(BigInteger.valueOf(1_000_000), 0))
    }

    @Test
    fun `formats slippage as the decimal fraction Fynd expects`() {
        assertEquals("0.005", fyndSlippage(50))
        assertEquals("0.01", fyndSlippage(100))
        assertEquals("0.0001", fyndSlippage(1))
    }

    @Test
    fun `accepts a fee-free native sale and reports the post-fee output`() {
        val quote = FyndQuoteValidator.validate(order(), expectation())
        assertEquals(BigInteger("1843983905"), quote.amountAfterFees)
        assertEquals(BigInteger("1834763986"), quote.minAmountReceived)
    }

    @Test
    fun `refuses a router other than the pinned one`() {
        shouldThrow<SwapException> {
            FyndQuoteValidator.validate(order(to = OTHER), expectation())
        }
    }

    @Test
    fun `refuses a different sell amount or native value`() {
        shouldThrow<SwapException> {
            FyndQuoteValidator.validate(order(amountIn = "1"), expectation())
        }
        shouldThrow<SwapException> {
            FyndQuoteValidator.validate(order(value = "2000000000000000000"), expectation())
        }
        shouldThrow<SwapException> {
            FyndQuoteValidator.validate(order(), expectation(sellsNative = false))
        }
    }

    @Test
    fun `refuses a minimum output looser than the requested slippage`() {
        shouldThrow<SwapException> {
            FyndQuoteValidator.validate(order(minReceived = "1800000000"), expectation())
        }
    }

    @Test
    fun `refuses an unrequested client fee`() {
        shouldThrow<SwapException> {
            FyndQuoteValidator.validate(order(clientFee = "1"), expectation())
        }
    }

    @Test
    fun `accepts a proxy-signed fee quote`() {
        FyndQuoteValidator.validate(feeOrder(), expectation(fee = FEE))
    }

    @Test
    fun `refuses a fee quote with the wrong amount, receiver, deadline or signature`() {
        listOf(
                feeOrder(clientFee = "1"),
                feeOrder(receiver = OTHER),
                feeOrder(deadline = NOW + 60),
                feeOrder(deadline = null),
                feeOrder(data = "0x" + "11".repeat(4) + "00".repeat(65)),
                feeOrder(offset = null),
                feeOrder(offset = 1_000),
            )
            .forEach { bad ->
                shouldThrow<SwapException> { FyndQuoteValidator.validate(bad, expectation(fee = FEE)) }
            }
    }

    @Test
    fun `maps Fynd failure statuses`() {
        shouldThrow<SwapException.SwapRouteNotAvailable> {
            FyndQuoteValidator.validate(order().copy(status = "no_route_found"), expectation())
        }
        shouldThrow<SwapException.TimeOut> {
            FyndQuoteValidator.validate(order().copy(status = "timeout"), expectation())
        }
        shouldThrow<SwapException.UnkownSwapError> {
            FyndQuoteValidator.validate(order().copy(status = "encoding_failed"), expectation())
        }
    }

    private fun expectation(sellsNative: Boolean = true, fee: FyndExpectedFee? = null) =
        FyndQuoteExpectation(
            router = ROUTER,
            amountIn = BigInteger("1000000000000000000"),
            sellsNative = sellsNative,
            slippageBps = 50,
            fee = fee,
            nowEpochSeconds = NOW,
        )

    // Fynd's documented encoded quote: 1 ETH → USDC, 0.5% slippage, no client fee.
    private fun order(
        to: String = ROUTER,
        amountIn: String = "1000000000000000000",
        value: String = "1000000000000000000",
        clientFee: String = "0",
        minReceived: String = "1834763986",
    ) =
        FyndOrderQuoteJson(
            status = "success",
            amountIn = amountIn,
            amountOut = "1844002345",
            gasEstimate = "219652",
            gasPrice = "108908625",
            transaction = FyndTransactionJson(to = to, value = value, data = "0xce25e49e"),
            feeBreakdown =
                FyndFeeBreakdownJson(
                    routerFee = "18440",
                    clientFee = clientFee,
                    maxSlippage = "9219919",
                    minAmountReceived = minReceived,
                ),
        )

    private fun feeOrder(
        clientFee: String = fyndExpectedClientFee(BigInteger.valueOf(AMOUNT_OUT), 63).toString(),
        receiver: String? = RECEIVER,
        deadline: Long? = NOW + 20 * 60,
        data: String = "0x" + "11".repeat(4) + "ab".repeat(65),
        offset: Int? = 4,
    ): FyndOrderQuoteJson {
        val afterFees = AMOUNT_OUT - 0 - clientFee.toLong()
        return FyndOrderQuoteJson(
            status = "success",
            amountIn = "1000000000000000000",
            amountOut = AMOUNT_OUT.toString(),
            gasEstimate = "219652",
            transaction =
                FyndTransactionJson(
                    to = ROUTER,
                    value = "1000000000000000000",
                    data = data,
                    clientFeeSignatureOffset = offset,
                ),
            feeBreakdown =
                FyndFeeBreakdownJson(
                    routerFee = "0",
                    clientFee = clientFee,
                    maxSlippage = (afterFees / 200).toString(),
                    minAmountReceived = (afterFees - afterFees / 200).toString(),
                ),
            clientFeeReceiver = receiver,
            clientFeeDeadline = deadline,
        )
    }

    private companion object {
        const val ROUTER = "0x1644d2477f809cc2c71bccfd6dc9497e3f83210d"
        const val OTHER = "0x00000000000000000000000000000000000000bb"
        const val RECEIVER = "0x00000000000000000000000000000000000000cc"
        const val NOW = 1_800_000_000L
        const val AMOUNT_OUT = 1_844_002_345L
        val FEE = FyndExpectedFee(grossBps = 63, receiver = RECEIVER)
    }
}
