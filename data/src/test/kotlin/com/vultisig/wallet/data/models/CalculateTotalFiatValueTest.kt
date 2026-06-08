package com.vultisig.wallet.data.models

import java.math.BigDecimal
import java.math.BigInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Tests for [calculateAddressesTotalFiatValue]: a slow/unresolved chain must not blank the whole
 * portfolio total — resolved chains keep contributing and only a fully-unresolved set yields null.
 * Regression coverage for issue #4768.
 */
class CalculateTotalFiatValueTest {

    private fun coin(chain: Chain, ticker: String, isNativeToken: Boolean = true) =
        Coin(
            chain = chain,
            ticker = ticker,
            logo = "",
            address = "addr-$ticker",
            decimal = 18,
            hexPublicKey = "",
            priceProviderID = "",
            contractAddress = "",
            isNativeToken = isNativeToken,
        )

    private fun account(chain: Chain, ticker: String, fiat: BigDecimal?): Account {
        val token = coin(chain, ticker)
        return Account(
            token = token,
            tokenValue = TokenValue(BigInteger.ONE, ticker, 18),
            fiatValue = fiat?.let { FiatValue(it, "USD") },
            price = fiat?.let { FiatValue(it, "USD") },
        )
    }

    private fun address(chain: Chain, vararg accounts: Account) =
        Address(chain = chain, address = "addr-${chain.raw}", accounts = accounts.toList())

    @Test
    fun `total sums all chains when every chain is resolved`() {
        val addresses =
            listOf(
                address(Chain.Ethereum, account(Chain.Ethereum, "ETH", BigDecimal("10"))),
                address(Chain.Solana, account(Chain.Solana, "SOL", BigDecimal("5"))),
            )

        assertEquals(BigDecimal("15"), addresses.calculateAddressesTotalFiatValue()?.value)
    }

    @Test
    fun `total ignores an unresolved chain instead of returning null`() {
        val addresses =
            listOf(
                address(Chain.Ethereum, account(Chain.Ethereum, "ETH", BigDecimal("10"))),
                // Solana still loading — fiat is null.
                address(Chain.Solana, account(Chain.Solana, "SOL", null)),
            )

        // Old behavior returned null (blank total); now it returns the resolved chains' sum.
        assertEquals(BigDecimal("10"), addresses.calculateAddressesTotalFiatValue()?.value)
    }

    @Test
    fun `total counts only resolved tokens within a partially-loaded chain`() {
        val addresses =
            listOf(
                address(
                    Chain.Ethereum,
                    account(Chain.Ethereum, "ETH", BigDecimal("10")),
                    account(Chain.Ethereum, "USDC", null),
                )
            )

        assertEquals(BigDecimal("10"), addresses.calculateAddressesTotalFiatValue()?.value)
    }

    @Test
    fun `total is null only when nothing has resolved yet`() {
        val addresses =
            listOf(
                address(Chain.Ethereum, account(Chain.Ethereum, "ETH", null)),
                address(Chain.Solana, account(Chain.Solana, "SOL", null)),
            )

        assertNull(addresses.calculateAddressesTotalFiatValue())
    }

    @Test
    fun `total is null for an empty address list`() {
        assertNull(emptyList<Address>().calculateAddressesTotalFiatValue())
    }
}
