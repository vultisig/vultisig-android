package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.api.RippleApi
import com.vultisig.wallet.data.api.RippleTrustLineJson
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.parseRippleTokenIdentity
import io.mockk.coEvery
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Discovery rules for XRPL trust-line holdings surfaced into the asset list (issue #5210).
 *
 * Discovery is gated to the curated [Coins] catalog: anyone can open a trust line under any
 * currency label, so only pairs the catalog lists are surfaced. The tests drive that gate through
 * the one catalog entry that exists today, RLUSD, whose currency/issuer pair is read from the
 * catalog so the suite tracks it automatically.
 */
class RippleTokenFinderTest {

    private val rippleApi = mockk<RippleApi>()
    private val finder = RippleTokenFinderImpl(rippleApi)

    private val rlusd = Coins.Ripple.RLUSD
    private val rlusdIdentity = checkNotNull(parseRippleTokenIdentity(rlusd.contractAddress))

    private fun line(
        currency: String = rlusdIdentity.currency,
        account: String = rlusdIdentity.issuer,
        balance: String,
    ) = RippleTrustLineJson(account = account, currency = currency, balance = balance)

    private suspend fun find(vararg lines: RippleTrustLineJson): List<Coin> {
        coEvery { rippleApi.fetchAccountLines(ACCOUNT) } returns lines.toList()
        return finder.find(ACCOUNT)
    }

    // A discovered line matching a catalog entry is surfaced as that entry, so the row keeps the
    // curated logo and priceProviderID that give it an icon and a fiat value.
    @Test
    fun `a curated currency is surfaced as its catalog entry`() = runTest {
        val coin = find(line(balance = "42")).single()

        assertEquals(rlusd, coin)
        assertEquals(Chain.Ripple, coin.chain)
        assertEquals("RLUSD", coin.ticker)
        assertEquals("rlusd", coin.logo)
        assertEquals("ripple-usd", coin.priceProviderID)
        assertEquals(false, coin.isNativeToken)
    }

    // The gate keys on the issuer too: the catalog's currency code minted by a different account is
    // a different, unverified asset and must not borrow the curated entry.
    @Test
    fun `a curated currency from an uncurated issuer is not surfaced`() = runTest {
        assertTrue(find(line(account = OTHER_ISSUER, balance = "42")).isEmpty())
    }

    // Anyone can open a trust line labelled "USD"; without a catalog entry it is unverifiable.
    @Test
    fun `an uncurated currency is not surfaced`() = runTest {
        assertTrue(find(line(currency = "USD", account = ISSUER, balance = "125.5")).isEmpty())
    }

    // Opening a trust line before ever receiving the currency leaves a zero-balance line. It still
    // costs owner reserve, but it is not a holding, so it must not clutter the asset list.
    @Test
    fun `zero balance trust lines are not surfaced`() = runTest {
        assertTrue(find(line(balance = "0")).isEmpty())
    }

    // A negative balance is the issuing side of the line — the account owes, it does not hold.
    @Test
    fun `owed trust lines are not surfaced`() = runTest {
        assertTrue(find(line(balance = "-10")).isEmpty())
    }

    @Test
    fun `XRP is never surfaced as a trust-line token`() = runTest {
        assertTrue(find(line(currency = "XRP", balance = "10")).isEmpty())
    }

    @Test
    fun `duplicate lines for one issuer collapse to a single coin`() = runTest {
        val coins = find(line(balance = "1"), line(balance = "2"))

        assertEquals(1, coins.size)
    }

    // A transient RPC failure must not wipe tokens the vault already holds; the refresh retries.
    @Test
    fun `a failed account_lines read yields no tokens instead of throwing`() = runTest {
        coEvery { rippleApi.fetchAccountLines(ACCOUNT) } throws IOException("offline")

        assertTrue(finder.find(ACCOUNT).isEmpty())
    }

    private companion object {
        const val ACCOUNT = "rHb9CJAWyB4rj91VRWn96DkukG4bwdtyTh"
        const val ISSUER = "rvYAfWj5gh67oV6fW32ZzP3Aw4Eubs59B"
        const val OTHER_ISSUER = "rcoef87SYMJ58NAFx7fNM5frVknmvHsvJ"
    }
}
