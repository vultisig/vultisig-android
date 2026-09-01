package com.vultisig.wallet.data.models

import java.math.BigDecimal
import java.math.BigInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Tests for [calculateActiveDefiPositionsCount], the count the DeFi home row reports in place of
 * the wallet's token inventory.
 */
class CalculateActiveDefiPositionsCountTest {

    private fun coin(ticker: String) =
        Coin(
            chain = Chain.ThorChain,
            ticker = ticker,
            logo = "",
            address = "addr",
            decimal = 8,
            hexPublicKey = "",
            priceProviderID = "",
            contractAddress = "",
            isNativeToken = ticker == "RUNE",
        )

    private fun account(ticker: String, amount: BigInteger?) =
        Account(
            token = coin(ticker),
            tokenValue = amount?.let { TokenValue(it, ticker, 8) },
            fiatValue = amount?.let { FiatValue(BigDecimal.ONE, "USD") },
            price = null,
        )

    @Test
    fun `counts only the accounts holding something`() {
        val accounts =
            listOf(
                account("RUNE", BigInteger("100")),
                account("TCY", BigInteger("50")),
                account("RUJI", BigInteger.ZERO),
                account("yRUNE", BigInteger.ZERO),
            )

        assertEquals(2, accounts.calculateActiveDefiPositionsCount())
    }

    @Test
    fun `a chain whose positions are all empty has none`() {
        val accounts = listOf(account("RUNE", BigInteger.ZERO), account("TCY", BigInteger.ZERO))

        assertEquals(0, accounts.calculateActiveDefiPositionsCount())
    }

    @Test
    fun `nothing resolved yet is unknown rather than none`() {
        val accounts = listOf(account("RUNE", null), account("TCY", null))

        assertNull(accounts.calculateActiveDefiPositionsCount())
    }

    @Test
    fun `a single resolved position counts while its neighbours are still pending`() {
        val accounts = listOf(account("RUNE", null), account("TCY", BigInteger("50")))

        assertEquals(1, accounts.calculateActiveDefiPositionsCount())
    }

    @Test
    fun `an address carrying no accounts has none rather than being unknown`() {
        assertEquals(0, emptyList<Account>().calculateActiveDefiPositionsCount())
    }
}
