package com.vultisig.wallet.data.blockchain.solana.kamino

import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * The numbers below are live values read from `api.kamino.finance` for the three launch vaults, so
 * these tests pin the arithmetic against real magnitudes rather than round invented ones.
 */
class KaminoPositionMathTest {

    private fun assertSameValue(expected: String, actual: BigDecimal?) {
        val expectedDecimal = BigDecimal(expected)
        assertTrue(
            actual != null && actual.compareTo(expectedDecimal) == 0,
            "expected $expected but was $actual",
        )
    }

    @Test
    fun `apy30d is a fraction and renders as a percentage`() {
        // Rendering the raw fraction would report a 4% vault as 0.04%.
        assertSameValue(
            "4.00",
            KaminoPositionMath.apyPercent(BigDecimal("0.039967764404019690278")),
        )
        assertSameValue(
            "5.88",
            KaminoPositionMath.apyPercent(BigDecimal("0.058775543215515951449")),
        )
        assertSameValue(
            "5.14",
            KaminoPositionMath.apyPercent(BigDecimal("0.05143926373911317205987")),
        )
    }

    @Test
    fun `a zero apy stays zero rather than becoming null or blank`() {
        assertSameValue("0.00", KaminoPositionMath.apyPercent(BigDecimal.ZERO))
    }

    @Test
    fun `token amount is shares times tokensPerShare at the token's own precision`() {
        // Steakhouse USDC: 6-decimal token, tokensPerShare just above 1.
        assertSameValue(
            "1054.427822",
            KaminoPositionMath.tokenAmount(
                shares = BigDecimal("1000"),
                tokensPerShare = BigDecimal("1.0544278224860290217"),
                tokenDecimals = 6,
            ),
        )
    }

    @Test
    fun `token amount honours a share scale that differs from the token scale`() {
        // Allez SOL issues 6-decimal shares against a 9-decimal token, so tokensPerShare is ~0.001.
        // Assuming the two scales match would misprice this position by a factor of a thousand.
        assertSameValue(
            "1.075761685",
            KaminoPositionMath.tokenAmount(
                shares = BigDecimal("1000"),
                tokensPerShare = BigDecimal("0.0010757616854425424673"),
                tokenDecimals = 9,
            ),
        )
    }

    @Test
    fun `token amount rounds down so a balance is never shown larger than it is`() {
        // 0.9999995 at 6 decimals must not become 1.000000 — the extra would not be withdrawable.
        assertSameValue(
            "0.999999",
            KaminoPositionMath.tokenAmount(
                shares = BigDecimal("1"),
                tokensPerShare = BigDecimal("0.9999995"),
                tokenDecimals = 6,
            ),
        )
    }

    @Test
    fun `an empty position is worth zero rather than failing`() {
        assertSameValue(
            "0.000000",
            KaminoPositionMath.tokenAmount(
                shares = BigDecimal.ZERO,
                tokensPerShare = BigDecimal("1.0544278224860290217"),
                tokenDecimals = 6,
            ),
        )
    }

    @Test
    fun `minimums convert from base units to decimal amounts`() {
        // The only Kamino field that is not already a decimal string.
        assertSameValue("0.1", KaminoPositionMath.baseUnitsToDecimal("100000", 6))
        assertSameValue("0.01", KaminoPositionMath.baseUnitsToDecimal("10000000", 9))
        assertSameValue("0.001", KaminoPositionMath.baseUnitsToDecimal("1000", 6))
        assertNull(KaminoPositionMath.baseUnitsToDecimal(null, 6))
    }

    @Test
    fun `deposit minimum gets a proportional margin so the advertised figure actually clears`() {
        // 100000 base units: 1/1000 margin is 100, rounded up — above the 16 floor.
        assertSameValue("0.1001", KaminoPositionMath.depositMinimumWithMargin("100000", 6))
        // 1000 base units: 1/1000 margin is 1, rounded up — the 16 floor applies instead.
        assertSameValue("0.001016", KaminoPositionMath.depositMinimumWithMargin("1000", 6))
        assertNull(KaminoPositionMath.depositMinimumWithMargin(null, 6))
    }

    @Test
    fun `deposit minimum rejects negative or fractional base units`() {
        // A negative or fractional minimum is not a valid base-unit amount — treat it as missing
        // rather than let it silently pass every deposit's minimum check.
        assertNull(KaminoPositionMath.depositMinimumWithMargin("-100000", 6))
        assertNull(KaminoPositionMath.depositMinimumWithMargin("1000.5", 6))
    }

    @Test
    fun `absent and malformed numbers read as missing, never as zero`() {
        // A row showing nothing is recoverable; a balance rendered as 0 reads as a lost deposit.
        assertNull(KaminoPositionMath.decimalOrNull(null))
        assertNull(KaminoPositionMath.decimalOrNull(""))
        assertNull(KaminoPositionMath.decimalOrNull("   "))
        assertNull(KaminoPositionMath.decimalOrNull("n/a"))
        assertNull(KaminoPositionMath.decimalOrNull("1.2.3"))
    }

    @Test
    fun `well-formed numbers parse without losing precision`() {
        val raw = "19766255.285929336591"
        assertEquals(BigDecimal(raw), KaminoPositionMath.decimalOrNull(raw))
        assertSameValue("0", KaminoPositionMath.decimalOrNull("0"))
    }
}
