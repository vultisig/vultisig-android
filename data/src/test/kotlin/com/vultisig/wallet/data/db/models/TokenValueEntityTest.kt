package com.vultisig.wallet.data.db.models

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
}
