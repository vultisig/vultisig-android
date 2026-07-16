package com.vultisig.wallet.data.api.models

import java.math.BigInteger
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class SolanaBalanceJson(
    @SerialName("error") val error: String?,
    @SerialName("result") val result: SolanaBalanceResultJson?,
)

@Serializable
data class SolanaMinimumBalanceForRentExemptionJson(
    @SerialName("result") @Contextual val result: BigInteger
)

@Serializable
data class SolanaBalanceResultJson(@SerialName("value") @Contextual val value: BigInteger)

@Serializable
data class RecentBlockHashResponseJson(
    @SerialName("error") val error: String? = null,
    @SerialName("result") val result: RecentBlockHashResultJson?,
)

@Serializable
data class RecentBlockHashResultJson(@SerialName("value") val value: RecentBlockHashValueJson)

@Serializable data class RecentBlockHashValueJson(@SerialName("blockhash") val blockHash: String)

@Serializable
data class SolanaFeeObjectRespJson(
    @SerialName("error") val error: String?,
    @SerialName("result") val result: List<SolanaFeeObjectJson>?,
)

@Serializable
data class BroadcastTransactionRespJson(
    @SerialName("error") @Serializable val error: JsonObject?,
    @SerialName("result") val result: String?,
)

@Serializable
data class SolanaFeeObjectJson(
    @SerialName("prioritizationFee") @Contextual val prioritizationFee: BigInteger,
    @SerialName("slot") @Contextual val slot: BigInteger,
)

@Serializable
data class SolanaFeeForMessageResponse(
    @SerialName("error") val error: JsonObject? = null,
    @SerialName("result") val result: SolanaFeeForMessageResult? = null,
)

@Serializable
data class SolanaFeeForMessageResult(
    @SerialName("value") @Contextual val value: BigInteger? = null
)

@Serializable
data class SolanaSignatureStatusesResult(val value: List<SolanaSignatureStatus?> = emptyList())

@Serializable
data class SolanaRpcResponseJson<T>(
    @SerialName("id") val id: Int,
    @SerialName("result") val result: T,
    @SerialName("error") val error: RpcError?,
)

@Serializable
data class SolanaSignatureStatus(
    val confirmationStatus: String? = null,
    // Non-null when the tx executed but reverted (e.g. swap slippage, insufficient funds). A Solana
    // TransactionError is either a string or an object, so keep it as a raw JsonElement.
    @SerialName("err") val err: JsonElement? = null,
)

@Serializable
data class SolanaAccountInfoResponseJson(
    @SerialName("result") val result: SolanaAccountInfoResultJson? = null,
    @SerialName("error") val error: JsonObject? = null,
)

@Serializable
data class SolanaAccountInfoResultJson(
    @SerialName("value") val value: SolanaAccountInfoValueJson? = null
)

@Serializable data class SolanaAccountInfoValueJson(@SerialName("owner") val owner: String? = null)

// Solana native-staking read-layer wire DTOs (getEpochInfo / getVoteAccounts / getProgramAccounts).

@Serializable
data class SolanaEpochInfoResponseJson(
    @SerialName("error") val error: JsonObject? = null,
    @SerialName("result") val result: SolanaEpochInfoResultJson? = null,
)

@Serializable
data class SolanaEpochInfoResultJson(
    @SerialName("epoch") val epoch: Long,
    @SerialName("slotIndex") val slotIndex: Long,
    @SerialName("slotsInEpoch") val slotsInEpoch: Long,
    @SerialName("absoluteSlot") val absoluteSlot: Long,
)

@Serializable
data class SolanaVoteAccountsResponseJson(
    @SerialName("error") val error: JsonObject? = null,
    @SerialName("result") val result: SolanaVoteAccountsResultJson? = null,
)

@Serializable
data class SolanaVoteAccountsResultJson(
    @SerialName("current") val current: List<SolanaVoteAccountJson> = emptyList(),
    @SerialName("delinquent") val delinquent: List<SolanaVoteAccountJson> = emptyList(),
)

@Serializable
data class SolanaVoteAccountJson(
    @SerialName("votePubkey") val votePubkey: String,
    @SerialName("nodePubkey") val nodePubkey: String,
    @SerialName("commission") val commission: Int,
    @SerialName("activatedStake") @Contextual val activatedStake: BigInteger,
)

@Serializable
data class SolanaProgramAccountsResponseJson(
    @SerialName("error") val error: JsonObject? = null,
    @SerialName("result") val result: List<SolanaProgramAccountJson> = emptyList(),
)

@Serializable
data class SolanaProgramAccountJson(
    @SerialName("pubkey") val pubkey: String,
    @SerialName("account") val account: SolanaStakeAccountDataJson,
)

// getAccountInfo (jsonParsed) for a single stake account. Its `value` is the same shape as the
// `account` field of a getProgramAccounts row, so it reuses SolanaStakeAccountDataJson.
@Serializable
data class SolanaStakeAccountInfoResponseJson(
    @SerialName("error") val error: JsonObject? = null,
    @SerialName("result") val result: SolanaStakeAccountInfoResultJson? = null,
)

@Serializable
data class SolanaStakeAccountInfoResultJson(
    @SerialName("value") val value: SolanaStakeAccountDataJson? = null
)

@Serializable
data class SolanaStakeAccountDataJson(
    @SerialName("lamports") @Contextual val lamports: BigInteger,
    @SerialName("data") val data: SolanaStakeParsedDataJson? = null,
)

@Serializable
data class SolanaStakeParsedDataJson(
    @SerialName("parsed") val parsed: SolanaParsedStakeJson? = null
)

@Serializable
data class SolanaParsedStakeJson(
    @SerialName("type") val type: String? = null,
    @SerialName("info") val info: SolanaStakeInfoJson? = null,
)

@Serializable
data class SolanaStakeInfoJson(
    @SerialName("meta") val meta: SolanaStakeMetaJson? = null,
    @SerialName("stake") val stake: SolanaStakeJson? = null,
)

@Serializable
data class SolanaStakeMetaJson(
    @SerialName("rentExemptReserve") val rentExemptReserve: String? = null,
    @SerialName("authorized") val authorized: SolanaStakeAuthorizedJson? = null,
)

@Serializable
data class SolanaStakeAuthorizedJson(
    @SerialName("staker") val staker: String? = null,
    @SerialName("withdrawer") val withdrawer: String? = null,
)

@Serializable
data class SolanaStakeJson(
    @SerialName("delegation") val delegation: SolanaStakeDelegationJson? = null
)

@Serializable
data class SolanaStakeDelegationJson(
    @SerialName("voter") val voter: String? = null,
    @SerialName("stake") val stake: String? = null,
    @SerialName("activationEpoch") val activationEpoch: String? = null,
    @SerialName("deactivationEpoch") val deactivationEpoch: String? = null,
)
