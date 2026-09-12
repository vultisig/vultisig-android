package com.vultisig.wallet.data.repositories.swap

import com.vultisig.wallet.data.api.models.quotes.SwapKitFee
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import java.math.BigInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Pins [resolveSwapKitProviderFee] against the `fees[]` shapes SwapKit actually returns. The
 * entries below are trimmed from live `/v3/quote` and `/v3/swap` replies: a NEAR Intents route
 * charges in the source asset and labels its entries `chain: "NEAR"`, Flashnet charges in Solana
 * USDC, Chainflip in Ethereum USDC.
 */
internal class SwapKitProviderFeeTest {

    @Test
    fun `sums affiliate and service in the source asset on a NEAR route`() {
        // TRX → ETH, 2000 TRX: affiliate 10 + service 3, both `TRON.TRX` under `chain: "NEAR"`.
        val fees =
            listOf(
                fee("affiliate", "10", "TRON.TRX", chain = "NEAR"),
                fee("service", "3", "TRON.TRX", chain = "NEAR"),
                fee("outbound", "0.000035", "ETH.ETH", chain = "ETH"),
                fee("inbound", "1.1", "TRON.TRX", chain = "TRON"),
            )

        val resolved = resolveSwapKitProviderFee(fees, trx, eth, subProvider = "NEAR")

        assertEquals(trx, resolved?.coin)
        // 13 TRX in 6-decimal base units. The inbound entry is not part of it.
        assertEquals(BigInteger("13000000"), resolved?.amount)
    }

    @Test
    fun `matches on the asset identifier, not the entry's protocol chain label`() {
        // `chain: "NEAR"` is the routing protocol, not the asset's chain; keying on it would drop
        // the fee on exactly the routes that carry one most often.
        val fees = listOf(fee("affiliate", "0.01", "ZEC.ZEC", chain = "NEAR"))

        val resolved = resolveSwapKitProviderFee(fees, zec, eth, subProvider = "NEAR")

        assertEquals(zec, resolved?.coin)
        assertEquals(BigInteger("1000000"), resolved?.amount)
    }

    @Test
    fun `resolves a Chainflip fee in Ethereum USDC, a coin that is neither leg`() {
        val usdc = Coins.Ethereum.USDC
        val fees =
            listOf(
                fee("affiliate", "0.5", "ETH.USDC-${usdc.contractAddress}", chain = "ETH"),
                fee("service", "0.15", "eth.usdc-${usdc.contractAddress.lowercase()}"),
            )

        val resolved = resolveSwapKitProviderFee(fees, btc, eth, subProvider = "CHAINFLIP")

        assertEquals(usdc, resolved?.coin)
        assertEquals(BigInteger("650000"), resolved?.amount)
    }

    @Test
    fun `does not offer Ethereum USDC as a fee coin off a Chainflip route`() {
        val usdc = Coins.Ethereum.USDC
        val fees = listOf(fee("affiliate", "0.5", "ETH.USDC-${usdc.contractAddress}"))

        assertNull(resolveSwapKitProviderFee(fees, btc, eth, subProvider = "NEAR"))
    }

    @Test
    fun `yields nothing for a Flashnet fee in Solana USDC on a ZEC to ETH route`() {
        // Neither leg nor a Chainflip stable: the co-signer could not price it, so nothing goes on
        // the wire rather than an amount in a coin it has to guess.
        val fees =
            listOf(
                fee(
                    "affiliate",
                    "10.830937",
                    "SOL.USDC-EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v",
                ),
                fee("service", "3.249281", "SOL.USDC-EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v"),
                fee("inbound", "0.0006", "ZEC.ZEC", chain = "ZEC"),
            )

        assertNull(resolveSwapKitProviderFee(fees, zec, eth, subProvider = "FLASHNET"))
    }

    @Test
    fun `resolves a fee charged in the destination asset`() {
        val fees =
            listOf(
                fee("affiliate", "0.000851376923076923", "ETH.ETH", chain = "THOR"),
                fee("service", "0.000255413076923077", "ETH.ETH", chain = "THOR"),
            )

        val resolved =
            resolveSwapKitProviderFee(fees, trx, eth, subProvider = "THORCHAIN_STREAMING")

        assertEquals(eth, resolved?.coin)
        assertEquals(BigInteger("1106790000000000"), resolved?.amount)
    }

    @Test
    fun `yields nothing when affiliate and service are charged in different coins`() {
        val fees = listOf(fee("affiliate", "10", "TRON.TRX"), fee("service", "0.0001", "ETH.ETH"))

        assertNull(resolveSwapKitProviderFee(fees, trx, eth, subProvider = "NEAR"))
    }

    @Test
    fun `yields nothing for a route with no provider entries or only zero ones`() {
        assertNull(
            resolveSwapKitProviderFee(
                listOf(fee("inbound", "1.1", "TRON.TRX"), fee("outbound", "0.00003", "ETH.ETH")),
                trx,
                eth,
                subProvider = "NEAR",
            )
        )
        assertNull(
            resolveSwapKitProviderFee(
                listOf(fee("affiliate", "0", "TRON.TRX"), fee("service", "0.0", "TRON.TRX")),
                trx,
                eth,
                subProvider = "NEAR",
            )
        )
    }

    @Test
    fun `yields nothing for a malformed or negative amount rather than throwing`() {
        assertNull(
            resolveSwapKitProviderFee(
                listOf(fee("affiliate", "1e+", "TRON.TRX")),
                trx,
                eth,
                subProvider = "NEAR",
            )
        )
        assertNull(
            resolveSwapKitProviderFee(
                listOf(fee("affiliate", "-10", "TRON.TRX")),
                trx,
                eth,
                subProvider = "NEAR",
            )
        )
        assertNull(
            resolveSwapKitProviderFee(
                listOf(fee("affiliate", "10", asset = null)),
                trx,
                eth,
                subProvider = "NEAR",
            )
        )
    }

    @Test
    fun `truncates sub-unit dust instead of rounding it up`() {
        val fees = listOf(fee("affiliate", "0.0000001", "TRON.TRX"))

        // 0.0000001 TRX is a tenth of a sun; nothing to charge, nothing on the wire.
        assertNull(resolveSwapKitProviderFee(fees, trx, eth, subProvider = "NEAR"))
    }

    private fun fee(type: String, amount: String, asset: String?, chain: String? = null) =
        SwapKitFee(type = type, amount = amount, asset = asset, chain = chain, protocol = null)

    private val trx =
        Coin(
            chain = Chain.Tron,
            ticker = "TRX",
            logo = "",
            address = "Tsrc",
            decimal = 6,
            hexPublicKey = "pub",
            priceProviderID = "tron",
            contractAddress = "",
            isNativeToken = true,
        )

    private val zec =
        Coin(
            chain = Chain.Zcash,
            ticker = "ZEC",
            logo = "",
            address = "t1src",
            decimal = 8,
            hexPublicKey = "pub",
            priceProviderID = "zcash",
            contractAddress = "",
            isNativeToken = true,
        )

    private val btc =
        Coin(
            chain = Chain.Bitcoin,
            ticker = "BTC",
            logo = "",
            address = "bc1src",
            decimal = 8,
            hexPublicKey = "pub",
            priceProviderID = "bitcoin",
            contractAddress = "",
            isNativeToken = true,
        )

    private val eth =
        Coin(
            chain = Chain.Ethereum,
            ticker = "ETH",
            logo = "",
            address = "0xdst",
            decimal = 18,
            hexPublicKey = "pub",
            priceProviderID = "ethereum",
            contractAddress = "",
            isNativeToken = true,
        )
}
