package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.api.RippleApi
import com.vultisig.wallet.data.api.RippleServerStateResponseJson
import com.vultisig.wallet.data.api.RippleServerStateResultJson
import com.vultisig.wallet.data.api.RippleTrustLineJson
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.RIPPLE_SEED_OWNER_RESERVE_DROPS
import com.vultisig.wallet.data.models.RIPPLE_TOKEN_DECIMALS
import com.vultisig.wallet.data.models.rippleTokenContractAddress
import io.mockk.coEvery
import io.mockk.mockk
import java.math.BigInteger
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class RippleTrustLinesTest {

    private val rippleApi: RippleApi = mockk()

    private val trustLines = RippleTrustLinesImpl(rippleApi)

    @Test
    fun `a token with no matching line needs activation`() = runTest {
        coEvery { rippleApi.fetchAccountLines(ACCOUNT) } returns listOf(line("EUR", ISSUER))

        val result = trustLines.tokensNeedingTrustLine(ACCOUNT, listOf(usd, Coins.Ripple.RLUSD))

        assertEquals(setOf(usd.id, Coins.Ripple.RLUSD.id), result)
    }

    // A held line can be empty or overdrawn; only its absence blocks receiving.
    @Test
    fun `an existing line needs no activation whatever its balance`() = runTest {
        coEvery { rippleApi.fetchAccountLines(ACCOUNT) } returns
            listOf(line("USD", ISSUER, balance = "0"))

        assertEquals(emptySet<String>(), trustLines.tokensNeedingTrustLine(ACCOUNT, listOf(usd)))
    }

    // Both halves are case-sensitive on XRPL, so a same-currency line from someone else is not it.
    @Test
    fun `a line from another issuer does not satisfy the token`() = runTest {
        coEvery { rippleApi.fetchAccountLines(ACCOUNT) } returns listOf(line("USD", OTHER_ISSUER))

        assertEquals(setOf(usd.id), trustLines.tokensNeedingTrustLine(ACCOUNT, listOf(usd)))
    }

    @Test
    fun `native XRP is never offered a trust line`() = runTest {
        assertEquals(
            emptySet<String>(),
            trustLines.tokensNeedingTrustLine(ACCOUNT, listOf(Coins.Ripple.XRP)),
        )
    }

    // Without evidence a line is missing, offering to open one would spend the reserve for nothing.
    @Test
    fun `an unreadable account offers no activation`() = runTest {
        coEvery { rippleApi.fetchAccountLines(ACCOUNT) } throws IllegalStateException("offline")

        assertEquals(emptySet<String>(), trustLines.tokensNeedingTrustLine(ACCOUNT, listOf(usd)))
    }

    @Test
    fun `the owner reserve comes from the live reserve_inc`() = runTest {
        coEvery { rippleApi.fetchServerState() } returns serverState(reserveInc = 500_000)

        assertEquals(BigInteger.valueOf(500_000), trustLines.fetchOwnerReserve())
    }

    @Test
    fun `an unreadable server state falls back to the seeded reserve`() = runTest {
        coEvery { rippleApi.fetchServerState() } throws IllegalStateException("offline")

        assertEquals(RIPPLE_SEED_OWNER_RESERVE_DROPS, trustLines.fetchOwnerReserve())
    }

    private fun line(currency: String, issuer: String, balance: String = "1") =
        RippleTrustLineJson(account = issuer, currency = currency, balance = balance)

    private fun serverState(reserveInc: Long) =
        RippleServerStateResponseJson(
            result =
                RippleServerStateResultJson(
                    state =
                        RippleServerStateResultJson.RippleStateJson(
                            validateLedger =
                                RippleServerStateResultJson.RippleStateJson.RippleValidateLedger(
                                    reservedBase = 1_000_000,
                                    reserveInc = reserveInc,
                                    baseFee = 10,
                                ),
                            loadBase = 256,
                            loadFactor = 256,
                        )
                )
        )

    private val usd =
        Coin(
            chain = Chain.Ripple,
            ticker = "USD",
            logo = "",
            address = ACCOUNT,
            decimal = RIPPLE_TOKEN_DECIMALS,
            hexPublicKey = "",
            priceProviderID = "",
            contractAddress = rippleTokenContractAddress("USD", ISSUER),
            isNativeToken = false,
        )

    private companion object {
        const val ACCOUNT = "rPVMhWBsfF9iMXYj3aAzJVkPDTFNSyWdKy"
        const val ISSUER = "rvYAfWj5gh67oV6fW32ZzP3Aw4Eubs59B"
        const val OTHER_ISSUER = "rMxCKbEDwqr76QuheSUMdEGf4B9xJ8m5De"
    }
}
