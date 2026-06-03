package com.vultisig.wallet.data.blockchain.cosmos.staking

import com.vultisig.wallet.data.models.Chain
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * Mirrors iOS `CosmosStakingAPYResolverTests.swift` — pins the per-validator APY formula, the clamp
 * behavior, the baseline fallback, the cache TTL, and in-flight coalescing.
 */
class CosmosStakingAPYResolverTests {

    private fun service() = mockk<CosmosStakingService>(relaxed = true)

    private fun resolver(
        svc: CosmosStakingService,
        now: () -> Long = { 0L },
    ): CosmosStakingAPYResolverImpl = CosmosStakingAPYResolverImpl(svc).also { it.clock = now }

    // MARK: - computeValidatorAPY

    @Test
    fun `computeValidatorAPY applies the full formula`() {
        // inflation 0.07, bondedRatio 0.5, communityTax 0.02, commission 0.05
        // chainBase = (1 - 0.02) × (0.07 / 0.5) = 0.98 × 0.14 = 0.1372
        // apy = 0.1372 × (1 - 0.05) = 0.13034
        val data =
            CosmosChainApyData(
                inflation = BigDecimal("0.07"),
                bondedRatio = BigDecimal("0.5"),
                communityTax = BigDecimal("0.02"),
            )
        val apy =
            CosmosStakingAPYResolver.computeValidatorAPY(data, commission = BigDecimal("0.05"))
        assertNotNull(apy)
        assertEquals(0, BigDecimal("0.13034").compareTo(apy.stripTrailingZeros()))
    }

    @Test
    fun `computeValidatorAPY collapses to null when inflation is zero`() {
        val data =
            CosmosChainApyData(
                inflation = BigDecimal.ZERO,
                bondedRatio = BigDecimal("0.5"),
                communityTax = BigDecimal("0.02"),
            )
        assertNull(CosmosStakingAPYResolver.computeValidatorAPY(data, BigDecimal("0.05")))
    }

    @Test
    fun `computeValidatorAPY collapses to null when bonded ratio is zero`() {
        val data =
            CosmosChainApyData(
                inflation = BigDecimal("0.07"),
                bondedRatio = BigDecimal.ZERO,
                communityTax = BigDecimal("0.02"),
            )
        assertNull(CosmosStakingAPYResolver.computeValidatorAPY(data, BigDecimal("0.05")))
    }

    @Test
    fun `computeValidatorAPY clamps out-of-range inputs`() {
        // commission > 1 clamps to 1 → (1 - 1) = 0 multiplier → apy 0 → null
        val data =
            CosmosChainApyData(
                inflation = BigDecimal("0.07"),
                bondedRatio = BigDecimal("0.5"),
                communityTax = BigDecimal("0.02"),
            )
        assertNull(CosmosStakingAPYResolver.computeValidatorAPY(data, BigDecimal("1.5")))
    }

    // MARK: - baselineFallback

    @Test
    fun `baselineFallback is 12_5pct for LUNA and null for LUNC`() {
        val r = resolver(service())
        assertEquals(0, BigDecimal("0.125").compareTo(r.baselineFallback(Chain.Terra)))
        assertNull(r.baselineFallback(Chain.TerraClassic))
    }

    // MARK: - chainApy fan-out

    @Test
    fun `chainApy folds the 4 LCD reads into chain data`() = runTest {
        val svc = service()
        coEvery { svc.fetchMintInflation(Chain.Terra) } returns CosmosMintInflationResponse("0.07")
        coEvery { svc.fetchStakingPool(Chain.Terra) } returns
            CosmosStakingPoolResponse(
                CosmosStakingPoolResponse.Pool(notBondedTokens = "0", bondedTokens = "500")
            )
        coEvery { svc.fetchBankSupplyByDenom(Chain.Terra, "uluna") } returns
            CosmosBankSupplyResponse(CosmosStakingCoin(denom = "uluna", amount = "1000"))
        coEvery { svc.fetchDistributionParams(Chain.Terra) } returns
            CosmosDistributionParamsResponse(
                CosmosDistributionParamsResponse.Params(communityTax = "0.02")
            )

        val data = resolver(svc).chainApy(Chain.Terra, "uluna")
        assertNotNull(data)
        assertEquals(0, BigDecimal("0.07").compareTo(data.inflation))
        // bondedRatio = 500 / 1000 = 0.5
        assertEquals(0, BigDecimal("0.5").compareTo(data.bondedRatio))
        assertEquals(0, BigDecimal("0.02").compareTo(data.communityTax))
    }

    @Test
    fun `chainApy returns null when any LCD call fails`() = runTest {
        val svc = service()
        coEvery { svc.fetchMintInflation(Chain.Terra) } throws RuntimeException("502 bad gateway")
        coEvery { svc.fetchStakingPool(Chain.Terra) } returns
            CosmosStakingPoolResponse(
                CosmosStakingPoolResponse.Pool(notBondedTokens = "0", bondedTokens = "500")
            )
        coEvery { svc.fetchBankSupplyByDenom(Chain.Terra, "uluna") } returns
            CosmosBankSupplyResponse(CosmosStakingCoin(denom = "uluna", amount = "1000"))
        coEvery { svc.fetchDistributionParams(Chain.Terra) } returns
            CosmosDistributionParamsResponse(
                CosmosDistributionParamsResponse.Params(communityTax = "0.02")
            )

        assertNull(resolver(svc).chainApy(Chain.Terra, "uluna"))
    }

    @Test
    fun `chainApy serves from cache within the TTL without re-fetching`() = runTest {
        val svc = service()
        coEvery { svc.fetchMintInflation(Chain.Terra) } returns CosmosMintInflationResponse("0.07")
        coEvery { svc.fetchStakingPool(Chain.Terra) } returns
            CosmosStakingPoolResponse(
                CosmosStakingPoolResponse.Pool(notBondedTokens = "0", bondedTokens = "500")
            )
        coEvery { svc.fetchBankSupplyByDenom(Chain.Terra, "uluna") } returns
            CosmosBankSupplyResponse(CosmosStakingCoin(denom = "uluna", amount = "1000"))
        coEvery { svc.fetchDistributionParams(Chain.Terra) } returns
            CosmosDistributionParamsResponse(
                CosmosDistributionParamsResponse.Params(communityTax = "0.02")
            )

        var fakeNow = 0L
        val r = resolver(svc) { fakeNow }
        r.chainApy(Chain.Terra, "uluna")
        fakeNow = 60_000L // 1 minute < 5 minute TTL
        r.chainApy(Chain.Terra, "uluna")

        // Each endpoint hit exactly once — second call served from cache.
        coVerify(exactly = 1) { svc.fetchMintInflation(Chain.Terra) }
    }

    @Test
    fun `chainApy re-fetches after the TTL expires`() = runTest {
        val svc = service()
        coEvery { svc.fetchMintInflation(Chain.Terra) } returns CosmosMintInflationResponse("0.07")
        coEvery { svc.fetchStakingPool(Chain.Terra) } returns
            CosmosStakingPoolResponse(
                CosmosStakingPoolResponse.Pool(notBondedTokens = "0", bondedTokens = "500")
            )
        coEvery { svc.fetchBankSupplyByDenom(Chain.Terra, "uluna") } returns
            CosmosBankSupplyResponse(CosmosStakingCoin(denom = "uluna", amount = "1000"))
        coEvery { svc.fetchDistributionParams(Chain.Terra) } returns
            CosmosDistributionParamsResponse(
                CosmosDistributionParamsResponse.Params(communityTax = "0.02")
            )

        var fakeNow = 0L
        val r = resolver(svc) { fakeNow }
        r.chainApy(Chain.Terra, "uluna")
        fakeNow = 6L * 60_000L // 6 minutes > 5 minute TTL
        r.chainApy(Chain.Terra, "uluna")

        coVerify(exactly = 2) { svc.fetchMintInflation(Chain.Terra) }
    }

    @Test
    fun `bondedRatio is zero when supply is zero`() = runTest {
        val svc = service()
        coEvery { svc.fetchMintInflation(Chain.Terra) } returns CosmosMintInflationResponse("0.07")
        coEvery { svc.fetchStakingPool(Chain.Terra) } returns
            CosmosStakingPoolResponse(
                CosmosStakingPoolResponse.Pool(notBondedTokens = "0", bondedTokens = "500")
            )
        coEvery { svc.fetchBankSupplyByDenom(Chain.Terra, "uluna") } returns
            CosmosBankSupplyResponse(CosmosStakingCoin(denom = "uluna", amount = "0"))
        coEvery { svc.fetchDistributionParams(Chain.Terra) } returns
            CosmosDistributionParamsResponse(
                CosmosDistributionParamsResponse.Params(communityTax = "0.02")
            )

        val data = resolver(svc).chainApy(Chain.Terra, "uluna")
        assertNotNull(data)
        assertTrue(data.bondedRatio.compareTo(BigDecimal.ZERO) == 0)
    }
}
