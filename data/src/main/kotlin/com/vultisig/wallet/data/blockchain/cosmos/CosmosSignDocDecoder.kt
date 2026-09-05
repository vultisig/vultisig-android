package com.vultisig.wallet.data.blockchain.cosmos

import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosStakingConfig
import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosStakingPayload
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.TokenStandard
import com.vultisig.wallet.data.models.transaction_decoding.DecodedAmount
import com.vultisig.wallet.data.models.transaction_decoding.DecodedAsset
import com.vultisig.wallet.data.models.transaction_decoding.DecodedCounterparty
import com.vultisig.wallet.data.models.transaction_decoding.DecodedEvidence
import com.vultisig.wallet.data.models.transaction_decoding.DecodedOperation
import com.vultisig.wallet.data.models.transaction_decoding.DecodedTransaction
import com.vultisig.wallet.data.models.transaction_decoding.OpaqueSignedContent
import com.vultisig.wallet.data.models.transaction_decoding.SignedTransactionContent
import com.vultisig.wallet.data.models.transaction_decoding.TransactionContentDecoder
import java.math.BigInteger
import javax.inject.Inject

/**
 * Reads the initiator's Cosmos staking pre-image, or the active SignDoc body a co-signer was sent.
 * Adjacent flat sidecars are never consulted for a SignDoc-driven transaction: they are
 * peer-supplied, and the body is what gets signed.
 *
 * Mirrors the iOS `CosmosSignDocDecoder`.
 */
class CosmosSignDocDecoder @Inject constructor() : TransactionContentDecoder {

    /** Only chains whose signing paths consume Cosmos SignDocs. */
    override val handles: Set<Chain> =
        Chain.entries.filter { it.standard == TokenStandard.COSMOS }.toSet()

    override fun decode(tx: SignedTransactionContent): DecodedTransaction? {
        // An initiator holds the intent its signer will encode; a co-signer holds the encoded body.
        tx.cosmosStakingIntent?.let {
            return decode(it, tx.chain)
        }

        if (!tx.signedDataBodyIsActive) return null
        val direct = tx.signedData as? OpaqueSignedContent.CosmosSignDirect ?: return null
        val reading = CosmosSignDocReader.read(direct.bodyBytes) ?: return null

        return DecodedTransaction(
            operation = reading.operation,
            amount = reading.amount,
            counterparty = reading.counterparty,
            evidence = DecodedEvidence.SignedData,
        )
    }

    /**
     * Maps a validated initiator pre-image into the same vocabulary the body produces, so both
     * devices in a ceremony name the operation identically.
     *
     * ⚠️ The bond denom comes from [CosmosStakingConfig], not from the payload — unlike iOS, where
     * it rides on `CosmosStakingPayload`. That is the same source
     * [com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosStakingSignDataResolver] encodes
     * the `Coin` from, so the initiator reads the denom its own signer is about to write. A chain
     * with no staking entry has no denom to name and is refused rather than guessed at.
     */
    private fun decode(intent: CosmosStakingPayload, chain: Chain): DecodedTransaction? {
        val denom = runCatching { CosmosStakingConfig.entryFor(chain).bondDenom }.getOrNull()

        fun amount(text: String): DecodedAmount? {
            if (denom.isNullOrEmpty()) return null
            val value = runCatching { BigInteger(text) }.getOrNull() ?: return null
            if (value.signum() < 0) return null
            return DecodedAmount.Units(value, DecodedAsset.Denom(denom))
        }

        return when (intent) {
            is CosmosStakingPayload.Delegate -> {
                if (intent.validatorAddress.isEmpty()) return null
                DecodedTransaction(
                    operation = DecodedOperation.Delegate,
                    amount = amount(intent.amount) ?: return null,
                    counterparty = DecodedCounterparty.Validator(intent.validatorAddress),
                    evidence = DecodedEvidence.StructuredPayload,
                )
            }

            is CosmosStakingPayload.Undelegate -> {
                if (intent.validatorAddress.isEmpty()) return null
                DecodedTransaction(
                    operation = DecodedOperation.Undelegate,
                    amount = amount(intent.amount) ?: return null,
                    counterparty = DecodedCounterparty.Validator(intent.validatorAddress),
                    evidence = DecodedEvidence.StructuredPayload,
                )
            }

            is CosmosStakingPayload.Redelegate -> {
                if (intent.validatorSrcAddress.isEmpty() || intent.validatorDstAddress.isEmpty()) {
                    return null
                }
                DecodedTransaction(
                    operation = DecodedOperation.Redelegate,
                    amount = amount(intent.amount) ?: return null,
                    // The destination is where the stake lands; the source is left behind.
                    counterparty = DecodedCounterparty.Validator(intent.validatorDstAddress),
                    evidence = DecodedEvidence.StructuredPayload,
                )
            }

            is CosmosStakingPayload.WithdrawRewards -> {
                val validators = intent.validators
                if (validators.isEmpty() || validators.size > MAX_CLAIM_VALIDATORS) return null
                if (validators.any { it.isEmpty() }) return null
                DecodedTransaction(
                    operation = DecodedOperation.ClaimRewards,
                    // The chain settles what has accrued; the intent names no quantity.
                    amount = DecodedAmount.Unstated,
                    // A batch across validators has no single counterparty to name.
                    counterparty = validators.singleOrNull()?.let(DecodedCounterparty::Validator),
                    evidence = DecodedEvidence.StructuredPayload,
                )
            }
        }
    }

    private companion object {
        /**
         * Matches the batch ceiling the SignDoc reader enforces on the co-signer side, so an intent
         * that could not have produced a readable body is refused on the initiator too.
         */
        const val MAX_CLAIM_VALIDATORS = 64
    }
}
