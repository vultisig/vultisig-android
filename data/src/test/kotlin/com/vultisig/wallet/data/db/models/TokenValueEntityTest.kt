package com.vultisig.wallet.data.db.models

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coins
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

internal class TokenValueEntityTest {

    @Test
    fun `secured assets sharing a ticker on different underlying chains have distinct tokenIds`() {
        val ethUsdc =
            TokenValueEntity(
                chain = "THORChain",
                address = "thor1abc",
                ticker = "USDC",
                tokenValue = "100",
                contractAddress = "eth-usdc-0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48",
            )
        val avaxUsdc =
            TokenValueEntity(
                chain = "THORChain",
                address = "thor1abc",
                ticker = "USDC",
                tokenValue = "200",
                contractAddress = "avax-usdc-0xb97ef9ef8734c71904d8002f8b6bc66dd9c48a6e",
            )

        assertNotEquals(ethUsdc.tokenId, avaxUsdc.tokenId)
    }

    @Test
    fun `tokenId mirrors Coin id for secured assets`() {
        val entity =
            TokenValueEntity(
                chain = "THORChain",
                address = "thor1abc",
                ticker = "USDC",
                tokenValue = "100",
                contractAddress = "eth-usdc-0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48",
            )

        assertEquals(
            "USDC-THORChain-eth-usdc-0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48",
            entity.tokenId,
        )
    }

    @Test
    fun `a regular (non-secured) token's tokenId is unaffected — ticker-chain, no contract`() {
        val entity =
            TokenValueEntity(
                chain = "Ethereum",
                address = "0xabc",
                ticker = "USDC",
                tokenValue = "100",
                contractAddress = "0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48",
            )

        assertEquals("USDC-Ethereum", entity.tokenId)
    }

    @Test
    fun `native coins keep the plain ticker-chain tokenId`() {
        val entity =
            TokenValueEntity(
                chain = "THORChain",
                address = "thor1abc",
                ticker = "RUNE",
                tokenValue = "100",
                contractAddress = "",
            )

        assertEquals("RUNE-THORChain", entity.tokenId)
    }

    @Test
    fun `an XRPL issued currency is contract-qualified, mirroring Coin id`() {
        val entity =
            TokenValueEntity(
                chain = "Ripple",
                address = "rHb9CJAWyB4rj91VRWn96DkukG4bwdtyTh",
                ticker = "USD",
                tokenValue = "100",
                contractAddress = "USD.rvYAfWj5gh67oV6fW32ZzP3Aw4Eubs59B",
            )

        assertEquals("USD-Ripple-USD.rvYAfWj5gh67oV6fW32ZzP3Aw4Eubs59B", entity.tokenId)
    }

    // The same currency code from two issuers must not collapse onto one balance row.
    @Test
    fun `same XRPL currency from two issuers has distinct tokenIds`() {
        fun usdFrom(issuer: String) =
            TokenValueEntity(
                chain = "Ripple",
                address = "rHb9CJAWyB4rj91VRWn96DkukG4bwdtyTh",
                ticker = "USD",
                tokenValue = "1",
                contractAddress = "USD.$issuer",
            )

        assertNotEquals(
            usdFrom("rvYAfWj5gh67oV6fW32ZzP3Aw4Eubs59B").tokenId,
            usdFrom("rcoef87SYMJ58NAFx7fNM5frVknmvHsvJ").tokenId,
        )
    }

    // Native XRP has no issuer pair, so it keeps the plain form like any native coin.
    @Test
    fun `native XRP keeps the plain ticker-chain tokenId`() {
        val entity =
            TokenValueEntity(
                chain = "Ripple",
                address = "rHb9CJAWyB4rj91VRWn96DkukG4bwdtyTh",
                ticker = "XRP",
                tokenValue = "100",
                contractAddress = "",
            )

        assertEquals("XRP-Ripple", entity.tokenId)
    }

    // The cache row has to land on the same key Coin.id produces, or getCachedTokenBalances
    // resolves no coin for it and renders the balance at zero decimals.
    @Test
    fun `a Cardano native token is contract-qualified, mirroring Coin id`() {
        val snek = Coins.Cardano.SNEK.copy(address = CARDANO_ADDRESS)
        val entity =
            TokenValueEntity(
                chain = Chain.Cardano.id,
                address = CARDANO_ADDRESS,
                ticker = snek.ticker,
                tokenValue = "76715880000",
                contractAddress = snek.contractAddress,
            )

        assertEquals(snek.id, entity.tokenId)
    }

    // TON and TRON were contract-qualified in Coin.id without this side following, so a jetton's
    // cached row was resolving to no coin at all.
    @Test
    fun `TON and TRON tokens are contract-qualified, mirroring Coin id`() {
        val jetton =
            TokenValueEntity(
                chain = Chain.Ton.id,
                address = "UQTonAddress",
                ticker = "USDJ",
                tokenValue = "1",
                contractAddress = "EQFirstJetton",
            )
        val trc20 =
            TokenValueEntity(
                chain = Chain.Tron.id,
                address = "TTronAddress",
                ticker = "USDX",
                tokenValue = "1",
                contractAddress = "TFirstTrc20",
            )

        assertEquals("USDJ-Ton-EQFirstJetton", jetton.tokenId)
        assertEquals("USDX-Tron-TFirstTrc20", trc20.tokenId)
    }

    @Test
    fun `native ADA keeps the plain ticker-chain tokenId`() {
        val entity =
            TokenValueEntity(
                chain = Chain.Cardano.id,
                address = CARDANO_ADDRESS,
                ticker = "ADA",
                tokenValue = "100",
                contractAddress = "",
            )

        assertEquals("ADA-Cardano", entity.tokenId)
    }

    private companion object {
        const val CARDANO_ADDRESS = "addr1v9g9wnzsutrxt7vcg4efdfwhagwh3x2f6hjwykk7acdpsfgyt4h2j"
    }
}
