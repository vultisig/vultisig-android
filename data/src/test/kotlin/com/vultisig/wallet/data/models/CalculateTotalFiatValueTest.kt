package com.vultisig.wallet.data.models

import java.math.BigDecimal
import java.math.BigInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Tests for the two portfolio-total folds:
 * - [calculateAddressesTotalFiatValue] (strict): null as soon as any chain is unresolved — used by
 *   the vault list and delete screen, which want a final figure.
 * - [calculateAddressesPartialFiatValue] (partial): a slow/unresolved chain must not blank the
 *   whole home total — resolved chains keep contributing and only a fully-unresolved set yields
 *   null.
 *
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
    fun `both folds sum all chains when every chain is resolved`() {
        val addresses =
            listOf(
                address(Chain.Ethereum, account(Chain.Ethereum, "ETH", BigDecimal("10"))),
                address(Chain.Solana, account(Chain.Solana, "SOL", BigDecimal("5"))),
            )

        assertEquals(BigDecimal("15"), addresses.calculateAddressesTotalFiatValue()?.value)
        assertEquals(BigDecimal("15"), addresses.calculateAddressesPartialFiatValue()?.value)
    }

    @Test
    fun `partial ignores an unresolved chain while strict blanks to null`() {
        val addresses =
            listOf(
                address(Chain.Ethereum, account(Chain.Ethereum, "ETH", BigDecimal("10"))),
                // Solana still loading — fiat is null.
                address(Chain.Solana, account(Chain.Solana, "SOL", null)),
            )

        // Partial returns the resolved chains' sum; strict stays null until every chain loads.
        assertEquals(BigDecimal("10"), addresses.calculateAddressesPartialFiatValue()?.value)
        assertNull(addresses.calculateAddressesTotalFiatValue())
    }

    @Test
    fun `partial counts only resolved tokens within a partially-loaded chain`() {
        val addresses =
            listOf(
                address(
                    Chain.Ethereum,
                    account(Chain.Ethereum, "ETH", BigDecimal("10")),
                    account(Chain.Ethereum, "USDC", null),
                )
            )

        assertEquals(BigDecimal("10"), addresses.calculateAddressesPartialFiatValue()?.value)
        assertNull(addresses.calculateAddressesTotalFiatValue())
    }

    @Test
    fun `both folds are null only when nothing has resolved yet`() {
        val addresses =
            listOf(
                address(Chain.Ethereum, account(Chain.Ethereum, "ETH", null)),
                address(Chain.Solana, account(Chain.Solana, "SOL", null)),
            )

        assertNull(addresses.calculateAddressesPartialFiatValue())
        assertNull(addresses.calculateAddressesTotalFiatValue())
    }

    @Test
    fun `empty address list yields null partial and a zero strict total`() {
        // Partial returns null ("nothing loaded"); strict folds an empty list to its $0 seed, which
        // is the pre-#4768 behavior the vault list and delete screen already relied on.
        assertNull(emptyList<Address>().calculateAddressesPartialFiatValue())
        assertEquals(
            BigDecimal.ZERO,
            emptyList<Address>().calculateAddressesTotalFiatValue()?.value,
        )
    }
}
