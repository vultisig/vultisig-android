package com.vultisig.wallet.data.blockchain.cosmos.staking

/**
 * Carries the staking-operation intent from the per-flow view-model through to the keysign-payload
 * builder. At build time [CosmosStakingSignDataResolver] consumes this payload, encodes the
 * matching `Any`-wrapped Cosmos-SDK message via [CosmosStakingHelper], builds the SignDoc bytes,
 * and writes them onto [com.vultisig.wallet.data.models.payload.KeysignPayload.signDirect] as a
 * [com.vultisig.wallet.data.models.proto.v1.SignDirectProto].
 *
 * Port of iOS `CosmosStakingPayload.swift` (vultisig-ios PR #4432).
 *
 * Discriminated by sealed-class subtype — every consumer dispatches on the concrete type. The
 * SignDoc bytes produced from this payload are what the peer device sees — those bytes are the
 * cross-platform contract.
 *
 * `amount` is in **base units** (e.g. `"1000000"` for 1 LUNA = 1_000_000 uluna). The caller is
 * responsible for the human → base-unit conversion before constructing the payload. The bond denom
 * is sourced from [CosmosStakingConfig] at encode time, so it is not carried on the payload.
 */
sealed class CosmosStakingPayload {

    data class Delegate(val validatorAddress: String, val amount: String) : CosmosStakingPayload()

    data class Undelegate(val validatorAddress: String, val amount: String) :
        CosmosStakingPayload()

    data class Redelegate(
        val validatorSrcAddress: String,
        val validatorDstAddress: String,
        val amount: String,
    ) : CosmosStakingPayload()

    /**
     * Multi-message claim. `validators` is 1..N — single-claim flows pass a one-element list,
     * batched flows pass up to [CosmosStakingSignDataResolver.MAX_BATCH_WITHDRAW_VALIDATORS]. No
     * Coin field on the wire ([CosmosStakingHelper.encodeWithdrawDelegatorReward] doesn't take an
     * amount), so no [amount] field here either.
     */
    data class WithdrawRewards(val validators: List<String>) : CosmosStakingPayload()
}
