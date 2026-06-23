package com.vultisig.wallet.data.api.models.cosmos

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CosmosGovProposalsResponse(
    @SerialName("proposals") val proposals: List<CosmosGovProposal> = emptyList()
)

@Serializable
data class CosmosGovProposal(
    @SerialName("id") val id: String,
    @SerialName("title") val title: String? = null,
    @SerialName("summary") val summary: String? = null,
    @SerialName("final_tally_result") val finalTallyResult: CosmosGovTallyResult? = null,
    @SerialName("voting_end_time") val votingEndTime: String? = null,
)

@Serializable
data class CosmosGovTallyResult(
    @SerialName("yes_count") val yesCount: String? = null,
    @SerialName("abstain_count") val abstainCount: String? = null,
    @SerialName("no_count") val noCount: String? = null,
    @SerialName("no_with_veto_count") val noWithVetoCount: String? = null,
)

@Serializable data class CosmosGovVoteResponse(@SerialName("vote") val vote: CosmosGovVote? = null)

@Serializable
data class CosmosGovVote(
    @SerialName("options") val options: List<CosmosGovVoteOption> = emptyList()
)

@Serializable data class CosmosGovVoteOption(@SerialName("option") val option: String? = null)
