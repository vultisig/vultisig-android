package com.vultisig.wallet.data.models

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import java.math.BigInteger
import org.junit.jupiter.api.Test

/**
 * The two conversions that stand between a stored XRPL token and its signed bytes: the on-ledger
 * currency code, and the decimal value string an amount travels as (issue #5212).
 */
class RippleIssuedCurrencyTest {

    @Test
    fun `keeps a standard three-character code exactly as written`() {
        toRippleCurrencyCode("USD") shouldBe "USD"
        toRippleCurrencyCode("  USD  ") shouldBe "USD"
    }

    @Test
    fun `packs a longer ticker into the 160-bit form`() {
        toRippleCurrencyCode("RLUSD") shouldBe RLUSD_HEX
    }

    @Test
    fun `upper-cases an already-encoded hex code`() {
        toRippleCurrencyCode(RLUSD_HEX.lowercase()) shouldBe RLUSD_HEX
    }

    // Both spellings of one token must collapse to a single code, or the same trust line would
    // yield two coins with different ids and two rows in the asset list.
    @Test
    fun `a ticker and its hex spelling normalise to the same code`() {
        toRippleCurrencyCode("RLUSD") shouldBe toRippleCurrencyCode(RLUSD_HEX.lowercase())
    }

    @Test
    fun `refuses a ticker too long for the 160-bit form`() {
        shouldThrow<IllegalArgumentException> { toRippleCurrencyCode("A".repeat(21)) }
    }

    // WalletCore upper-cases a 3-byte code before encoding it while the ledger compares those
    // bytes case-sensitively, so a lowercase code would sign a currency nobody reviewed.
    @Test
    fun `refuses a standard code the signer would alter`() {
        isSignableRippleCurrencyCode("USD") shouldBe true
        isSignableRippleCurrencyCode("usd") shouldBe false
        isSignableRippleCurrencyCode("UsD") shouldBe false
    }

    // rippled's own repertoire admits these, so the gate must not narrow to alphanumerics.
    @Test
    fun `accepts the symbols a standard code may draw on`() {
        "<>(){}[]|?!@#$%^&*"
            .forEach { symbol -> isSignableRippleCurrencyCode("A${symbol}1") shouldBe true }
    }

    @Test
    fun `refuses a standard code outside that repertoire`() {
        listOf("A-B", "A B", "A_B", "A.B", "ÁBC").forEach {
            isSignableRippleCurrencyCode(it) shouldBe false
        }
    }

    @Test
    fun `accepts an upper-case hex code and refuses its lower-case spelling`() {
        isSignableRippleCurrencyCode(RLUSD_HEX) shouldBe true
        isSignableRippleCurrencyCode(RLUSD_HEX.lowercase()) shouldBe false
    }

    @Test
    fun `refuses a code that is neither three characters nor forty hex digits`() {
        listOf("", "AB", "RLUSD", "A".repeat(39), "Z".repeat(40)).forEach {
            isSignableRippleCurrencyCode(it) shouldBe false
        }
    }

    @Test
    fun `renders units as a trimmed decimal string`() {
        BigInteger("1500000000000000").toRippleTokenValue(RIPPLE_TOKEN_DECIMALS) shouldBe "1.5"
        BigInteger.ZERO.toRippleTokenValue(RIPPLE_TOKEN_DECIMALS) shouldBe "0"
        BigInteger.ONE.toRippleTokenValue(RIPPLE_TOKEN_DECIMALS) shouldBe "0.000000000000001"
    }

    // Never scientific notation: XRPL accepts an exponent but the value is also what the verify
    // screen and a co-signer compare against, so it stays in the spelling a human can read.
    @Test
    fun `renders a large amount in plain notation`() {
        BigInteger.TEN.pow(30).toRippleTokenValue(RIPPLE_TOKEN_DECIMALS) shouldBe "1000000000000000"
    }

    // The value a co-signer reads off the wire has to be the reviewed amount, not a rounding of it.
    @Test
    fun `a full-precision amount survives the round trip to a value string and back`() {
        val amount = BigInteger("1234567890123456")

        val value = amount.toRippleTokenValue(RIPPLE_TOKEN_DECIMALS)

        value shouldBe "1.234567890123456"
        value.toRippleTokenUnits(RIPPLE_TOKEN_DECIMALS) shouldBe amount
    }

    // A quarter of a full-precision balance keeps 15 decimals and gains an integer part, which is
    // 17 significant digits — one more than the ledger carries, and the signer hard-errors on it.
    @Test
    fun `trims a fraction of a full-precision balance to what the ledger carries`() {
        val quarterOfBalance = BigInteger("62496839072869625")

        val trimmed = quarterOfBalance.toRepresentableRippleTokenUnits(RIPPLE_TOKEN_DECIMALS)

        trimmed.toRippleTokenValue(RIPPLE_TOKEN_DECIMALS) shouldBe "62.49683907286962"
        (trimmed < quarterOfBalance) shouldBe true
    }

    @Test
    fun `leaves an amount the ledger can already carry untouched`() {
        listOf(
                BigInteger.ZERO,
                BigInteger.ONE,
                BigInteger("1500000000000000"),
                // 16 significant digits, the ceiling itself.
                BigInteger("1234567890123456"),
                // The default trust-line limit: one significant digit followed by zeros.
                BigInteger.TEN.pow(30),
            )
            .forEach { it.toRepresentableRippleTokenUnits(RIPPLE_TOKEN_DECIMALS) shouldBe it }
    }

    // Trailing zeros are written digits, not significant ones, so a long-looking amount that the
    // ledger can carry exactly must come back untouched.
    @Test
    fun `counts significant digits rather than written ones`() {
        val amount = BigInteger("12").multiply(BigInteger.TEN.pow(33))

        amount.toRippleTokenValue(RIPPLE_TOKEN_DECIMALS) shouldBe "12000000000000000000"
        amount.toRepresentableRippleTokenUnits(RIPPLE_TOKEN_DECIMALS) shouldBe amount
    }

    // The scale is the coin's own and arrives relayed from a peer.
    @Test
    fun `refuses a scale outside the supported range`() {
        shouldThrow<IllegalArgumentException> { BigInteger.ONE.toRippleTokenValue(-1) }
        shouldThrow<IllegalArgumentException> { BigInteger.ONE.toRippleTokenValue(1_000_000) }
    }

    private companion object {
        const val RLUSD_HEX = "524C555344000000000000000000000000000000"
    }
}
