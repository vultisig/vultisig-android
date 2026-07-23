package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.api.RippleApi
import com.vultisig.wallet.data.api.RippleTrustLineJson
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.RIPPLE_TOKEN_DECIMALS
import com.vultisig.wallet.data.models.parseRippleTokenIdentity
import io.mockk.coEvery
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Discovery rules for XRPL trust-line holdings surfaced into the asset list (issue #5210). */
class RippleTokenFinderTest {

    private val rippleApi = mockk<RippleApi>()
    private val finder = RippleTokenFinderImpl(rippleApi)

    private fun line(currency: String, account: String = ISSUER, balance: String) =
        RippleTrustLineJson(account = account, currency = currency, balance = balance)

    private suspend fun find(vararg lines: RippleTrustLineJson): List<Coin> {
        coEvery { rippleApi.fetchAccountLines(ACCOUNT) } returns lines.toList()
        return finder.find(ACCOUNT)
    }

    @Test
    fun `a held trust line becomes a coin carrying its currency and issuer`() = runTest {
        val coins = find(line("USD", balance = "125.5"))

        assertEquals(1, coins.size)
        val coin = coins.single()
        assertEquals(Chain.Ripple, coin.chain)
        assertEquals("USD", coin.ticker)
        assertEquals("USD.$ISSUER", coin.contractAddress)
        assertEquals(RIPPLE_TOKEN_DECIMALS, coin.decimal)
        assertEquals(false, coin.isNativeToken)
    }

    // Opening a trust line before ever receiving the currency leaves a zero-balance line. It still
    // costs owner reserve, but it is not a holding, so it must not clutter the asset list.
    @Test
    fun `zero balance trust lines are not surfaced`() = runTest {
        assertTrue(find(line("USD", balance = "0")).isEmpty())
    }

    // A negative balance is the issuing side of the line — the account owes, it does not hold.
    @Test
    fun `owed trust lines are not surfaced`() = runTest {
        assertTrue(find(line("USD", balance = "-10")).isEmpty())
    }

    @Test
    fun `XRP is never surfaced as a trust-line token`() = runTest {
        assertTrue(find(line("XRP", balance = "10")).isEmpty())
    }

    // The same currency code from two issuers is two distinct assets, both of which must appear.
    @Test
    fun `the same currency from different issuers yields two coins`() = runTest {
        val coins = find(line("USD", balance = "1"), line("USD", OTHER_ISSUER, "2"))

        assertEquals(2, coins.size)
        assertEquals(listOf("USD.$ISSUER", "USD.$OTHER_ISSUER"), coins.map { it.contractAddress })
        assertEquals(2, coins.map { it.id }.distinct().size)
    }

    @Test
    fun `duplicate lines for one issuer collapse to a single coin`() = runTest {
        val coins = find(line("USD", balance = "1"), line("USD", balance = "2"))

        assertEquals(1, coins.size)
    }

    @Test
    fun `hex currency codes surface under their decoded ticker`() = runTest {
        val coins = find(line("534F4C4F00000000000000000000000000000000", balance = "5"))

        assertEquals("SOLO", coins.single().ticker)
        // The raw on-chain code stays in the contract address so balance lookups still match.
        assertEquals(
            "534F4C4F00000000000000000000000000000000.$ISSUER",
            coins.single().contractAddress,
        )
    }

    // A discovered line that matches a catalog entry must reuse it, or the row loses the curated
    // logo and priceProviderID that give it an icon and a fiat value.
    @Test
    fun `a curated currency is surfaced as its catalog entry`() = runTest {
        val rlusd = Coins.Ripple.RLUSD
        val identity = checkNotNull(parseRippleTokenIdentity(rlusd.contractAddress))

        val coin = find(line(identity.currency, identity.issuer, balance = "42")).single()

        assertEquals(rlusd, coin)
        assertEquals("RLUSD", coin.ticker)
        assertEquals("rlusd", coin.logo)
        assertEquals("ripple-usd", coin.priceProviderID)
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
