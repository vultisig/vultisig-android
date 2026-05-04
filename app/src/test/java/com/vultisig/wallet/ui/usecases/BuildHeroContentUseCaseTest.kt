package com.vultisig.wallet.ui.usecases

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.securityscanner.blockaid.BlockaidSimulationCoin
import com.vultisig.wallet.data.securityscanner.blockaid.BlockaidSimulationInfo
import com.vultisig.wallet.ui.components.hero.HeroContent
import java.math.BigInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class BuildHeroContentUseCaseTest {

    private val build = BuildHeroContentUseCase()

    @Test
    fun `transfer simulation maps to send hero with formatted amount`() {
        val coin =
            BlockaidSimulationCoin(
                chain = Chain.Ethereum,
                address = "0xUSDC",
                ticker = "USDC",
                logo = "logo.png",
                decimals = 6,
            )
        val sim =
            BlockaidSimulationInfo.Transfer(
                fromCoin = coin,
                fromAmount = BigInteger("250000000"), // 250 USDC
            )

        val hero =
            build(simulation = sim, decodedFunctionName = "Approve", didLoadSimulation = true)

        val send = hero as HeroContent.Send
        assertEquals("Approve", send.title)
        assertEquals("250", send.coin.amount)
        assertEquals("USDC", send.coin.ticker)
    }

    @Test
    fun `swap simulation maps to swap hero with both sides`() {
        val ethCoin =
            BlockaidSimulationCoin(
                chain = Chain.Ethereum,
                address = null,
                ticker = "ETH",
                logo = "",
                decimals = 18,
            )
        val usdcCoin =
            BlockaidSimulationCoin(
                chain = Chain.Ethereum,
                address = "0xUSDC",
                ticker = "USDC",
                logo = "u.png",
                decimals = 6,
            )
        val sim =
            BlockaidSimulationInfo.Swap(
                fromCoin = ethCoin,
                toCoin = usdcCoin,
                fromAmount = BigInteger("1000000000000000000"), // 1 ETH
                toAmount = BigInteger("3150000000"), // 3,150 USDC
            )

        val swap =
            build(simulation = sim, decodedFunctionName = "Swap", didLoadSimulation = true)
                as HeroContent.Swap

        assertEquals("Swap", swap.title)
        assertEquals("1", swap.from.amount)
        assertEquals("3150", swap.to.amount)
    }

    @Test
    fun `no simulation but loaded with function name renders Unverified`() {
        // The decoded function name (e.g. "Pause") is intentionally NOT shown
        // as the hero title — it would read like the user's intended action.
        // The hero shows the localized "Unverified function" instead, and the
        // decoded name lives in the function-signature row below.
        val hero =
            build(simulation = null, decodedFunctionName = "Approve", didLoadSimulation = true)

        assertSame(HeroContent.Unverified, hero)
    }

    @Test
    fun `no simulation and no function name returns null`() {
        val hero = build(simulation = null, decodedFunctionName = null, didLoadSimulation = true)

        assertNull(hero)
    }

    @Test
    fun `simulation not loaded yet returns null even with function name`() {
        // Pre-load tick: we don't want to flash "Unverified function" before
        // the simulation has even had a chance to come back. Caller should
        // render something benign (the existing title/native amount fallback).
        val hero =
            build(simulation = null, decodedFunctionName = "Approve", didLoadSimulation = false)

        assertNull(hero)
    }

    @Test
    fun `formatted amount strips trailing zeros for round numbers`() {
        val coin = simCoin(decimals = 18, ticker = "ETH")
        val sim = BlockaidSimulationInfo.Transfer(coin, BigInteger("1000000000000000000"))

        val hero =
            build(simulation = sim, decodedFunctionName = null, didLoadSimulation = true)
                as HeroContent.Send

        assertEquals("1", hero.coin.amount)
    }

    @Test
    fun `fractional amount preserves precision`() {
        val coin = simCoin(decimals = 6, ticker = "USDC")
        // 1.234567 USDC == 1_234_567 base units
        val sim = BlockaidSimulationInfo.Transfer(coin, BigInteger("1234567"))

        val hero =
            build(simulation = sim, decodedFunctionName = null, didLoadSimulation = true)
                as HeroContent.Send

        assertEquals("1.234567", hero.coin.amount)
    }

    @Test
    fun `zero amount formats to plain zero`() {
        val coin = simCoin(decimals = 18, ticker = "ETH")
        val sim = BlockaidSimulationInfo.Transfer(coin, BigInteger.ZERO)

        val hero =
            build(simulation = sim, decodedFunctionName = null, didLoadSimulation = true)
                as HeroContent.Send

        assertEquals("0", hero.coin.amount)
    }

    @Test
    fun `single wei amount with eighteen decimals does not strip to zero`() {
        val coin = simCoin(decimals = 18, ticker = "ETH")
        val sim = BlockaidSimulationInfo.Transfer(coin, BigInteger.ONE)

        val hero =
            build(simulation = sim, decodedFunctionName = null, didLoadSimulation = true)
                as HeroContent.Send

        assertEquals("0.000000000000000001", hero.coin.amount)
    }

    @Test
    fun `single base unit with twenty four decimals does not silently round to zero`() {
        // Defends against the previous formatter that capped the scale at 18 fractional digits
        // and silently rounded sub-18-decimal amounts to "0" for high-decimal tokens. The
        // formatter now grows the scale to match the token's own decimals, preserving precision.
        val coin = simCoin(decimals = 24, ticker = "FOO")
        val sim = BlockaidSimulationInfo.Transfer(coin, BigInteger.ONE)

        val hero =
            build(simulation = sim, decodedFunctionName = null, didLoadSimulation = true)
                as HeroContent.Send

        assertEquals("0.000000000000000000000001", hero.coin.amount)
    }

    @Test
    fun `decimals zero produces a plain integer`() {
        val coin = simCoin(decimals = 0, ticker = "BTC")
        val sim = BlockaidSimulationInfo.Transfer(coin, BigInteger("42"))

        val hero =
            build(simulation = sim, decodedFunctionName = null, didLoadSimulation = true)
                as HeroContent.Send

        assertEquals("42", hero.coin.amount)
    }

    @Test
    fun `MAX_UINT256 sentinel preserves all integer digits without scientific notation`() {
        // Defends against the previous MathContext-based formatter which would
        // round significant digits and clip the integer portion of huge values.
        // movePointLeft + setScale guarantees integer digits survive untouched
        // and toPlainString never falls back to exponent form.
        val coin = simCoin(decimals = 18, ticker = "USDC")
        val maxU256 =
            BigInteger(
                "115792089237316195423570985008687907853269984665640564039457584007913129639935"
            )
        val sim = BlockaidSimulationInfo.Transfer(coin, maxU256)

        val hero =
            build(simulation = sim, decodedFunctionName = "Approve", didLoadSimulation = true)
                as HeroContent.Send

        // 2^256 - 1 divided by 1e18, in plain decimal form: 60 integer
        // digits + 18 fractional digits, no exponent notation.
        assertEquals(
            "115792089237316195423570985008687907853269984665640564039457.584007913129639935",
            hero.coin.amount,
        )
        assertFalse(hero.coin.amount.contains('E'), "amount must not use scientific notation")
        assertFalse(hero.coin.amount.contains('e'), "amount must not use scientific notation")
    }

    private fun simCoin(decimals: Int, ticker: String): BlockaidSimulationCoin =
        BlockaidSimulationCoin(
            chain = Chain.Ethereum,
            address = "0xT",
            ticker = ticker,
            logo = "",
            decimals = decimals,
        )
}
