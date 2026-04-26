package com.vultisig.wallet.data.api.errors

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

class HandleSwapExceptionTest {

    @Test
    fun `slippage error maps to HighPriceImpact`() {
        val result = SwapException.handleSwapException("Slippage tolerance exceeded for this trade")
        assertInstanceOf(SwapException.HighPriceImpact::class.java, result)
    }

    @Test
    fun `price impact error maps to HighPriceImpact`() {
        val result = SwapException.handleSwapException("Price impact too high: 15.2%")
        assertInstanceOf(SwapException.HighPriceImpact::class.java, result)
    }

    @Test
    fun `exceeds desired slippage maps to HighPriceImpact`() {
        val result = SwapException.handleSwapException("Transaction exceeds desired slippage limit")
        assertInstanceOf(SwapException.HighPriceImpact::class.java, result)
    }

    @Test
    fun `route not profitable maps to HighPriceImpact`() {
        val result = SwapException.handleSwapException("Route not profitable after fees")
        assertInstanceOf(SwapException.HighPriceImpact::class.java, result)
    }

    @Test
    fun `case insensitive slippage matching`() {
        val result = SwapException.handleSwapException("SLIPPAGE EXCEEDED")
        assertInstanceOf(SwapException.HighPriceImpact::class.java, result)
    }

    @Test
    fun `unknown error maps to UnkownSwapError`() {
        val result = SwapException.handleSwapException("some random API error")
        assertInstanceOf(SwapException.UnkownSwapError::class.java, result)
    }

    @Test
    fun `unknown error preserves original message`() {
        val msg = "Jupiter: internal server error 500"
        val result = SwapException.handleSwapException(msg)
        assertInstanceOf(SwapException.UnkownSwapError::class.java, result)
        assertEquals(msg, result.message)
    }

    @Test
    fun `timeout maps to TimeOut`() {
        val result = SwapException.handleSwapException("Request timeout after 30s")
        assertInstanceOf(SwapException.TimeOut::class.java, result)
    }

    @Test
    fun `insufficient funds maps correctly`() {
        val result = SwapException.handleSwapException("Insufficient funds for transfer")
        assertInstanceOf(SwapException.InsufficientFunds::class.java, result)
    }

    @Test
    fun `no available quotes maps to SwapRouteNotAvailable`() {
        val result = SwapException.handleSwapException("No available quotes for the requested swap")
        assertInstanceOf(SwapException.SwapRouteNotAvailable::class.java, result)
    }

    @Test
    fun `trading halted maps to SwapRouteNotAvailable`() {
        val result = SwapException.handleSwapException("Trading is halted for this pool")
        assertInstanceOf(SwapException.SwapRouteNotAvailable::class.java, result)
    }

    @Test
    fun `pool does not exist maps to SwapRouteNotAvailable`() {
        val result = SwapException.handleSwapException("Pool does not exist")
        assertInstanceOf(SwapException.SwapRouteNotAvailable::class.java, result)
    }

    @Test
    fun `amount less than min swap maps to SmallSwapAmount`() {
        val result = SwapException.handleSwapException("amount less than min swap amount: 10000")
        assertInstanceOf(SwapException.SmallSwapAmount::class.java, result)
    }

    @Test
    fun `dust threshold error maps to AmountBelowDustThreshold`() {
        val result = SwapException.handleSwapException("amount less than dust threshold")
        assertInstanceOf(SwapException.AmountBelowDustThreshold::class.java, result)
    }

    @Test
    fun `dust threshold error is case insensitive`() {
        val result = SwapException.handleSwapException("Amount Less Than Dust Threshold")
        assertInstanceOf(SwapException.AmountBelowDustThreshold::class.java, result)
    }

    @Test
    fun `network error maps to NetworkConnection`() {
        val result = SwapException.handleSwapException("Unable to resolve host api.example.com")
        assertInstanceOf(SwapException.NetworkConnection::class.java, result)
    }

    @Test
    fun `zero amount maps to AmountCannotBeZero`() {
        val result = SwapException.handleSwapException("Amount cannot be zero")
        assertInstanceOf(SwapException.AmountCannotBeZero::class.java, result)
    }

    @Test
    fun `too many requests maps to RateLimitExceeded`() {
        val result = SwapException.handleSwapException("[Jupiter] Too many requests")
        assertInstanceOf(SwapException.RateLimitExceeded::class.java, result)
    }

    @Test
    fun `gateway too many requests maps to RateLimitExceeded`() {
        val result = SwapException.handleSwapException("[API Gateway] Too many requests")
        assertInstanceOf(SwapException.RateLimitExceeded::class.java, result)
    }
}
