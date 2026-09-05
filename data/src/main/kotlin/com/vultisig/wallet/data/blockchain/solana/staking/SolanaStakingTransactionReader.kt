package com.vultisig.wallet.data.blockchain.solana.staking

import com.vultisig.wallet.data.blockchain.solana.kamino.KaminoComputeBudget
import com.vultisig.wallet.data.blockchain.solana.kamino.KaminoDecodedTransaction
import com.vultisig.wallet.data.blockchain.solana.kamino.KaminoTransactionDecoder
import com.vultisig.wallet.data.blockchain.solana.kamino.KaminoTxInstruction
import com.vultisig.wallet.data.chains.helpers.SOLANA_MAX_PRIORITY_FEE_PRICE
import com.vultisig.wallet.data.chains.helpers.SOLANA_PRIORITY_FEE_LIMIT
import com.vultisig.wallet.data.chains.helpers.SOLANA_PRIORITY_FEE_PRICE
import com.vultisig.wallet.data.crypto.Base58Codec
import com.vultisig.wallet.data.models.transaction_decoding.DecodedAmount
import com.vultisig.wallet.data.models.transaction_decoding.DecodedAsset
import com.vultisig.wallet.data.models.transaction_decoding.DecodedCounterparty
import com.vultisig.wallet.data.models.transaction_decoding.DecodedOperation
import java.math.BigInteger

/** What one relayed Solana transaction proves it does. */
data class SolanaStakingReading(
    val operation: DecodedOperation,
    val amount: DecodedAmount,
    val counterparty: DecodedCounterparty?,
)

/**
 * Reads self-contained Solana staking transactions out of the bytes a co-signer receives.
 *
 * Mirrors the iOS `SolanaTransactionReader`, but does not re-implement its hand-written message
 * parser: Android already decodes Solana transactions through WalletCore's `TransactionDecoder`
 * (see [KaminoTransactionDecoder]), which handles legacy and v0 messages and resolves the static
 * account keys. What is ported is the part that matters — the refusals, and the requirement that a
 * whole instruction sequence match, so an unrelated transfer cannot ride along beside a recognised
 * staking instruction and go undescribed.
 *
 * Everything unrecognised is refused rather than guessed at. A co-signer that cannot read a
 * transaction is better served by the screen it already had than by a confident wrong verb.
 */
object SolanaStakingTransactionReader {

    /** Little-endian stake-program instruction discriminators. */
    private const val STAKE_INITIALIZE = 0
    private const val STAKE_DELEGATE = 2
    private const val STAKE_WITHDRAW = 4
    private const val STAKE_DEACTIVATE = 5

    /** Little-endian system-program instruction discriminators. */
    private const val SYSTEM_CREATE_ACCOUNT = 0
    private const val SYSTEM_CREATE_ACCOUNT_WITH_SEED = 3

    private const val SYSTEM_PROGRAM_ID = "11111111111111111111111111111111"

    private const val PUBLIC_KEY_BYTES = 32
    private const val MAX_SEED_BYTES = 32

    /**
     * `Initialize` is a 4-byte discriminator, an `Authorized { staker, withdrawer }` of two public
     * keys, and a `Lockup { unix_timestamp: i64, epoch: u64, custodian }` — 4 + 64 + 48.
     */
    private const val INITIALIZE_DATA_BYTES = 116

    private const val AUTHORIZED_STAKER_OFFSET = 4

    private const val CREATE_ACCOUNT_DATA_BYTES = 52

    /**
     * The compute-unit limit every staking transaction this app builds reserves.
     * `BlockChainSpecificRepository` pins it for all of Solana, and the staking payload builder
     * passes that through to `SolanaHelper`, so a relayed transaction reserving anything else was
     * not built by this app.
     */
    private val EXPECTED_UNIT_LIMIT: BigInteger =
        BigInteger.valueOf(SOLANA_PRIORITY_FEE_LIMIT.toLong())

    /** The clamp `SolanaApi.getMedianPriorityFee` and `SolanaHelper` apply to the sampled price. */
    private val MINIMUM_UNIT_PRICE: BigInteger = BigInteger.valueOf(SOLANA_PRIORITY_FEE_PRICE)

    private val MAXIMUM_UNIT_PRICE: BigInteger = BigInteger.valueOf(SOLANA_MAX_PRIORITY_FEE_PRICE)

    /**
     * Reads one relayed transaction, or refuses it.
     *
     * @param signerAddress the vault's own Solana address. Everything below is read as a claim
     *   about *this* wallet, so without it a transaction that merely names the wallet in an
     *   authority slot would be described as the wallet's own.
     */
    fun read(base64Transaction: String, signerAddress: String): SolanaStakingReading? {
        if (signerAddress.isEmpty()) return null

        val decoded =
            runCatching { KaminoTransactionDecoder.decode(base64Transaction) }.getOrNull()
                ?: return null

        if (!decoded.isSignableBy(signerAddress)) return null
        if (!decoded.carriesAnAcceptableComputeBudget()) return null

        // Compute-budget entries are checked above rather than matched as part of the sequence:
        // they move no value, and their position is WalletCore's to choose. Order is otherwise
        // preserved — the shapes below are positional.
        val instructions =
            decoded.instructions.filterNot { it.programId == KaminoComputeBudget.PROGRAM_ID }

        // An account the decoder could not name came from an address lookup table, which needs
        // network state this read deliberately does not have. Refused rather than compared, since
        // two unresolved accounts would otherwise look like the same account.
        if (instructions.any { it.namesAnUnresolvedAccount }) return null

        return when (instructions.size) {
            1 -> readStandalone(instructions[0], signerAddress)
            3 -> readDelegation(instructions, signerAddress)
            else -> null
        }
    }

    /**
     * Whether the envelope is one this wallet alone completes.
     *
     * The raw-signing path splices this vault's signature into slot 0 and leaves the rest of the
     * envelope exactly as it arrived. So the fee payer — message account key 0, not any account an
     * instruction happens to name first — must be the wallet, one signature must be all the message
     * declares, and every slot must still be an empty placeholder. Otherwise the wallet pays for
     * somebody else's transaction, signs one that is broadcast incomplete, or countersigns beside a
     * signature it never saw. These are the same three checks the Kamino raw-Solana path makes for
     * the same reason.
     */
    private fun KaminoDecodedTransaction.isSignableBy(signerAddress: String): Boolean =
        feePayer == signerAddress && requiredSignatures == 1 && isUnsigned

    /**
     * Whether the compute budget is one this app would have set.
     *
     * The budget is not inert: unit price times unit limit is the priority fee the runtime charges,
     * and the screen this reading feeds describes only the staking action. So each ComputeBudget
     * instruction has to parse, neither kind may repeat, and the values have to be the ones this
     * app clamps to. A transaction carrying no budget at all is accepted — it pays the base fee
     * only, which is the one direction that cannot overcharge — and the two instructions' order is
     * not checked, since it is WalletCore's to pick on both platforms and changes nothing about
     * what is charged.
     */
    private fun KaminoDecodedTransaction.carriesAnAcceptableComputeBudget(): Boolean {
        val budget = instructions.filter { it.programId == KaminoComputeBudget.PROGRAM_ID }
        if (budget.isEmpty()) return true

        val limits = budget.mapNotNull { KaminoComputeBudget.unitLimitArgument(it.data) }
        val prices = budget.mapNotNull { KaminoComputeBudget.unitPriceArgument(it.data) }

        // Anything unparseable, duplicated, or from neither entry point leaves a budget instruction
        // unaccounted for, and an unaccounted-for instruction is one nothing here is checking.
        if (limits.size + prices.size != budget.size) return false
        if (limits.size > 1 || prices.size > 1) return false

        limits.singleOrNull()?.let { if (it != EXPECTED_UNIT_LIMIT) return false }
        prices.singleOrNull()?.let {
            if (it < MINIMUM_UNIT_PRICE || it > MAXIMUM_UNIT_PRICE) return false
        }
        return true
    }

    /**
     * Deactivate and withdraw each stand alone. A delegate never does — it only reaches this app as
     * the third instruction of a create/initialize/delegate sequence — so it is refused here.
     */
    private fun readStandalone(
        instruction: KaminoTxInstruction,
        signerAddress: String,
    ): SolanaStakingReading? {
        if (instruction.programId != SolanaStakingConfig.STAKE_PROGRAM_ID) return null

        return when (instruction.data.discriminator()) {
            STAKE_DEACTIVATE -> {
                // Deactivation cools the whole account and names no quantity.
                if (instruction.data.size != 4 || instruction.accounts.size != 3) return null

                // Account 2 is the stake authority. A deactivation authorised by anyone else is
                // not this wallet's to describe, whatever the envelope says about who pays.
                if (instruction.accounts[2] != signerAddress) return null
                SolanaStakingReading(
                    operation = DecodedOperation.Unstake,
                    amount = DecodedAmount.Unstated,
                    counterparty = DecodedCounterparty.StakeAccount(instruction.accounts[0]),
                )
            }

            STAKE_WITHDRAW -> {
                // Withdraw data carries the lamports leaving the stake account.
                if (instruction.data.size != 12 || instruction.accounts.size != 5) return null

                // Account 1 is where the lamports go and account 4 is the authority permitting it.
                // A transaction can name this wallet as the authority and somebody else as the
                // recipient, which would read as "you're withdrawing" over funds leaving for an
                // address the screen never shows. WalletCore builds this app's withdrawals with
                // the signer as both, so requiring that of both refuses the mismatch and accepts
                // every transaction this app produces.
                if (instruction.accounts[1] != signerAddress) return null
                if (instruction.accounts[4] != signerAddress) return null

                val lamports = instruction.data.littleEndianU64(offset = 4) ?: return null
                SolanaStakingReading(
                    operation = DecodedOperation.WithdrawStake,
                    // Stake accounts hold chain-native SOL.
                    amount = DecodedAmount.Units(lamports, DecodedAsset.ChainNative),
                    counterparty = DecodedCounterparty.StakeAccount(instruction.accounts[0]),
                )
            }

            else -> null
        }
    }

    /**
     * The delegation shape this app builds: create the stake account, initialize it, delegate it.
     *
     * Every account identity is checked across the three instructions. Without that, a transaction
     * could fund one account and delegate a different one, and the funding figure shown would
     * describe neither.
     */
    private fun readDelegation(
        instructions: List<KaminoTxInstruction>,
        signerAddress: String,
    ): SolanaStakingReading? {
        val (create, initialize, delegate) =
            Triple(instructions[0], instructions[1], instructions[2])

        val funding = create.stakeAccountFunding() ?: return null
        if (!initialize.isStakeInitialize() || !delegate.isStakeDelegate()) return null

        // The created, initialized and delegated stake account must be one account.
        val createdStake = create.accounts.getOrNull(1) ?: return null
        if (createdStake != initialize.accounts.getOrNull(0)) return null
        if (createdStake != delegate.accounts.getOrNull(0)) return null

        // The payer must be this wallet, the account the delegation is authorised by, and the
        // account the initialize instruction names as staker. Otherwise the funds are the signer's
        // while the stake is somebody else's.
        val payer = create.accounts.getOrNull(0) ?: return null
        if (payer != signerAddress) return null
        if (payer != delegate.accounts.getOrNull(5)) return null
        if (payer != initialize.authorizedStaker()) return null

        // The withdrawer matters more than the staker: it is the authority that can take the
        // lamports back out. Left unchecked, a transaction could name this wallet as staker while
        // handing withdrawal to someone else — the wallet funds the account and cannot empty it.
        if (payer != initialize.authorizedWithdrawer()) return null

        val vote = delegate.accounts.getOrNull(1) ?: return null

        return SolanaStakingReading(
            operation = DecodedOperation.Delegate,
            // Funding, not stake: the rent reserve stays in the account.
            amount = DecodedAmount.AccountFunding(funding, DecodedAsset.ChainNative),
            counterparty = DecodedCounterparty.Validator(vote),
        )
    }

    /**
     * The lamports a system-program instruction funds a new stake account with, or null when it is
     * not creating one. Both shapes WalletCore emits are accepted, and both are checked to be
     * creating a stake-program-owned account of the stake-account size — otherwise this would read
     * the funding of an ordinary account creation as a delegation.
     */
    private fun KaminoTxInstruction.stakeAccountFunding(): BigInteger? {
        if (programId != SYSTEM_PROGRAM_ID) return null

        val payer = accounts.getOrNull(0) ?: return null
        val createdStake = accounts.getOrNull(1) ?: return null
        if (payer == createdStake) return null

        return when (data.discriminator()) {
            SYSTEM_CREATE_ACCOUNT -> {
                if (accounts.size != 2 || data.size != CREATE_ACCOUNT_DATA_BYTES) return null
                val lamports = data.littleEndianU64(offset = 4) ?: return null
                if (lamports <= BigInteger.ZERO) return null
                if (data.littleEndianU64(offset = 12) != STAKE_ACCOUNT_SPACE) return null
                if (!data.holdsStakeProgramId(offset = 20)) return null
                lamports
            }

            SYSTEM_CREATE_ACCOUNT_WITH_SEED -> {
                if (accounts.size != 3) return null
                val seedLength = data.littleEndianU64(offset = 36) ?: return null
                if (seedLength > BigInteger.valueOf(MAX_SEED_BYTES.toLong())) return null

                val lamportsOffset = 44 + seedLength.toInt()
                val spaceOffset = lamportsOffset + 8
                val ownerOffset = spaceOffset + 8
                if (data.size != ownerOffset + PUBLIC_KEY_BYTES) return null

                val lamports = data.littleEndianU64(lamportsOffset) ?: return null
                if (lamports <= BigInteger.ZERO) return null
                if (data.littleEndianU64(spaceOffset) != STAKE_ACCOUNT_SPACE) return null
                if (!data.holdsStakeProgramId(ownerOffset)) return null

                // The seed's base must be the payer, and must be the base the data names.
                val base = accounts.getOrNull(2) ?: return null
                if (base != payer) return null
                if (data.base58(offset = 4) != base) return null

                lamports
            }

            else -> null
        }
    }

    private fun KaminoTxInstruction.isStakeInitialize(): Boolean =
        programId == SolanaStakingConfig.STAKE_PROGRAM_ID &&
            accounts.size == 2 &&
            data.size == INITIALIZE_DATA_BYTES &&
            data.discriminator() == STAKE_INITIALIZE

    private fun KaminoTxInstruction.isStakeDelegate(): Boolean =
        programId == SolanaStakingConfig.STAKE_PROGRAM_ID &&
            accounts.size == 6 &&
            data.size == 4 &&
            data.discriminator() == STAKE_DELEGATE

    /**
     * `Initialize` carries `Authorized { staker, withdrawer }` straight after the discriminator, so
     * the staker is the first key and the withdrawer the second.
     */
    private fun KaminoTxInstruction.authorizedStaker(): String? =
        data.takeIf { it.size == INITIALIZE_DATA_BYTES }?.base58(offset = AUTHORIZED_STAKER_OFFSET)

    private fun KaminoTxInstruction.authorizedWithdrawer(): String? =
        data
            .takeIf { it.size == INITIALIZE_DATA_BYTES }
            ?.base58(offset = AUTHORIZED_STAKER_OFFSET + PUBLIC_KEY_BYTES)

    /** True when any account this instruction names came from an unresolved lookup table. */
    private val KaminoTxInstruction.namesAnUnresolvedAccount: Boolean
        get() =
            programId == KaminoTransactionDecoder.UNKNOWN_ACCOUNT ||
                accounts.any { it == KaminoTransactionDecoder.UNKNOWN_ACCOUNT }

    /** The four-byte little-endian instruction discriminator, or null when the data is shorter. */
    private fun ByteArray.discriminator(): Int? {
        if (size < 4) return null
        var value = 0
        for (offset in 0 until 4) {
            value = value or ((this[offset].toInt() and 0xFF) shl (8 * offset))
        }
        return value
    }

    /** A little-endian `u64` read as an unsigned value, or null when it does not fit. */
    private fun ByteArray.littleEndianU64(offset: Int): BigInteger? {
        if (offset < 0 || size < offset + 8) return null
        var value = BigInteger.ZERO
        for (byte in 7 downTo 0) {
            value =
                value.shiftLeft(8).or(BigInteger.valueOf((this[offset + byte].toLong() and 0xFF)))
        }
        return value
    }

    private fun ByteArray.base58(offset: Int): String? {
        if (offset < 0 || size < offset + PUBLIC_KEY_BYTES) return null
        return Base58Codec.encode(copyOfRange(offset, offset + PUBLIC_KEY_BYTES))
    }

    private fun ByteArray.holdsStakeProgramId(offset: Int): Boolean =
        base58(offset) == SolanaStakingConfig.STAKE_PROGRAM_ID

    private val STAKE_ACCOUNT_SPACE: BigInteger =
        BigInteger.valueOf(SolanaStakingConfig.STAKE_ACCOUNT_SPACE.toLong())
}
