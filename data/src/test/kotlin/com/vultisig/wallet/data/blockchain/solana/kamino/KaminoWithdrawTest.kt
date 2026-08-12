package com.vultisig.wallet.data.blockchain.solana.kamino

import com.vultisig.wallet.data.api.KaminoUserPositionJson
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * Coverage for the withdraw path's arithmetic, which is the fund-safety boundary of the feature:
 * the endpoint validates nothing, and a request naming more shares than the wallet holds is
 * silently rewritten to `u64::MAX` — withdraw everything.
 *
 * Rates and scales are the live values for the launch vaults.
 */
class KaminoWithdrawTest {

    /** Steakhouse USDC: 6-decimal token, 6-decimal shares, rate just above 1. */
    private val usdcRate = KaminoRate.parse("1.0544278224860290217")!!

    /** Allez SOL: 9-decimal token, 6-decimal shares, rate far below 1. */
    private val solRate = KaminoRate.parse("0.0010757616854425424673")!!

    private fun shares(baseUnits: String, decimals: Int = 6) =
        KaminoShareAmount(BigInteger(baseUnits), decimals)

    private fun tokens(baseUnits: String, decimals: Int) =
        KaminoTokenAmount(BigInteger(baseUnits), decimals)

    private fun position(staked: String?, unstaked: String?, total: String?) =
        KaminoUserPositionJson(
            vaultAddress = KaminoVaultRegistry.STEAKHOUSE_USDC.address,
            stakedShares = staked,
            unstakedShares = unstaked,
            totalShares = total,
        )

    @Test
    fun `rate parsing is exact and rejects anything it cannot read plainly`() {
        val rate = KaminoRate.parse("1.0544278224860290217")!!
        assertEquals(BigInteger("10544278224860290217"), rate.numerator)
        assertEquals(19, rate.scale)

        assertEquals(BigInteger("5"), KaminoRate.parse("5")!!.numerator)
        assertEquals(0, KaminoRate.parse("5")!!.scale)

        // A value that is present but unreadable is a failed read, never a number to guess at:
        // grouping separators and exponents are exactly how a locale-sensitive parser mis-prices.
        listOf("1e5", "1,054.42", " 1.5", "1.5 ", "1..5", "abc", "", "1.2.3").forEach {
            assertNull(KaminoRate.parse(it), "should have refused '$it'")
        }
        assertNull(KaminoRate.parse(null))
    }

    @Test
    fun `share amounts parse at the vault's scale, truncating a longer fraction`() {
        assertEquals(BigInteger("1000000"), KaminoShareAmount.parse("1", 6)!!.baseUnits)
        assertEquals(BigInteger("1500000"), KaminoShareAmount.parse("1.5", 6)!!.baseUnits)
        // More precision than the mint carries is truncated, never rounded up.
        assertEquals(BigInteger("1999999"), KaminoShareAmount.parse("1.9999999", 6)!!.baseUnits)
    }

    @Test
    fun `shares convert to the token value the position card shows`() {
        // 1000 shares at the Steakhouse rate.
        val value = shares("1000000000").tokenValue(usdcRate, tokenDecimals = 6)
        assertEquals(BigInteger("1054427822"), value!!.baseUnits)
        assertEquals("1054.427822", value.apiString())

        // Allez SOL: 6-decimal shares against a 9-decimal token, so the same share count is worth a
        // thousandth as much. Assuming the scales match would misprice this by ~930x.
        val solValue = shares("1000000000").tokenValue(solRate, tokenDecimals = 9)
        assertEquals(BigInteger("1075761685"), solValue!!.baseUnits)
        assertEquals("1.075761685", solValue.apiString())
    }

    @Test
    fun `the vault minimum rounds up so it converts back to at least the minimum`() {
        // minWithdrawAmount is 1000 SHARE base units. Rounded down it would be 1054 token units,
        // which converts back to fewer shares than the vault accepts.
        val minimum = KaminoWithdrawMath.minimumTokens(shares("1000"), usdcRate, tokenDecimals = 6)
        assertEquals(BigInteger("1055"), minimum!!.baseUnits)

        val backToShares = minimum.shareAmount(usdcRate, shareDecimals = 6)!!
        assertTrue(
            backToShares.baseUnits >= BigInteger("1000"),
            "rounding up must survive the round trip, was ${backToShares.baseUnits}",
        )
    }

    @Test
    fun `a withdraw of exactly the maximum sends the held shares verbatim`() {
        val held = shares("1000000000")
        val maximum = KaminoWithdrawMath.maximumTokens(held, usdcRate, 6)!!

        val sized =
            KaminoWithdrawMath.sharesForTokens(
                tokens = maximum,
                held = held,
                maximumTokens = maximum,
                rate = usdcRate,
                shareDecimals = 6,
            )

        // Not merely equal — the same balance that was read. Round-tripping the maximum through
        // tokens and back loses a base unit (999999999 against 1000000000), and it is the opposite
        // slip, an extra unit, that the API turns into a full exit.
        assertSame(held, sized)
        assertEquals(
            BigInteger("999999999"),
            maximum.shareAmount(usdcRate, 6)!!.baseUnits,
            "the round trip really does lose a unit, which is why it is not used",
        )
    }

    @Test
    fun `a withdraw above the maximum is refused rather than clamped`() {
        val held = shares("1000000000")
        val maximum = KaminoWithdrawMath.maximumTokens(held, usdcRate, 6)!!
        val oneUnitOver = tokens(maximum.baseUnits.add(BigInteger.ONE).toString(), 6)

        // Clamping would mean reading "more than the position" as "the position" — and since the
        // API
        // rewrites an over-request to u64::MAX, a mistyped digit would become a full exit.
        assertNull(
            KaminoWithdrawMath.sharesForTokens(oneUnitOver, held, maximum, usdcRate, 6),
            "one base unit over the maximum must be refused",
        )
    }

    @Test
    fun `a partial withdraw can never reach the held balance`() {
        val held = shares("1000000000")
        val maximum = KaminoWithdrawMath.maximumTokens(held, usdcRate, 6)!!

        // Amounts worth at least one share; anything smaller truncates to zero shares and is
        // refused by `a token amount worth less than one share is refused` below.
        listOf(
                "1054428",
                "1000000000",
                "527213911",
                maximum.baseUnits.subtract(BigInteger.ONE).toString(),
            )
            .forEach { raw ->
                val sized =
                    KaminoWithdrawMath.sharesForTokens(tokens(raw, 6), held, maximum, usdcRate, 6)
                assertNotNull(sized, "partial withdraw of $raw should size")
                assertTrue(
                    sized.baseUnits < held.baseUnits,
                    "partial withdraw of $raw produced $sized, which is not below the balance",
                )
            }
    }

    @Test
    fun `a token amount worth less than one share is refused rather than sized to zero`() {
        // One base unit of USDC is 0.000001, worth 0.00000094 shares at the Steakhouse rate, which
        // truncates to nothing. A request for zero shares would build a transaction that moves
        // nothing while the form said it moves something.
        val held = shares("1000000000")
        val maximum = KaminoWithdrawMath.maximumTokens(held, usdcRate, 6)!!
        assertNull(
            KaminoWithdrawMath.sharesForTokens(tokens("1", 6), held, maximum, usdcRate, 6),
            "an amount below one share must not size to zero",
        )
    }

    @Test
    fun `zero and a zero balance size to nothing`() {
        val held = shares("1000000000")
        val maximum = KaminoWithdrawMath.maximumTokens(held, usdcRate, 6)!!
        assertNull(KaminoWithdrawMath.sharesForTokens(tokens("0", 6), held, maximum, usdcRate, 6))
        assertNull(
            KaminoWithdrawMath.sharesForTokens(maximum, shares("0"), maximum, usdcRate, 6),
            "a zero balance cannot fund any withdraw",
        )
    }

    @Test
    fun `sending the token figure where shares belong is what this arithmetic prevents`() {
        // The bug this whole type split exists to make impossible. On a rate above 1 the token
        // figure is LARGER than the share count, so passing it through as shares over-requests —
        // and an over-request is rewritten by the API to a full exit.
        val held = shares("1000000000")
        val maximum = KaminoWithdrawMath.maximumTokens(held, usdcRate, 6)!!
        val tokenFigureUsedAsShares = KaminoShareAmount(maximum.baseUnits, 6)

        assertTrue(
            tokenFigureUsedAsShares.baseUnits > held.baseUnits,
            "the token figure exceeds the share balance, which is why it must never be sent as one",
        )

        // Sized correctly it is exactly the balance instead.
        assertEquals(
            held.baseUnits,
            KaminoWithdrawMath.sharesForTokens(maximum, held, maximum, usdcRate, 6)!!.baseUnits,
        )
    }

    @Test
    fun `a wholly unstaked position is withdrawable, one base unit below the balance`() {
        val eligibility =
            KaminoWithdrawEligibility.resolve(position("0", "1000", "1000"), shareDecimals = 6)
        assertTrue(eligibility is KaminoWithdrawEligibility.Withdrawable)
        // "1000" scales exactly to 1000000000 base units, so the maximum steps back one unit: the
        // API cannot tell a request for the whole balance from a request for more than it, and the
        // latter it answers by withdrawing everything.
        assertEquals(BigInteger("999999999"), eligibility.shares.maximum.baseUnits)
        assertEquals(BigInteger("1000000000"), eligibility.shares.unstaked.baseUnits)
    }

    @Test
    fun `a staked position is withdrawable — Kamino releases it from the farm`() {
        // Every launch-vault deposit auto-stakes, so this is what essentially every real holder is
        // in. The withdraw transaction does farms::unstake then farms::withdraw_unstaked_deposits
        // ahead of the vault withdraw, so refusing it would block the only path that exists.
        val eligibility =
            KaminoWithdrawEligibility.resolve(position("400", "600", "1000"), shareDecimals = 6)
        assertTrue(eligibility is KaminoWithdrawEligibility.Withdrawable)
        assertEquals(BigInteger("999999999"), eligibility.shares.maximum.baseUnits)
        // The unstaked half travels with it: it decides how much the farm has to release.
        assertEquals(BigInteger("600000000"), eligibility.shares.unstaked.baseUnits)
    }

    @Test
    fun `an inexact total needs no step back, because truncating already went below it`() {
        // "1.9999999" at 6 decimals truncates to 1999999, which is already strictly under the
        // balance, so no unit is given up on top.
        val eligibility =
            KaminoWithdrawEligibility.resolve(
                position("0", "1.9999999", "1.9999999"),
                shareDecimals = 6,
            )
        assertTrue(eligibility is KaminoWithdrawEligibility.Withdrawable)
        assertEquals(BigInteger("1999999"), eligibility.shares.maximum.baseUnits)
    }

    @Test
    fun `a position of a single base unit has nothing to withdraw`() {
        // Stepping back from the sentinel leaves zero, and that is the true statement about it.
        assertEquals(
            KaminoWithdrawEligibility.Empty,
            KaminoWithdrawEligibility.resolve(position("0", "0.000001", "0.000001"), 6),
        )
    }

    @Test
    fun `parts are summed at the API's precision, not the mint's`() {
        // Truncating each string to six decimals and adding two of them misses the third by a base
        // unit with nothing wrong: 944548 + 959593 is one short of 1904142. Refusing a real
        // position
        // over its last decimal place would be a bug, not a guard.
        val eligibility =
            KaminoWithdrawEligibility.resolve(
                position("0.9445485", "0.9595935", "1.904142"),
                shareDecimals = 6,
            )
        assertTrue(
            eligibility is KaminoWithdrawEligibility.Withdrawable,
            "summing at full precision must accept this position, was $eligibility",
        )
    }

    @Test
    fun `parts that genuinely do not add up are refused`() {
        // Not a truncation artefact — the response has shares it has not accounted for, and how
        // much
        // of the remainder is staked would have to be guessed.
        assertEquals(
            KaminoWithdrawEligibility.Unreadable,
            KaminoWithdrawEligibility.resolve(position("0", "600", "1000"), 6),
        )
    }

    @Test
    fun `an implausible or unparseable position is refused, never treated as zero or whole`() {
        // Parts exceeding the total cannot all be true, and spending a balance that is too large is
        // precisely what becomes u64::MAX.
        assertEquals(
            KaminoWithdrawEligibility.Unreadable,
            KaminoWithdrawEligibility.resolve(position("0", "2000", "1000"), 6),
        )
        assertEquals(
            KaminoWithdrawEligibility.Unreadable,
            KaminoWithdrawEligibility.resolve(position("0", "n/a", "1000"), 6),
        )
        assertEquals(
            KaminoWithdrawEligibility.Unreadable,
            KaminoWithdrawEligibility.resolve(position("0", null, "1000"), 6),
        )
    }

    @Test
    fun `an absent vault or a zero balance is empty`() {
        assertEquals(KaminoWithdrawEligibility.Empty, KaminoWithdrawEligibility.resolve(null, 6))
        assertEquals(
            KaminoWithdrawEligibility.Empty,
            KaminoWithdrawEligibility.resolve(position("0", "0", "0"), 6),
        )
    }

    @Test
    fun `liquidity is delayed only when the request exceeds the published buffer`() {
        val buffer = tokens("72851448339", 6) // the Steakhouse vault's live liquid balance

        assertEquals(
            KaminoWithdrawLiquidity.Instant,
            KaminoWithdrawLiquidity.resolve(tokens("1000000", 6), buffer),
        )
        assertEquals(
            KaminoWithdrawLiquidity.Instant,
            KaminoWithdrawLiquidity.resolve(buffer, buffer),
            "exactly the buffer still settles instantly",
        )

        val delayed = KaminoWithdrawLiquidity.resolve(tokens("72851448340", 6), buffer)
        assertTrue(delayed is KaminoWithdrawLiquidity.Delayed)
        assertEquals(buffer, delayed.available)
    }

    @Test
    fun `an unpublished buffer is not reported as delayed`() {
        // Absent information is not evidence of a delay; the row simply says nothing.
        assertEquals(
            KaminoWithdrawLiquidity.Instant,
            KaminoWithdrawLiquidity.resolve(tokens("1000000", 6), null),
        )
    }

    @Test
    fun `implausible scales are refused rather than producing a wrong number`() {
        val held = shares("1000000000")
        assertNull(held.tokenValue(KaminoRate(BigInteger.ZERO, 0), 6), "a zero rate has no value")
        assertNull(held.tokenValue(usdcRate, tokenDecimals = 40), "absurd token scale")
        assertNull(
            KaminoShareAmount(BigInteger("-1"), 6).tokenValue(usdcRate, 6),
            "a negative balance is not a balance",
        )
        assertFalse(KaminoRate(BigInteger.ZERO, 0).isPositive)
    }
}
