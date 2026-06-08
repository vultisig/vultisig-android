package com.vultisig.wallet.data.blockchain.cosmos.staking

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.utils.bodyOrThrow
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import javax.inject.Inject

/**
 * Read-side service for Cosmos-SDK x/staking + x/distribution LCD endpoints. Port of iOS
 * `CosmosStakingService.swift` (vultisig-ios PR #4432). View-models call only the service.
 *
 * Mirrors the SDK consumer at
 * `vultisig-sdk/packages/core/chain/chains/cosmos/staking/lcdQueries.ts`.
 */
interface CosmosStakingService {
    suspend fun fetchDelegations(chain: Chain, address: String): List<CosmosDelegation>

    suspend fun fetchUnbondingDelegations(
        chain: Chain,
        address: String,
    ): List<CosmosUnbondingDelegation>

    suspend fun fetchDelegatorRewards(chain: Chain, address: String): CosmosDelegatorRewards

    suspend fun fetchValidators(chain: Chain): List<CosmosValidator>

    suspend fun fetchRedelegations(chain: Chain, address: String): List<CosmosRedelegationEntry>

    /** `/cosmos/mint/v1beta1/inflation` — annualized inflation as a `cosmos.Dec` string. */
    suspend fun fetchMintInflation(chain: Chain): CosmosMintInflationResponse

    /** `/cosmos/staking/v1beta1/pool` — bonded / not-bonded supply totals in base units. */
    suspend fun fetchStakingPool(chain: Chain): CosmosStakingPoolResponse

    /**
     * `/cosmos/bank/v1beta1/supply/by_denom?denom={denom}` — total supply of the bond denom in base
     * units. Pair with the staking pool to derive `bondedRatio`.
     */
    suspend fun fetchBankSupplyByDenom(chain: Chain, denom: String): CosmosBankSupplyResponse

    /**
     * `/cosmos/distribution/v1beta1/params` — community-pool skim before per-validator commission.
     */
    suspend fun fetchDistributionParams(chain: Chain): CosmosDistributionParamsResponse
}

internal class CosmosStakingServiceImpl @Inject constructor(private val httpClient: HttpClient) :
    CosmosStakingService {

    override suspend fun fetchDelegations(chain: Chain, address: String): List<CosmosDelegation> {
        val baseUrl = baseUrlFor(chain)
        return httpClient
            .get("$baseUrl/cosmos/staking/v1beta1/delegations/$address") {
                // Without an explicit limit the LCD pages at 100, so a delegator with more than
                // 100 positions silently loses the rest from the staked total, the positions list
                // and the claim candidates. Mirror fetchValidators' single-page-with-headroom cap.
                parameter("pagination.limit", DELEGATOR_PAGE_LIMIT)
            }
            .bodyOrThrow<CosmosDelegationResponse>()
            .toDelegations()
    }

    override suspend fun fetchUnbondingDelegations(
        chain: Chain,
        address: String,
    ): List<CosmosUnbondingDelegation> {
        val baseUrl = baseUrlFor(chain)
        return httpClient
            .get("$baseUrl/cosmos/staking/v1beta1/delegators/$address/unbonding_delegations") {
                parameter("pagination.limit", DELEGATOR_PAGE_LIMIT)
            }
            .bodyOrThrow<CosmosUnbondingDelegationResponse>()
            .toUnbondingDelegations()
    }

    override suspend fun fetchDelegatorRewards(
        chain: Chain,
        address: String,
    ): CosmosDelegatorRewards {
        val baseUrl = baseUrlFor(chain)
        return httpClient
            .get("$baseUrl/cosmos/distribution/v1beta1/delegators/$address/rewards") {
                parameter("pagination.limit", DELEGATOR_PAGE_LIMIT)
            }
            .bodyOrThrow<CosmosDelegatorRewardsResponse>()
            .toRewards()
    }

    override suspend fun fetchValidators(chain: Chain): List<CosmosValidator> {
        val baseUrl = baseUrlFor(chain)
        return httpClient
            .get("$baseUrl/cosmos/staking/v1beta1/validators") {
                // Hardcoded caps mirror iOS / agent app. Terra has ~130 active validators today;
                // 300 keeps headroom without paging. If a chain ever exceeds 300 the resolver
                // layer will need to page — left as a follow-up.
                parameter("status", "BOND_STATUS_BONDED")
                parameter("pagination.limit", VALIDATOR_PAGE_LIMIT)
            }
            .bodyOrThrow<CosmosValidatorListResponse>()
            .toValidators()
    }

    override suspend fun fetchRedelegations(
        chain: Chain,
        address: String,
    ): List<CosmosRedelegationEntry> {
        val baseUrl = baseUrlFor(chain)
        return httpClient
            .get("$baseUrl/cosmos/staking/v1beta1/delegators/$address/redelegations") {
                parameter("pagination.limit", DELEGATOR_PAGE_LIMIT)
            }
            .bodyOrThrow<CosmosRedelegationResponse>()
            .toRedelegations()
    }

    override suspend fun fetchMintInflation(chain: Chain): CosmosMintInflationResponse {
        val baseUrl = baseUrlFor(chain)
        return httpClient
            .get("$baseUrl/cosmos/mint/v1beta1/inflation")
            .bodyOrThrow<CosmosMintInflationResponse>()
    }

    override suspend fun fetchStakingPool(chain: Chain): CosmosStakingPoolResponse {
        val baseUrl = baseUrlFor(chain)
        return httpClient
            .get("$baseUrl/cosmos/staking/v1beta1/pool")
            .bodyOrThrow<CosmosStakingPoolResponse>()
    }

    override suspend fun fetchBankSupplyByDenom(
        chain: Chain,
        denom: String,
    ): CosmosBankSupplyResponse {
        val baseUrl = baseUrlFor(chain)
        return httpClient
            .get("$baseUrl/cosmos/bank/v1beta1/supply/by_denom") { parameter("denom", denom) }
            .bodyOrThrow<CosmosBankSupplyResponse>()
    }

    override suspend fun fetchDistributionParams(chain: Chain): CosmosDistributionParamsResponse {
        val baseUrl = baseUrlFor(chain)
        return httpClient
            .get("$baseUrl/cosmos/distribution/v1beta1/params")
            .bodyOrThrow<CosmosDistributionParamsResponse>()
    }

    private fun baseUrlFor(chain: Chain): String =
        LCD_HOSTS[chain] ?: throw CosmosStakingConfigException(chain)

    companion object {
        private const val VALIDATOR_PAGE_LIMIT = 300

        /**
         * Single-page cap for per-delegator reads (delegations, unbondings, rewards,
         * redelegations). The LCD default page size is 100; a delegator past that silently loses
         * positions from the staked total and the claim list. 1000 keeps a single round-trip with
         * ample headroom — a delegator with >1000 distinct validators is not a realistic shape, and
         * full `pagination.next_key` cursoring is left as a follow-up if one ever appears.
         */
        private const val DELEGATOR_PAGE_LIMIT = 1000

        /**
         * LCD hosts mirror the values already defined in [com.vultisig.wallet.data.api.CosmosApi]
         * for Terra / TerraClassic. Centralising both call sites onto a single host table is a
         * follow-up — left here to keep PR1 surface contained to the staking module.
         */
        private val LCD_HOSTS: Map<Chain, String> =
            mapOf(
                Chain.Terra to "https://terra-lcd.publicnode.com",
                Chain.TerraClassic to "https://terra-classic-lcd.publicnode.com",
                // QBTC's Cosmos REST gateway, mirroring the host already wired in CosmosApi.
                Chain.Qbtc to "https://api.vultisig.com/qbtc-rpc",
            )
    }
}
