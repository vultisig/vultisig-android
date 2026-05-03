package com.vultisig.wallet.data.models

import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

/**
 * Pins [Chain.raw] values that travel on the wire to the FastVault server. Server stores chain
 * pubkeys keyed by `.raw` at /vault/import time and looks them up via case-insensitive match at
 * sign time. API request models MUST send `.raw`, not `.name` (the Kotlin enum case identifier).
 */
class ChainWireFormatTest {

    @Test
    fun `BscChain raw is BSC, name is BscChain`() {
        assertEquals("BSC", Chain.BscChain.raw)
        assertEquals("BscChain", Chain.BscChain.name)
    }

    @Test
    fun `BitcoinCash raw is Bitcoin-Cash with hyphen`() {
        assertEquals("Bitcoin-Cash", Chain.BitcoinCash.raw)
        assertEquals("BitcoinCash", Chain.BitcoinCash.name)
    }

    @Test
    fun `GaiaChain raw is Cosmos`() {
        assertEquals("Cosmos", Chain.GaiaChain.raw)
        assertEquals("GaiaChain", Chain.GaiaChain.name)
    }

    @Test
    fun `ThorChain raw is THORChain capitalised`() {
        assertEquals("THORChain", Chain.ThorChain.raw)
        assertEquals("ThorChain", Chain.ThorChain.name)
    }

    @Test
    fun `Ethereum raw equals Ethereum`() {
        assertEquals("Ethereum", Chain.Ethereum.raw)
    }

    @Test
    fun `Bitcoin raw equals Bitcoin`() {
        assertEquals("Bitcoin", Chain.Bitcoin.raw)
    }

    @Test
    fun `Solana raw equals Solana`() {
        assertEquals("Solana", Chain.Solana.raw)
    }

    @Test
    fun `Polygon raw equals Polygon`() {
        assertEquals("Polygon", Chain.Polygon.raw)
    }

    @Test
    fun `fromRaw round-trips for chains where name differs from raw`() {
        assertEquals(Chain.ThorChain, Chain.fromRaw("thorchain"))
        assertEquals(Chain.BscChain, Chain.fromRaw("bsc"))
        assertEquals(Chain.GaiaChain, Chain.fromRaw("cosmos"))
    }

    @Test
    fun `Android raw matches iOS chain name for chains with custom wire string`() {
        assertEquals("BSC", Chain.BscChain.raw)
        assertEquals("Bitcoin-Cash", Chain.BitcoinCash.raw)
        assertEquals("Cosmos", Chain.GaiaChain.raw)
        assertEquals("THORChain", Chain.ThorChain.raw)
        assertEquals("MayaChain", Chain.MayaChain.raw)
        assertEquals("CronosChain", Chain.CronosChain.raw)
        assertEquals("TerraClassic", Chain.TerraClassic.raw)
    }
}
