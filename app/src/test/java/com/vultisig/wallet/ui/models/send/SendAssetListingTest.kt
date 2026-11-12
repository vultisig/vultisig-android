package com.vultisig.wallet.ui.models.send

import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.models.TokenValue
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import org.junit.jupiter.api.Test

class SendAssetListingTest {

    private val btc = account(Coins.Bitcoin.BTC, "50")
    private val eth = account(Coins.Ethereum.ETH, "30")
    private val usdc = account(Coins.Ethereum.USDC, "30")
    private val doge = account(Coins.Dogecoin.DOGE, null)
    private val dash = account(Coins.Dash.DASH, "0")

    // Vault order: Dogecoin, Ethereum, Bitcoin, Dash.
    private val vault = listOf(doge, eth, usdc, btc, dash)

    @Test
    fun `all chains lists every coin richest first, then by ticker`() {
        val listing = buildSendAssetListing(vault, null, "", null)

        listing.rows.tickers() shouldContainExactly listOf("BTC", "ETH", "USDC", "DASH", "DOGE")
    }

    @Test
    fun `a chain chip narrows the rows to that chain`() {
        val listing = buildSendAssetListing(vault, Chain.Ethereum, "", null)

        listing.rows.tickers() shouldContainExactly listOf("ETH", "USDC")
    }

    @Test
    fun `a search spans every chain whatever chip is picked`() {
        val listing = buildSendAssetListing(vault, Chain.Ethereum, "bit", null)

        listing.rows.tickers() shouldContainExactly listOf("BTC")
    }

    @Test
    fun `a search matches the chain name too`() {
        val listing = buildSendAssetListing(vault, null, "  ethereum ", null)

        listing.rows.tickers() shouldContainExactly listOf("ETH", "USDC")
    }

    @Test
    fun `the selected coin is pinned first when visible`() {
        val listing = buildSendAssetListing(vault, null, "", Coins.Dogecoin.DOGE.id)

        listing.rows.tickers().first() shouldBe "DOGE"
        listing.rows.size shouldBe vault.size
    }

    @Test
    fun `a selected coin outside the visible rows is not added`() {
        val listing = buildSendAssetListing(vault, Chain.Ethereum, "", Coins.Bitcoin.BTC.id)

        listing.rows.tickers() shouldContainExactly listOf("ETH", "USDC")
    }

    @Test
    fun `chains run by summed holdings with ties in vault order`() {
        val listing = buildSendAssetListing(vault, null, "", null)

        // Ethereum 60, Bitcoin 50, then Dogecoin (unpriced) and Dash (0) tie at zero.
        listing.chains shouldContainExactly
            listOf(Chain.Ethereum, Chain.Bitcoin, Chain.Dogecoin, Chain.Dash)
    }

    @Test
    fun `chain order ignores the chip and the search`() {
        val all = buildSendAssetListing(vault, null, "", null)
        val searched = buildSendAssetListing(vault, Chain.Dash, "usd", null)

        searched.chains shouldBe all.chains
    }

    @Test
    fun `equal fiat and ticker fall back to the iOS chain order, not the Android enum`() {
        // Android declares Arbitrum, Base, Ethereum in that order; iOS has Ethereum, Base,
        // Arbitrum.
        val accounts =
            listOf(
                account(Coins.Arbitrum.ETH, "0"),
                account(Coins.Base.ETH, "0"),
                account(Coins.Ethereum.ETH, "0"),
                account(Coins.Arbitrum.USDC, "0"),
                account(Coins.Base.USDC, "0"),
                account(Coins.Ethereum.USDC, "0"),
            )

        val listing = buildSendAssetListing(accounts, null, "", null)

        listing.rows.map { it.token.chain } shouldContainExactly
            listOf(
                Chain.Ethereum,
                Chain.Base,
                Chain.Arbitrum,
                Chain.Ethereum,
                Chain.Base,
                Chain.Arbitrum,
            )
        listing.rows.tickers() shouldContainExactly
            listOf("ETH", "ETH", "ETH", "USDC", "USDC", "USDC")
    }

    @Test
    fun `fiat is balance times price unrounded, so sub-cent holdings still order`() {
        // Both hold under a cent, so the stored value reads 0.00 for each.
        val smaller = priced(Coins.Dash.DASH, units = "0.0001", unitPrice = "10", storedFiat = "0")
        val larger =
            priced(Coins.Dogecoin.DOGE, units = "0.04", unitPrice = "0.1", storedFiat = "0")

        val listing = buildSendAssetListing(listOf(smaller, larger), null, "", null)

        listing.rows.tickers() shouldContainExactly listOf("DOGE", "DASH")
        listing.chains shouldContainExactly listOf(Chain.Dogecoin, Chain.Dash)
    }

    @Test
    fun `DeFi-only positions are not listed and do not count towards their chain`() {
        val rune = account(Coins.ThorChain.RUNE, "1")
        val stcy = account(Coins.ThorChain.sTCY, "500")
        val ybrune = account(Coins.ThorChain.ybRUNE, "500")
        val accounts = listOf(rune, stcy, ybrune, btc)

        val listing = buildSendAssetListing(accounts, null, "", Coins.ThorChain.RUNE.id)

        listing.rows.tickers() shouldContainExactly listOf("RUNE", "BTC")
        listing.chains shouldContainExactly listOf(Chain.Bitcoin, Chain.ThorChain)
    }

    @Test
    fun `a send opened on a DeFi receipt keeps that receipt's row, pinned`() {
        val rune = account(Coins.ThorChain.RUNE, "1")
        val stcy = account(Coins.ThorChain.sTCY, "500")
        val ybrune = account(Coins.ThorChain.ybRUNE, "500")

        val listing =
            buildSendAssetListing(
                listOf(rune, stcy, ybrune, btc),
                null,
                "",
                Coins.ThorChain.ybRUNE.id,
            )

        listing.rows.tickers() shouldContainExactly listOf("ybRUNE", "BTC", "RUNE")
    }

    @Test
    fun `a chain without its native coin in the vault is not sendable`() {
        val listing =
            buildSendAssetListing(listOf(btc, account(Coins.Base.USDC, "10")), null, "", null)

        listing.rows.tickers() shouldContainExactly listOf("BTC")
        listing.chains shouldContainExactly listOf(Chain.Bitcoin)
    }

    @Test
    fun `chains follow the order their native coins appear in`() {
        val zeroUsdc = account(Coins.Ethereum.USDC, "0")
        val zeroEth = account(Coins.Ethereum.ETH, "0")
        val listing = buildSendAssetListing(listOf(zeroUsdc, dash, zeroEth, doge), null, "", null)

        // All hold zero; Dash's native coin comes before Ethereum's, though a USDC precedes both.
        listing.chains shouldContainExactly listOf(Chain.Dash, Chain.Ethereum, Chain.Dogecoin)
    }

    private fun List<Account>.tickers() = map { it.token.ticker }

    private fun account(coin: Coin, fiat: String?) =
        Account(
            token = coin,
            tokenValue = null,
            fiatValue = fiat?.let { FiatValue(BigDecimal(it), "USD") },
            price = null,
        )

    private fun priced(coin: Coin, units: String, unitPrice: String, storedFiat: String) =
        Account(
            token = coin,
            tokenValue =
                TokenValue(
                    BigDecimal(units).movePointRight(coin.decimal).toBigIntegerExact(),
                    coin,
                ),
            fiatValue = FiatValue(BigDecimal(storedFiat), "USD"),
            price = FiatValue(BigDecimal(unitPrice), "USD"),
        )
}
