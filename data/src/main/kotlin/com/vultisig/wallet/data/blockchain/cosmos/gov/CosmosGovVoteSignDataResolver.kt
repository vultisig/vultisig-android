package com.vultisig.wallet.data.blockchain.cosmos.gov

import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosStakingHelper
import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosStakingSignDataResolver
import com.vultisig.wallet.data.common.base64NoWrap
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.proto.v1.SignDirectProto

/**
 * Builds the `signDirect` SignDoc artefacts for an x/gov vote: a TxBody holding exactly one
 * `/cosmos.gov.v1beta1.MsgVote` with an empty memo, and a SIGN_MODE_DIRECT AuthInfo for the voter's
 * secp256k1 key.
 *
 * Every co-signer — `CosmosHelper` here, iOS `CosmosSignDataBuilder`, the SDK — signs these bytes
 * verbatim, so the vote needs no vote-specific signing code anywhere. Byte-equal with the
 * extension's `applyGovVoteSignData` (vultisig-windows#5159).
 */
object CosmosGovVoteSignDataResolver {

    /**
     * @param voterAddress the vault's address on the chain; it is both the `MsgVote.voter` and the
     *   signer.
     * @param hexPublicKey the voter's compressed secp256k1 public key.
     * @param chainId the Cosmos chain id the SignDoc is bound to.
     * @param chainSpecific the freshly fetched account number + sequence; its `gas` is the fee
     *   amount and its relayed `gasLimit`, when set, the gas limit.
     * @param feeDenom the denom the fee is paid in.
     * @param staticGasLimit the chain's gas limit when no limit was relayed — the same fallback
     *   `CosmosHelper` resolves, so the fee signed is the fee shown.
     */
    fun resolve(
        vote: CosmosGovVote,
        voterAddress: String,
        hexPublicKey: String,
        chainId: String,
        chainSpecific: BlockChainSpecific.Cosmos,
        feeDenom: String,
        staticGasLimit: Long,
    ): SignDirectProto {
        require(voterAddress.isNotBlank()) { "Voter address is required" }
        require(vote.proposalId > 0uL) { "Proposal id must be positive" }
        val pubKey =
            requireNotNull(CosmosStakingSignDataResolver.decodePubKey(hexPublicKey)) {
                "Public key must be a compressed secp256k1 pubkey (33 bytes, 0x02/0x03 prefix)"
            }

        val gasLimit =
            chainSpecific.gasLimit
                ?.takeIf { it.signum() > 0 }
                ?.let {
                    require(it.bitLength() < Long.SIZE_BITS) {
                        "Relayed gas limit $it exceeds the supported range"
                    }
                    it.toLong()
                } ?: staticGasLimit
        require(chainSpecific.gas.signum() > 0 && chainSpecific.gas.bitLength() < Long.SIZE_BITS) {
            "Fee amount ${chainSpecific.gas} is out of range"
        }

        val bodyBytes =
            CosmosStakingHelper.buildTxBodyMulti(
                listOf(
                    CosmosStakingHelper.encodeVote(
                        proposalId = vote.proposalId.toLong(),
                        voter = voterAddress,
                        option = vote.option.protoValue,
                    )
                ),
                memo = "",
            )
        val authInfoBytes =
            CosmosStakingHelper.buildAuthInfo(
                pubKey = pubKey,
                sequence = chainSpecific.sequence.toLong(),
                gasLimit = gasLimit,
                feeDenom = feeDenom,
                feeAmount = chainSpecific.gas.toLong(),
            )

        return SignDirectProto(
            bodyBytes = bodyBytes.base64NoWrap(),
            authInfoBytes = authInfoBytes.base64NoWrap(),
            chainId = chainId,
            accountNumber = chainSpecific.accountNumber.toString(),
        )
    }
}
