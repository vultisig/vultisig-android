package com.vultisig.wallet.data.api.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MayaLatestBlockInfoResponse(
    @SerialName("block_id")
    val blockId: BlockIdJson,
    @SerialName("block")
    val block: BlockJson,
)

@Serializable
data class BlockIdJson(
    @SerialName("hash")
    val hash: String,
    @SerialName("parts")
    val parts: PartsJson,
)

@Serializable
data class PartsJson(
    @SerialName("total")
    val total: Int,
    @SerialName("hash")
    val hash: String,
)

@Serializable
data class BlockJson(
    @SerialName("header")
    val header: HeaderJson,
    @SerialName("data")
    val data: DataJson,
)

@Serializable
data class HeaderJson(
    @SerialName("version")
    val version: VersionJson,
    @SerialName("chain_id")
    val chainId: String,
    @SerialName("height")
    val height: String,
    @SerialName("time")
    val time: String,
    @SerialName("last_block_id")
    val lastBlockId: BlockIdJson,
    @SerialName("last_commit_hash")
    val lastCommitHash: String,
    @SerialName("data_hash")
    val dataHash: String,
    @SerialName("validators_hash")
    val validatorsHash: String,
    @SerialName("next_validators_hash")
    val nextValidatorsHash: String,
    @SerialName("consensus_hash")
    val consensusHash: String,
    @SerialName("app_hash")
    val appHash: String,
    @SerialName("last_results_hash")
    val lastResultsHash: String,
    @SerialName("evidence_hash")
    val evidenceHash: String,
    @SerialName("proposer_address")
    val proposerAddress: String,
)

@Serializable
data class VersionJson(
    @SerialName("block")
    val block: String,
)

@Serializable
data class DataJson(
    @SerialName("txs")
    val txs: List<String>?,
)
