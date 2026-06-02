package com.vultisig.wallet.data.blockchain.cosmos.staking

import java.math.BigDecimal
import java.time.Instant
import java.time.format.DateTimeFormatter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Read-side DTOs for Cosmos-SDK x/staking + x/distribution LCD endpoints. Shape mirrors iOS
 * `CosmosStakingDTOs.swift` (vultisig-ios PR #4432), which in turn mirrors the SDK's
 * `lcdQueries.ts` types.
 */

// MARK: - Wire-level coin

@Serializable data class CosmosStakingCoin(val denom: String, val amount: String)

// MARK: - Delegations

data class CosmosDelegation(
    val validatorAddress: String,
    val balance: CosmosStakingCoin,
    /**
     * `shares` is a `cosmos.Dec` (18-decimal fixed-point) string on the wire. Kept as the raw
     * string so UI consumers convert via [BigDecimal] when needed.
     */
    val shares: String,
)

@Serializable
data class CosmosDelegationResponse(
    @SerialName("delegation_responses") val delegationResponses: List<DelegationResponseEntry>
) {
    @Serializable
    data class DelegationResponseEntry(
        val delegation: DelegationInner,
        val balance: CosmosStakingCoin,
    ) {
        @Serializable
        data class DelegationInner(
            @SerialName("delegator_address") val delegatorAddress: String,
            @SerialName("validator_address") val validatorAddress: String,
            val shares: String,
        )
    }

    fun toDelegations(): List<CosmosDelegation> =
        delegationResponses.map { entry ->
            CosmosDelegation(
                validatorAddress = entry.delegation.validatorAddress,
                balance = entry.balance,
                shares = entry.delegation.shares,
            )
        }
}

// MARK: - Unbonding delegations

data class CosmosUnbondingEntry(
    val creationHeight: Long,
    val completionTime: Instant,
    val initialBalance: BigDecimal,
    val balance: BigDecimal,
)

data class CosmosUnbondingDelegation(
    val validatorAddress: String,
    val entries: List<CosmosUnbondingEntry>,
)

@Serializable
data class CosmosUnbondingDelegationResponse(
    @SerialName("unbonding_responses") val unbondingResponses: List<UnbondingDelegationEntry>
) {
    @Serializable
    data class UnbondingDelegationEntry(
        @SerialName("validator_address") val validatorAddress: String,
        val entries: List<WireEntry>,
    ) {
        @Serializable
        data class WireEntry(
            @SerialName("creation_height") val creationHeight: String,
            @SerialName("completion_time") val completionTime: String,
            @SerialName("initial_balance") val initialBalance: String,
            val balance: String,
        )
    }

    fun toUnbondingDelegations(): List<CosmosUnbondingDelegation> =
        unbondingResponses.map { wire ->
            CosmosUnbondingDelegation(
                validatorAddress = wire.validatorAddress,
                entries = wire.entries.mapNotNull { entry -> makeEntry(entry) },
            )
        }

    private fun makeEntry(wire: UnbondingDelegationEntry.WireEntry): CosmosUnbondingEntry? {
        val creationHeight = wire.creationHeight.toLongOrNull() ?: return null
        val completion = CosmosStakingDateParser.parse(wire.completionTime) ?: return null
        val initialBalance = wire.initialBalance.toBigDecimalOrNull() ?: return null
        val balance = wire.balance.toBigDecimalOrNull() ?: return null
        return CosmosUnbondingEntry(
            creationHeight = creationHeight,
            completionTime = completion,
            initialBalance = initialBalance,
            balance = balance,
        )
    }
}

// MARK: - Rewards

data class CosmosDelegatorReward(
    val validatorAddress: String,
    /**
     * Multi-asset rewards are technically possible (a chain that rewards in multiple denoms). v1
     * aggregates by denom downstream and surfaces only the bond denom total; the full list is
     * preserved here so v1.1 can expand the per-validator row without a DTO change.
     */
    val reward: List<CosmosStakingCoin>,
)

data class CosmosDelegatorRewards(
    val rewards: List<CosmosDelegatorReward>,
    val total: List<CosmosStakingCoin>,
)

@Serializable
data class CosmosDelegatorRewardsResponse(
    /**
     * Both `rewards` and `total` arrive as `null` on some LCD firmwares when the delegator has
     * never accrued any rewards. The SDK falls back to `[]`; we do the same.
     */
    val rewards: List<WireReward>? = null,
    val total: List<WireCoin>? = null,
) {
    @Serializable
    data class WireReward(
        @SerialName("validator_address") val validatorAddress: String,
        val reward: List<WireCoin>? = null,
    )

    @Serializable
    data class WireCoin(
        val denom: String,
        /**
         * LCD returns reward amounts as `cosmos.Dec` strings — they frequently include a fractional
         * component because rewards accrue per-block. Kept as raw string at the DTO boundary; the
         * position-aggregation layer converts.
         */
        val amount: String,
    )

    fun toRewards(): CosmosDelegatorRewards {
        val rewardsOut =
            (rewards ?: emptyList()).map { wire ->
                CosmosDelegatorReward(
                    validatorAddress = wire.validatorAddress,
                    reward =
                        (wire.reward ?: emptyList()).map {
                            CosmosStakingCoin(denom = it.denom, amount = it.amount)
                        },
                )
            }
        val totalOut =
            (total ?: emptyList()).map { CosmosStakingCoin(denom = it.denom, amount = it.amount) }
        return CosmosDelegatorRewards(rewards = rewardsOut, total = totalOut)
    }
}

// MARK: - Validators

data class CosmosValidator(
    val operatorAddress: String,
    val moniker: String,
    /**
     * Commission rate as a fixed-point `cosmos.Dec` (1.0 = 100%). Display layer multiplies by 100
     * to render "5%". Kept as [BigDecimal] to avoid floating-point drift in sort comparisons.
     */
    val commission: BigDecimal,
    val jailed: Boolean,
    val status: Status,
    /**
     * Voting power proxied via `tokens` (uint string of bond-denom base units). Sufficient for
     * sort-by-power without pulling staking-pool totals.
     */
    val votingPower: BigDecimal,
    /**
     * Keybase identity advertised in the validator description. When set, resolves to a
     * profile-picture URL via the Keybase user lookup; absent or unresolved validators fall back to
     * the deterministic monogram avatar.
     */
    val identity: String? = null,
) {
    enum class Status {
        Bonded,
        Unbonded,
        Unbonding,
        Unspecified,
    }
}

@Serializable
data class CosmosValidatorListResponse(val validators: List<WireValidator>) {
    @Serializable
    data class WireValidator(
        @SerialName("operator_address") val operatorAddress: String,
        val jailed: Boolean? = null,
        val status: String,
        val tokens: String,
        val description: WireDescription,
        val commission: WireCommission,
    ) {
        @Serializable
        data class WireDescription(
            val moniker: String,
            /**
             * Optional Keybase identity (16-hex string by convention). Many validators omit it —
             * keep the field optional so a missing `identity` doesn't fail the entire decode.
             */
            val identity: String? = null,
        )

        @Serializable
        data class WireCommission(@SerialName("commission_rates") val commissionRates: WireRates) {
            @Serializable data class WireRates(val rate: String)
        }
    }

    fun toValidators(): List<CosmosValidator> =
        validators.map { wire ->
            val identity = wire.description.identity?.takeIf { it.isNotEmpty() }
            // Commission parse-failure defaults to ONE (treat as 100%, worst APY, sinks the
            // validator to the bottom of the sort) rather than ZERO. A malformed rate falling to
            // ZERO would invert the APY ranking and push the broken validator to the TOP of the
            // picker — exactly the validator we want to bury, not surface.
            val commission =
                wire.commission.commissionRates.rate.toBigDecimalOrNull() ?: BigDecimal.ONE
            CosmosValidator(
                operatorAddress = wire.operatorAddress,
                moniker = wire.description.moniker,
                commission = commission,
                jailed = wire.jailed ?: false,
                status = mapStatus(wire.status),
                votingPower = wire.tokens.toBigDecimalOrNull() ?: BigDecimal.ZERO,
                identity = identity,
            )
        }

    private fun mapStatus(raw: String): CosmosValidator.Status =
        when (raw) {
            "BOND_STATUS_BONDED" -> CosmosValidator.Status.Bonded
            "BOND_STATUS_UNBONDED" -> CosmosValidator.Status.Unbonded
            "BOND_STATUS_UNBONDING" -> CosmosValidator.Status.Unbonding
            else -> CosmosValidator.Status.Unspecified
        }
}

// MARK: - Redelegations (used by the cooldown gate)

data class CosmosRedelegationEntry(
    val srcValidator: String,
    val dstValidator: String,
    val completionTime: Instant,
)

@Serializable
data class CosmosRedelegationResponse(
    @SerialName("redelegation_responses") val redelegationResponses: List<Entry>
) {
    @Serializable
    data class Entry(val redelegation: Redelegation, val entries: List<WireEntry>) {
        @Serializable
        data class Redelegation(
            @SerialName("validator_src_address") val validatorSrcAddress: String,
            @SerialName("validator_dst_address") val validatorDstAddress: String,
        )

        @Serializable
        data class WireEntry(
            @SerialName("redelegation_entry") val redelegationEntry: RedelegationEntry
        ) {
            @Serializable
            data class RedelegationEntry(@SerialName("completion_time") val completionTime: String)
        }
    }

    fun toRedelegations(): List<CosmosRedelegationEntry> {
        val out = mutableListOf<CosmosRedelegationEntry>()
        for (entry in redelegationResponses) {
            for (wire in entry.entries) {
                val date =
                    CosmosStakingDateParser.parse(wire.redelegationEntry.completionTime) ?: continue
                out.add(
                    CosmosRedelegationEntry(
                        srcValidator = entry.redelegation.validatorSrcAddress,
                        dstValidator = entry.redelegation.validatorDstAddress,
                        completionTime = date,
                    )
                )
            }
        }
        return out
    }
}

// MARK: - Shared date parser

/**
 * LCD wire dates arrive in two shapes — RFC3339 with fractional seconds
 * (`2026-06-02T13:00:00.123456789Z`) and without (`2026-06-10T10:00:00Z`). [Instant.parse] (which
 * delegates to [DateTimeFormatter.ISO_INSTANT]) handles both shapes natively, with up to 9
 * fractional digits.
 */
internal object CosmosStakingDateParser {
    fun parse(value: String): Instant? = runCatching { Instant.parse(value) }.getOrNull()
}
