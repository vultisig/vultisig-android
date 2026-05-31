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
}

internal class CosmosStakingServiceImpl @Inject constructor(private val httpClient: HttpClient) :
    CosmosStakingService {

    override suspend fun fetchDelegations(chain: Chain, address: String): List<CosmosDelegation> {
        val baseUrl = baseUrlFor(chain)
        return httpClient
            .get("$baseUrl/cosmos/staking/v1beta1/delegations/$address")
            .bodyOrThrow<CosmosDelegationResponse>()
            .toDelegations()
    }

    override suspend fun fetchUnbondingDelegations(
        chain: Chain,
        address: String,
    ): List<CosmosUnbondingDelegation> {
        val baseUrl = baseUrlFor(chain)
        return httpClient
            .get("$baseUrl/cosmos/staking/v1beta1/delegators/$address/unbonding_delegations")
            .bodyOrThrow<CosmosUnbondingDelegationResponse>()
            .toUnbondingDelegations()
    }

    override suspend fun fetchDelegatorRewards(
        chain: Chain,
        address: String,
    ): CosmosDelegatorRewards {
        val baseUrl = baseUrlFor(chain)
        return httpClient
            .get("$baseUrl/cosmos/distribution/v1beta1/delegators/$address/rewards")
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
            .get("$baseUrl/cosmos/staking/v1beta1/delegators/$address/redelegations")
            .bodyOrThrow<CosmosRedelegationResponse>()
            .toRedelegations()
    }

    private fun baseUrlFor(chain: Chain): String =
        LCD_HOSTS[chain] ?: throw CosmosStakingConfigException(chain)

    companion object {
        private const val VALIDATOR_PAGE_LIMIT = 300

        /**
         * LCD hosts mirror the values already defined in [com.vultisig.wallet.data.api.CosmosApi]
         * for Terra / TerraClassic. Centralising both call sites onto a single host table is a
         * follow-up — left here to keep PR1 surface contained to the staking module.
         */
        private val LCD_HOSTS: Map<Chain, String> =
            mapOf(
                Chain.Terra to "https://terra-lcd.publicnode.com",
                Chain.TerraClassic to "https://terra-classic-lcd.publicnode.com",
            )
    }
}
