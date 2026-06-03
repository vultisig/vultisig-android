package com.vultisig.wallet.data.blockchain.cosmos.staking

import com.vultisig.wallet.data.models.Chain
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pins the cross-platform staking-config contract for Terra + TerraClassic. Mirrors iOS
 * `CosmosStakingConfigTests.swift`.
 */
class CosmosStakingConfigTests {

    @Test
    fun `Terra entry matches pinned values`() {
        val entry = CosmosStakingConfig.entryFor(Chain.Terra)
        assertEquals("phoenix-1", entry.chainId)
        assertEquals("uluna", entry.bondDenom)
        assertEquals("uluna", entry.feeDenom)
        assertEquals("terravaloper", entry.valoperHrp)
        // Bumped from 300_000 -> 400_000 after observed OoG on phoenix-1 redelegate at 300_140
        // gasUsed (tx 44A3CE6C...EAF31). MsgBeginRedelegate writes two validator records so it
        // costs more than delegate/undelegate. Fee scaled proportionally to keep the gas-price
        // (uluna/gas) ratio constant.
        assertEquals(400_000L, entry.gasLimit)
        assertEquals(10_000L, entry.feeAmount)
        assertEquals(21, entry.unbondingDays)
    }

    @Test
    fun `TerraClassic entry matches pinned values`() {
        val entry = CosmosStakingConfig.entryFor(Chain.TerraClassic)
        assertEquals("columbus-5", entry.chainId)
        assertEquals("uluna", entry.bondDenom)
        assertEquals("uluna", entry.feeDenom)
        assertEquals("terravaloper", entry.valoperHrp)
        // LUNC redelegate also hits the dual-record path. Bumped 1.5M -> 2M for headroom; fee
        // scaled to preserve prior gas-price ratio (~66.6667 uluna/gas). 8-validator claim batch
        // = 16M total gas, still under columbus-5 per-block budget.
        assertEquals(2_000_000L, entry.gasLimit)
        assertEquals(133_333_334L, entry.feeAmount)
        assertEquals(21, entry.unbondingDays)
    }

    @Test
    fun `isStakingSupported returns true for Terra family only`() {
        assertTrue(CosmosStakingConfig.isStakingSupported(Chain.Terra))
        assertTrue(CosmosStakingConfig.isStakingSupported(Chain.TerraClassic))
    }

    @Test
    fun `isStakingSupported returns false for chains not in the allowlist`() {
        // THORChain uses its own bond model — even though it's a Cosmos-SDK chain, it must not slip
        // into the staking allowlist by accident.
        assertFalse(CosmosStakingConfig.isStakingSupported(Chain.ThorChain))
        assertFalse(CosmosStakingConfig.isStakingSupported(Chain.GaiaChain))
        assertFalse(CosmosStakingConfig.isStakingSupported(Chain.Kujira))
        assertFalse(CosmosStakingConfig.isStakingSupported(Chain.Osmosis))
        assertFalse(CosmosStakingConfig.isStakingSupported(Chain.Ethereum))
    }

    @Test
    fun `entryFor throws CosmosStakingConfigException for unsupported chain`() {
        val ex =
            assertFailsWith<CosmosStakingConfigException> {
                CosmosStakingConfig.entryFor(Chain.GaiaChain)
            }
        assertEquals(Chain.GaiaChain, ex.chain)
    }

    @Test
    fun `Convenience accessors delegate to the table entry`() {
        assertEquals("terravaloper", CosmosStakingConfig.valoperHrpFor(Chain.Terra))
        assertEquals(10_000L, CosmosStakingConfig.feeAmountFor(Chain.Terra))
        assertEquals(21, CosmosStakingConfig.unbondingDaysFor(Chain.Terra))
    }

    @Test
    fun `Table entry exposes the expected Terra config`() {
        val entry = CosmosStakingConfig.entryFor(Chain.Terra)
        assertEquals("phoenix-1", entry.chainId)
        assertEquals("uluna", entry.bondDenom)
        assertEquals("uluna", entry.feeDenom)
        assertEquals(400_000L, entry.gasLimit)
    }
}
