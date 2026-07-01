package com.vultisig.wallet.ui.models.defi

import com.vultisig.wallet.data.api.chains.ton.TonStakingPoolEntryJson
import com.vultisig.wallet.data.models.Coins
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

internal class TonStakeViewModelTest {

    private val decimals = Coins.Ton.TON.decimal

    private fun entry(
        address: String,
        apy: Double,
        verified: Boolean = true,
        implementation: String? = "whales",
        minStake: Long = 50_000_000_000L,
        current: Int? = null,
        max: Int? = null,
    ) =
        TonStakingPoolEntryJson(
            address = address,
            name = address,
            apy = apy,
            minStake = minStake,
            verified = verified,
            currentNominators = current,
            maxNominators = max,
            implementation = implementation,
        )

    @Test
    fun `keeps only verified nominator pools`() {
        val result =
            TonStakeViewModel.filterAndSortPools(
                listOf(
                    entry("whales-ok", apy = 5.0, implementation = "whales"),
                    entry("tf-ok", apy = 4.0, implementation = "tf"),
                    entry("liquid-excluded", apy = 9.0, implementation = "liquidTF"),
                    entry("unknown-excluded", apy = 8.0, implementation = "somethingElse"),
                    entry("unverified-excluded", apy = 7.0, verified = false),
                ),
                decimals,
            )

        assertEquals(listOf("whales-ok", "tf-ok"), result.map { it.address })
    }

    @Test
    fun `sorts by APY descending`() {
        val result =
            TonStakeViewModel.filterAndSortPools(
                listOf(entry("low", apy = 2.0), entry("high", apy = 12.5), entry("mid", apy = 7.0)),
                decimals,
            )

        assertEquals(listOf("high", "mid", "low"), result.map { it.address })
    }

    @Test
    fun `excludes pools at nominator capacity but keeps those with room or unknown counts`() {
        val result =
            TonStakeViewModel.filterAndSortPools(
                listOf(
                    entry("full", apy = 9.0, current = 40, max = 40),
                    entry("room", apy = 8.0, current = 10, max = 40),
                    entry("unknown", apy = 7.0, current = null, max = null),
                ),
                decimals,
            )

        assertEquals(listOf("room", "unknown"), result.map { it.address })
    }

    @Test
    fun `scales min stake from nanotons to human-decimal TON`() {
        val result =
            TonStakeViewModel.filterAndSortPools(
                listOf(entry("p", apy = 5.0, minStake = 50_000_000_000L)),
                decimals,
            )

        assertEquals("50", result.single().minStake.stripTrailingZeros().toPlainString())
    }

    @Test
    fun `display name falls back to a short address when unnamed`() {
        val pool =
            TonPoolUiModel(
                address = "0:a45b17f28409229b78360e3290420f13e4fe20f90d7e2bf8c4ac6703259e22fa",
                name = "",
                apy = 5.0,
                minStake = BigDecimal.TEN,
                verified = true,
            )

        assertTrue(pool.displayName.contains("…"))
    }
}
