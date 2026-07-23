package com.vultisig.wallet.data.models

import java.math.BigInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Identity, ticker and fixed-point conversion rules for XRPL issued currencies (issue #5210). */
class RippleTokenTest {

    private fun token(currency: String, issuerAddress: String = ISSUER) =
        Coin.EMPTY.copy(
            chain = Chain.Ripple,
            ticker = rippleCurrencyTicker(currency),
            decimal = RIPPLE_TOKEN_DECIMALS,
            contractAddress = rippleTokenContractAddress(currency, issuerAddress),
            isNativeToken = false,
        )

    @Test
    fun `contract address round-trips the currency and issuer`() {
        val identity = token("USD").rippleTokenIdentity()

        assertEquals("USD", identity?.currency)
        assertEquals(ISSUER, identity?.issuer)
    }

    @Test
    fun `native XRP is not an issued token`() {
        val native = Coin.EMPTY.copy(chain = Chain.Ripple, ticker = "XRP", isNativeToken = true)

        assertFalse(native.isRippleIssuedToken)
        assertNull(native.rippleTokenIdentity())
    }

    @Test
    fun `a contract address without an issuer is not a valid identity`() {
        assertNull(parseRippleTokenIdentity("USD"))
        assertNull(parseRippleTokenIdentity("USD."))
        assertNull(parseRippleTokenIdentity(".$ISSUER"))
        assertNull(parseRippleTokenIdentity(""))
    }

    // Two issuers each minting "USD" would collide on the plain ticker-chain id and overwrite each
    // other's persisted row, so the id has to carry the issuer.
    @Test
    fun `same currency from different issuers yields distinct coin ids`() {
        assertNotEquals(token("USD").id, token("USD", OTHER_ISSUER).id)
    }

    @Test
    fun `issued token id is qualified by its contract address`() {
        assertEquals("USD-Ripple-USD.$ISSUER", token("USD").id)
    }

    @Test
    fun `native XRP keeps the unqualified id`() {
        val native = Coin.EMPTY.copy(chain = Chain.Ripple, ticker = "XRP", isNativeToken = true)

        assertEquals("XRP-Ripple", native.id)
    }

    @Test
    fun `standard three character currency codes are used verbatim`() {
        assertEquals("USD", rippleCurrencyTicker("USD"))
        assertEquals("EUR", rippleCurrencyTicker("EUR"))
    }

    // Names longer than three characters travel as the hex of a 160-bit code: a 0x00 lead byte,
    // the ASCII name, then zero padding.
    @Test
    fun `hex currency codes decode to their ascii name`() {
        val solo = "534F4C4F00000000000000000000000000000000"

        assertEquals("SOLO", rippleCurrencyTicker(solo))
    }

    // The other hex shape: a standard 3-character code widened to 160 bits, which pads the name
    // into bytes 12-14 behind a zero prefix.
    @Test
    fun `a standard code widened to hex decodes past its zero prefix`() {
        val usd = "0000000000000000000000005553440000000000"

        assertEquals("USD", rippleCurrencyTicker(usd))
    }

    @Test
    fun `non-ascii hex currency codes fall back to a hex preview`() {
        val demurrage = "01" + "FF".repeat(19)

        assertEquals("01FFFFFF", rippleCurrencyTicker(demurrage))
    }

    @Test
    fun `balances scale to fifteen decimals`() {
        assertEquals(BigInteger("125500000000000000"), "125.5".toRippleTokenUnits())
        assertEquals(BigInteger("1000000000000000"), "1".toRippleTokenUnits())
    }

    // XRPL carries 16 significant digits; anything below the pinned scale truncates down so a
    // displayed balance never rounds up past what the ledger holds.
    @Test
    fun `excess precision truncates instead of rounding up`() {
        assertEquals(BigInteger("1999999999999999"), "1.9999999999999999".toRippleTokenUnits())
        assertEquals(BigInteger.ZERO, "1e-20".toRippleTokenUnits())
    }

    @Test
    fun `scientific notation balances are parsed`() {
        assertEquals(BigInteger("1500000000000000000"), "1.5e3".toRippleTokenUnits())
    }

    @Test
    fun `negative and unparseable balances clamp to zero`() {
        assertEquals(BigInteger.ZERO, "-125.5".toRippleTokenUnits())
        assertEquals(BigInteger.ZERO, "0".toRippleTokenUnits())
        assertEquals(BigInteger.ZERO, "".toRippleTokenUnits())
        assertEquals(BigInteger.ZERO, "not-a-number".toRippleTokenUnits())
    }

    @Test
    fun `XRP is rejected as a trust-line currency`() {
        assertTrue(isRippleNativeCurrency("XRP"))
        assertTrue(isRippleNativeCurrency("xrp"))
        assertFalse(isRippleNativeCurrency("USD"))
    }

    // Read-only assets have no signing path, so send and swap must stay closed for them.
    @Test
    fun `issued tokens are read-only while native XRP is not`() {
        val native = Coin.EMPTY.copy(chain = Chain.Ripple, ticker = "XRP", isNativeToken = true)

        assertTrue(token("USD").isReadOnlyAsset)
        assertFalse(native.isReadOnlyAsset)
    }

    private companion object {
        const val ISSUER = "rvYAfWj5gh67oV6fW32ZzP3Aw4Eubs59B"
        const val OTHER_ISSUER = "rcoef87SYMJ58NAFx7fNM5frVknmvHsvJ"
    }
}
