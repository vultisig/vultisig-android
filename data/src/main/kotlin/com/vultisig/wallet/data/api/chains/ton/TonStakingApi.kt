package com.vultisig.wallet.data.api.chains.ton

import com.vultisig.wallet.data.utils.NetworkException
import com.vultisig.wallet.data.utils.bodyOrThrow
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode
import javax.inject.Inject
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Client for tonapi.io nominator-pool staking endpoints.
 *
 * These endpoints are served exclusively by `tonapi.io` (the Vultisig proxy `/ton/v3/wallet`
 * `pools` field does not populate), so they are hit directly rather than through [TonApi]'s
 * `api.vultisig.com` host.
 */
interface TonStakingApi {

    /**
     * List of known staking pools, backing the pool picker. `include_unverified=false` is sent so
     * only verified pools are returned; callers re-filter defensively to nominator implementations.
     */
    suspend fun getStakingPools(): List<TonStakingPoolEntryJson>

    /**
     * Computed pool info (name, APY, min stake, implementation, cycle end) for a single pool. Used
     * to decorate a held position and to gate a deposit/withdraw. Returns `null` only when the pool
     * is genuinely unknown (HTTP 404); transient/network failures propagate so a caller can tell an
     * outage apart from an unsupported pool.
     */
    suspend fun getStakingPool(poolAddress: String): TonStakingPoolInfoJson?

    /**
     * Nominator-pool positions for an account. Authoritative source for staked positions; empty
     * participation returns an empty list.
     */
    suspend fun getNominatorPools(address: String): List<TonAccountStakingInfoJson>
}

internal class TonStakingApiImpl @Inject constructor(private val http: HttpClient) : TonStakingApi {

    override suspend fun getStakingPools(): List<TonStakingPoolEntryJson> =
        http
            .get("$BASE_URL/v2/staking/pools") { parameter("include_unverified", false) }
            .bodyOrThrow<TonStakingPoolsResponseJson>()
            .pools

    override suspend fun getStakingPool(poolAddress: String): TonStakingPoolInfoJson? =
        try {
            http
                .get("$BASE_URL/v2/staking/pool/$poolAddress")
                .bodyOrThrow<TonStakingPoolResponseJson>()
                .pool
        } catch (e: NetworkException) {
            // A 404 means the pool isn't known to tonapi — surface that as null; let every other
            // failure (timeout, 5xx, transport) propagate so callers don't mistake an outage for an
            // unsupported pool.
            if (e.httpStatusCode == HttpStatusCode.NotFound.value) null else throw e
        }

    override suspend fun getNominatorPools(address: String): List<TonAccountStakingInfoJson> =
        http
            .get("$BASE_URL/v2/staking/nominator/$address/pools")
            .bodyOrThrow<TonAccountStakingResponseJson>()
            .pools

    private companion object {
        const val BASE_URL = "https://tonapi.io"
    }
}

@Serializable
data class TonStakingPoolsResponseJson(
    @SerialName("pools") val pools: List<TonStakingPoolEntryJson> = emptyList()
)

/**
 * A single pool from the list endpoint. `apy` is a percentage (e.g. `13.27` = 13.27%) and
 * `minStake` is in nanotons. Picker-critical fields are required; the rest are optional so a shape
 * drift degrades rather than dropping the whole list.
 */
@Serializable
data class TonStakingPoolEntryJson(
    @SerialName("address") val address: String,
    @SerialName("name") val name: String,
    @SerialName("apy") val apy: Double,
    @SerialName("min_stake") val minStake: Long,
    @SerialName("verified") val verified: Boolean = false,
    @SerialName("current_nominators") val currentNominators: Int? = null,
    @SerialName("max_nominators") val maxNominators: Int? = null,
    @SerialName("implementation") val implementation: String? = null,
)

@Serializable
data class TonStakingPoolResponseJson(@SerialName("pool") val pool: TonStakingPoolInfoJson? = null)

/**
 * Computed info for a single pool. `apy` is a percentage; `cycleEnd` is a Unix timestamp (seconds)
 * marking when the current validation cycle ends — i.e. when a pending withdrawal becomes
 * claimable. All fields are optional so a partial response degrades gracefully.
 */
@Serializable
data class TonStakingPoolInfoJson(
    @SerialName("address") val address: String? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("apy") val apy: Double? = null,
    @SerialName("min_stake") val minStake: Long? = null,
    @SerialName("implementation") val implementation: String? = null,
    @SerialName("cycle_end") val cycleEnd: Long? = null,
)

@Serializable
data class TonAccountStakingResponseJson(
    @SerialName("pools") val pools: List<TonAccountStakingInfoJson> = emptyList()
)

/**
 * A single nominator-pool position for an account. All amounts are nanotons. `amount` is the active
 * stake; `pendingDeposit` is a just-placed stake awaiting the next cycle (must surface so a fresh
 * stake is visible immediately); `pendingWithdraw`/`readyWithdraw` indicate a withdrawal in flight.
 */
@Serializable
data class TonAccountStakingInfoJson(
    @SerialName("pool") val pool: String,
    @SerialName("amount") val amount: Long = 0,
    @SerialName("pending_deposit") val pendingDeposit: Long = 0,
    @SerialName("pending_withdraw") val pendingWithdraw: Long = 0,
    @SerialName("ready_withdraw") val readyWithdraw: Long = 0,
)
