package com.vultisig.wallet.data.blockchain.cosmos.gov

/**
 * The x/gov ballots a vote can carry, in canonical Cosmos order. [protoValue] is the `VoteOption`
 * enum value written into `MsgVote.option`; `VOTE_OPTION_UNSPECIFIED` (0) is not a ballot the chain
 * accepts, so it has no entry.
 */
enum class CosmosVoteOption(val protoValue: Long) {
    YES(1),
    ABSTAIN(2),
    NO(3),
    NO_WITH_VETO(4),
}

/**
 * A governance vote on [proposalId]. The id is a Cosmos `uint64`, so it is held as [ULong] — a
 * signed type would misread every id above [Long.MAX_VALUE].
 */
data class CosmosGovVote(val proposalId: ULong, val option: CosmosVoteOption) {

    companion object {
        private val DIGITS = Regex("^[0-9]+$")

        /**
         * Parses a proposal id typed by the user: ASCII digits only, positive, within `uint64`.
         * Answers null for anything else — a blank field, zero, a sign, grouping, or an overflow —
         * so a malformed entry can never become a vote on some other proposal.
         */
        fun parseProposalId(text: String): ULong? =
            text.takeIf { it.matches(DIGITS) }?.toULongOrNull()?.takeIf { it > 0uL }
    }
}
