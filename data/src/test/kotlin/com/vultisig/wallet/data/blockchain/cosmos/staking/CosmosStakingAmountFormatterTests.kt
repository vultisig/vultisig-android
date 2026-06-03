package com.vultisig.wallet.data.blockchain.cosmos.staking

import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

/** Mirrors iOS `CosmosStakingAmountFormatterTests.swift`. */
class CosmosStakingAmountFormatterTests {

    @Test
    fun `1_5 LUNA at 6 decimals encodes to 1_500_000 base units`() {
        assertEquals("1500000", CosmosStakingAmountFormatter.baseUnitsString("1.5", 6))
    }

    @Test
    fun `comma separator is normalized to dot`() {
        assertEquals("1500000", CosmosStakingAmountFormatter.baseUnitsString("1,5", 6))
    }

    @Test
    fun `excess decimals are rounded DOWN`() {
        // 1.5555559 × 10^6 = 1_555_555.9 → DOWN → 1_555_555
        // Truncating (not rounding to nearest) ensures we never silently over-stake.
        assertEquals("1555555", CosmosStakingAmountFormatter.baseUnitsString("1.5555559", 6))
    }

    @Test
    fun `whole-number inputs work without a decimal point`() {
        assertEquals("100000000", CosmosStakingAmountFormatter.baseUnitsString("100", 6))
    }

    @Test
    fun `empty input returns 0 rather than throwing`() {
        assertEquals("0", CosmosStakingAmountFormatter.baseUnitsString("", 6))
    }

    @Test
    fun `garbage input returns 0 rather than throwing`() {
        assertEquals("0", CosmosStakingAmountFormatter.baseUnitsString("not a number", 6))
    }

    @Test
    fun `zero input encodes to zero`() {
        assertEquals("0", CosmosStakingAmountFormatter.baseUnitsString("0", 6))
    }

    @Test
    fun `subbaseunit input rounds down to zero`() {
        // 0.0000001 × 10^6 = 0.1 → DOWN → 0. User entered less than one base unit.
        assertEquals("0", CosmosStakingAmountFormatter.baseUnitsString("0.0000001", 6))
    }
}
