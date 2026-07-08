package com.vultisig.wallet.data.blockchain.solana.staking

import com.vultisig.wallet.data.chains.helpers.SolanaHelper
import java.math.BigInteger
import vultisig.keysign.v1.SignSolana

/**
 * Produces the [SignSolana] artefact (relayed raw transaction bytes) for a Solana native-staking
 * operation. Analog of `CosmosStakingSignDataResolver`; mirrors the iOS
 * `SolanaStakingSignDataResolver` (vultisig-ios #4661/#4662).
 *
 * Unlike the transfer path — where every co-signing device rebuilds the signing input from fields
 * that all round-trip through proto — a staking op's validator pubkey + amount live on the
 * LOCAL-ONLY [SolanaStakingPayload], which the peer never receives. So the resolver builds the
 * unsigned transaction ONCE here (pinning the recent blockhash and, for delegate, the
 * wallet-core-derived stake-account address) and relays the raw bytes via
 * [SignSolana.rawTransactions]. Every device then signs the byte-identical message through the
 * raw-transaction path — the MPC byte-parity guarantee.
 */
class SolanaStakingSignDataResolver {

    /**
     * Builds the [SignSolana] for a [SolanaStakingOpType.Delegate] payload.
     *
     * @param helper a [SolanaHelper] constructed with the vault's hex public key
     * @param senderAddress base58 SOL address of the signer (stake authority / funder)
     * @param coinHexPublicKey the signer's ed25519 public key (hex)
     * @param recentBlockHash the pinned recent blockhash
     * @param priorityFeePrice micro-lamports-per-CU price
     * @param priorityFeeLimit compute-unit limit
     * @param balanceLamports the signer's spendable lamports, for the funding guard
     */
    fun resolveDelegate(
        helper: SolanaHelper,
        payload: SolanaStakingPayload,
        senderAddress: String,
        coinHexPublicKey: String,
        recentBlockHash: String,
        priorityFeePrice: BigInteger,
        priorityFeeLimit: BigInteger,
        balanceLamports: BigInteger,
    ): SignSolana {
        require(payload.opType == SolanaStakingOpType.Delegate) {
            "SolanaStakingSignDataResolver.resolveDelegate: wrong op type ${payload.opType}"
        }
        val votePubkey =
            payload.votePubkey?.takeIf { it.isNotEmpty() }
                ?: error("solana delegate: missing validator vote pubkey")
        val lamports =
            payload.lamports?.takeIf { it.signum() > 0 }
                ?: error("solana delegate: missing or zero delegation amount")
        // `lamports` is the stake-account FUNDING (delegated amount + rent-exempt reserve, combined
        // upstream). The signer pays that funding plus a negligible tx fee, so reject up front when
        // it exceeds the balance — otherwise the ceremony signs a chain-rejected tx.
        check(lamports <= balanceLamports) {
            "solana delegate: funding $lamports exceeds balance $balanceLamports"
        }
        require(votePubkey.isNotEmpty())

        return build(
            helper,
            payload,
            senderAddress,
            coinHexPublicKey,
            recentBlockHash,
            priorityFeePrice,
            priorityFeeLimit,
        )
    }

    /**
     * Builds the [SignSolana] for a [SolanaStakingOpType.Unstake] (deactivate) payload. No
     * validator preflight or funding guard — deactivate operates on an existing stake account and
     * carries no amount.
     */
    fun resolveDeactivate(
        helper: SolanaHelper,
        payload: SolanaStakingPayload,
        senderAddress: String,
        coinHexPublicKey: String,
        recentBlockHash: String,
        priorityFeePrice: BigInteger,
        priorityFeeLimit: BigInteger,
    ): SignSolana {
        require(payload.opType == SolanaStakingOpType.Unstake) {
            "SolanaStakingSignDataResolver.resolveDeactivate: wrong op type ${payload.opType}"
        }
        require(!payload.stakeAccount.isNullOrEmpty()) {
            "solana deactivate: missing stake account"
        }
        return build(
            helper,
            payload,
            senderAddress,
            coinHexPublicKey,
            recentBlockHash,
            priorityFeePrice,
            priorityFeeLimit,
        )
    }

    /**
     * Builds the [SignSolana] for a [SolanaStakingOpType.Withdraw] payload. The withdraw CTA is
     * gated upstream by the epoch-cooldown check (full inactivity), so no cooldown check is
     * repeated here — only field validation and the byte-parity build.
     */
    fun resolveWithdraw(
        helper: SolanaHelper,
        payload: SolanaStakingPayload,
        senderAddress: String,
        coinHexPublicKey: String,
        recentBlockHash: String,
        priorityFeePrice: BigInteger,
        priorityFeeLimit: BigInteger,
    ): SignSolana {
        require(payload.opType == SolanaStakingOpType.Withdraw) {
            "SolanaStakingSignDataResolver.resolveWithdraw: wrong op type ${payload.opType}"
        }
        require(!payload.stakeAccount.isNullOrEmpty()) { "solana withdraw: missing stake account" }
        require(payload.lamports?.let { it.signum() > 0 } == true) {
            "solana withdraw: missing or zero withdrawal amount"
        }
        return build(
            helper,
            payload,
            senderAddress,
            coinHexPublicKey,
            recentBlockHash,
            priorityFeePrice,
            priorityFeeLimit,
        )
    }

    private fun build(
        helper: SolanaHelper,
        payload: SolanaStakingPayload,
        senderAddress: String,
        coinHexPublicKey: String,
        recentBlockHash: String,
        priorityFeePrice: BigInteger,
        priorityFeeLimit: BigInteger,
    ): SignSolana {
        val rawTransaction =
            helper.buildStakingUnsignedTransaction(
                payload = payload,
                senderAddress = senderAddress,
                coinHexPublicKey = coinHexPublicKey,
                recentBlockHash = recentBlockHash,
                priorityFeePrice = priorityFeePrice,
                priorityFeeLimit = priorityFeeLimit,
            )
        return SignSolana(rawTransactions = listOf(rawTransaction))
    }
}
