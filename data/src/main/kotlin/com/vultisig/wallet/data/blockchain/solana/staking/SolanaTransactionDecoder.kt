package com.vultisig.wallet.data.blockchain.solana.staking

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.transaction_decoding.DecodedAmount
import com.vultisig.wallet.data.models.transaction_decoding.DecodedAsset
import com.vultisig.wallet.data.models.transaction_decoding.DecodedCounterparty
import com.vultisig.wallet.data.models.transaction_decoding.DecodedEvidence
import com.vultisig.wallet.data.models.transaction_decoding.DecodedOperation
import com.vultisig.wallet.data.models.transaction_decoding.DecodedTransaction
import com.vultisig.wallet.data.models.transaction_decoding.OpaqueSignedContent
import com.vultisig.wallet.data.models.transaction_decoding.SignedTransactionContent
import com.vultisig.wallet.data.models.transaction_decoding.TransactionContentDecoder
import javax.inject.Inject

/**
 * Reads Solana staking operations from the initiator's pre-image or, on a joining device, from the
 * opaque transaction bytes it was relayed. The payload's flat sidecars are never consulted for a
 * relayed transaction: they are peer-supplied, and the bytes are what gets signed.
 *
 * Mirrors the iOS `SolanaTransactionDecoder`.
 */
class SolanaTransactionDecoder @Inject constructor() : TransactionContentDecoder {

    override val handles: Set<Chain> = setOf(Chain.Solana)

    override fun decode(tx: SignedTransactionContent): DecodedTransaction? {
        // An initiator holds the intent it will build from; a co-signer holds its encoded bytes.
        tx.stakingIntent?.let {
            return read(it)
        }

        val solana = tx.signedData as? OpaqueSignedContent.SolanaSignature ?: return null

        // Several relayed transactions have no single operation between them, so naming one would
        // describe only part of what is being signed.
        val raw = solana.rawTransactions.singleOrNull() ?: return null
        val reading =
            SolanaStakingTransactionReader.read(raw, signerAddress = tx.signerAddress)
                ?: return null

        return DecodedTransaction(
            operation = reading.operation,
            amount = reading.amount,
            counterparty = reading.counterparty,
            evidence = DecodedEvidence.SignedData,
        )
    }

    /** The operation an initiator's staking intent commits to. */
    private fun read(intent: SolanaStakingPayload): DecodedTransaction =
        when (intent.opType) {
            SolanaStakingOpType.Delegate ->
                DecodedTransaction(
                    operation = DecodedOperation.Delegate,
                    amount =
                        when {
                            // A "Finish Move" re-delegates an account that already holds its
                            // lamports on-chain. `SolanaHelper` leaves the built value at 0
                            // precisely so nothing reads it as an amount to move, so neither does
                            // this: the payload's lamports fund nothing here, and presenting them
                            // as funding would put a rent-reserve subtraction on a transaction
                            // that pays no rent.
                            !intent.stakeAccount.isNullOrEmpty() -> DecodedAmount.Unstated

                            // A fresh delegation funds a new account: the rent reserve stays
                            // behind, so this is funding rather than stake.
                            else ->
                                intent.lamports?.let {
                                    DecodedAmount.AccountFunding(it, DecodedAsset.ChainNative)
                                } ?: DecodedAmount.Unstated
                        },
                    counterparty = intent.votePubkey?.let(DecodedCounterparty::Validator),
                    evidence = DecodedEvidence.StructuredPayload,
                )

            SolanaStakingOpType.Unstake ->
                DecodedTransaction(
                    operation = DecodedOperation.Unstake,
                    // Deactivation cools the account; the funds move only on the later withdrawal.
                    amount = DecodedAmount.Unstated,
                    counterparty = intent.stakeAccount?.let(DecodedCounterparty::StakeAccount),
                    evidence = DecodedEvidence.StructuredPayload,
                )

            SolanaStakingOpType.Withdraw ->
                DecodedTransaction(
                    operation = DecodedOperation.WithdrawStake,
                    // The withdrawn lamports are the committed quantity.
                    amount =
                        intent.lamports?.let { DecodedAmount.Units(it, DecodedAsset.ChainNative) }
                            ?: DecodedAmount.Unstated,
                    counterparty = intent.stakeAccount?.let(DecodedCounterparty::StakeAccount),
                    evidence = DecodedEvidence.StructuredPayload,
                )
        }
}
