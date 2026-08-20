package com.vultisig.wallet.data.blockchain.solana.kamino

import com.vultisig.wallet.data.api.KaminoApi
import io.ktor.util.decodeBase64Bytes
import java.math.BigInteger
import java.math.RoundingMode
import javax.inject.Inject
import wallet.core.jni.CoinType
import wallet.core.jni.SolanaAddress
import wallet.core.jni.SolanaTransaction
import wallet.core.jni.TransactionDecoder
import wallet.core.jni.proto.Solana

/**
 * A decoded transaction, reduced to what validation reasons about.
 *
 * [feePayer] is the message's own account key 0 — the fee payer and first required signer. It has
 * to come from the message header rather than from any instruction's account list: an instruction
 * that happens to name the wallet first says nothing about who is authorising the transaction.
 */
data class KaminoDecodedTransaction(
    val feePayer: String?,
    val instructions: List<KaminoTxInstruction>,
)

/** Decodes a base64 Solana transaction into the instructions validation reasons about. */
object KaminoTransactionDecoder {

    fun decode(base64Transaction: String): KaminoDecodedTransaction {
        val decoded =
            TransactionDecoder.decode(CoinType.SOLANA, base64Transaction.decodeBase64Bytes())
        val output = Solana.DecodingTransactionOutput.parseFrom(decoded)
        check(output.hasTransaction()) { "transaction could not be decoded" }

        val transaction = output.transaction
        val (instructions, accountKeys) =
            when {
                transaction.hasV0() ->
                    transaction.v0.instructionsList to transaction.v0.accountKeysList
                transaction.hasLegacy() ->
                    transaction.legacy.instructionsList to transaction.legacy.accountKeysList
                else -> error("transaction is neither versioned nor legacy")
            }

        val decodedInstructions =
            instructions.map { instruction ->
                KaminoTxInstruction(
                    // A versioned message may not invoke a program loaded through a lookup table,
                    // so a
                    // program index always addresses the static keys.
                    programId = accountKeys.getOrNull(instruction.programId) ?: UNKNOWN_ACCOUNT,
                    data = instruction.programData.toByteArray(),
                    // Indices at or past the static keys address lookup-table-loaded accounts,
                    // which
                    // the
                    // decoder cannot resolve without fetching the tables. They are named as
                    // unresolved
                    // rather than silently dropped, so a check can tell "not the signer" from
                    // "unknown".
                    accounts =
                        instruction.accountsList.map { index ->
                            accountKeys.getOrNull(index) ?: UNKNOWN_ACCOUNT
                        },
                )
            }

        return KaminoDecodedTransaction(
            feePayer = accountKeys.firstOrNull(),
            instructions = decodedInstructions,
        )
    }

    /**
     * Stands in for an account the decode cannot name, rather than an empty string that reads as
     * absent.
     */
    const val UNKNOWN_ACCOUNT = "unknown"
}

/**
 * Turns a Kamino deposit or withdraw intent into transaction bytes ready for keysign.
 *
 * The order of operations is the contract:
 * 1. Kamino builds the transaction, embedding a recent blockhash. It carries no compute budget and
 *    ignores any fee hints in the request, so the app supplies both.
 * 2. Compute budget goes on first, as the two leading instructions: the unit limit at index 0 and
 *    the unit price at index 1. That is the layout iOS emits and matches the remaining instructions
 *    against positionally, so it is a cross-platform contract rather than a preference — get it
 *    wrong and an iPhone co-signer reads the transaction as unsigned-for and will not join.
 * 3. The attribution memo goes on last, after the budget, which is what leaves it unambiguously
 *    final — the position the validator then requires.
 * 4. The result is decoded and validated against the local registry, never against anything Kamino
 *    just said.
 *
 * The embedded blockhash lives about a minute, and an MPC ceremony can outlast it, so this must be
 * called immediately before keysign rather than when the user opens the form.
 */
class KaminoTransactionPreparer @Inject constructor(private val kaminoApi: KaminoApi) {

    /**
     * @param amount the decimal amount in the denomination the chosen endpoint expects — the
     *   underlying **token** for a deposit, vault **shares** for a withdraw. They are not
     *   interchangeable: confusing them is off by the share rate, which on the SOL vault is enough
     *   to exceed the held balance and trip the `u64::MAX` full-exit rewrite.
     * @param networkUnitPrice the app's current micro-lamports-per-unit sample, or null to use the
     *   floor
     * @return base64 transaction with a zeroed signature envelope, ready for the raw-signing path
     * @throws KaminoTransactionRejected if the assembled transaction is not one the app will sign
     */
    suspend fun prepare(
        vault: KaminoVault,
        action: KaminoAction,
        walletAddress: String,
        amount: String,
        networkUnitPrice: BigInteger? = null,
    ): String {
        val built =
            when (action) {
                KaminoAction.DEPOSIT -> kaminoApi.buildDeposit(walletAddress, vault.address, amount)
                KaminoAction.WITHDRAW ->
                    kaminoApi.buildWithdraw(walletAddress, vault.address, amount)
            }

        val withBudget = injectComputeBudget(built, vault, action, networkUnitPrice)
        val withMemo = KaminoAttributionMemo.append(withBudget)

        KaminoTransactionValidator.validate(
            decoded = KaminoTransactionDecoder.decode(withMemo),
            vault = vault,
            action = action,
            signerAddress = walletAddress,
            tokenAccount = associatedTokenAccount(walletAddress, vault.tokenMint),
            shareAccount = associatedTokenAccount(walletAddress, vault.sharesMint),
            farmUserState = KaminoFarmsUserState.derive(vault.farm, walletAddress),
            amountBaseUnits = baseUnits(amount, vault, action),
        )

        return withMemo
    }

    /**
     * The wallet's associated token account for [mint], derived from the signer and a mint the
     * registry pins rather than read out of the transaction being checked — which is the whole
     * point of it: an address that came out of the response could not check the response.
     *
     * Two of these are derived per transaction. The one for the vault's underlying mint is where a
     * deposit's tokens come from and where a withdraw pays out — and on the SOL vault it is also
     * the wrapped-SOL account a wrap funds. The one for its share mint is what ties an instruction
     * to *this* vault at all: the vault account itself travels in an address lookup table that this
     * decode does not resolve.
     *
     * Null when WalletCore cannot derive it, which the validator treats as a refusal rather than as
     * a check it may skip.
     */
    private fun associatedTokenAccount(walletAddress: String, mint: String): String? =
        runCatching { SolanaAddress(walletAddress).defaultTokenAddress(mint) }.getOrNull()

    /**
     * The amount that was sent, in the base units the instruction carries — tokens for a deposit,
     * shares for a withdraw, matching the `u64` each kVault instruction takes.
     *
     * Converted from the very string this app handed the API, so the comparison downstream is
     * against the request rather than against anything the response echoed back.
     *
     * Truncates rather than throwing on sub-unit precision: the amount screen already quantises
     * input to the token's own scale before it gets here, so this is a floor over a number that has
     * nothing to floor. Null when the string is not a positive decimal at all, which the validator
     * refuses rather than skips.
     */
    private fun baseUnits(amount: String, vault: KaminoVault, action: KaminoAction): BigInteger? {
        val decimals =
            when (action) {
                KaminoAction.DEPOSIT -> vault.tokenDecimals
                KaminoAction.WITHDRAW -> vault.sharesDecimals
            }
        return amount
            .toBigDecimalOrNull()
            ?.movePointRight(decimals)
            ?.setScale(0, RoundingMode.DOWN)
            ?.toBigInteger()
            ?.takeIf { it.signum() > 0 }
    }

    private fun injectComputeBudget(
        base64Transaction: String,
        vault: KaminoVault,
        action: KaminoAction,
        networkUnitPrice: BigInteger?,
    ): String {
        val limit = KaminoComputeBudget.unitLimitFor(vault, action)
        val price = KaminoComputeBudget.unitPriceFor(networkUnitPrice)

        // WalletCore collapses every failure into a null return, so each step is checked. Signing a
        // transaction with no compute-unit limit would abort it on chain: these consume far more
        // than
        // the runtime default.
        val withLimit =
            checkNotNull(
                SolanaTransaction.setComputeUnitLimit(base64Transaction, limit.toString())
            ) {
                "WalletCore could not set the Kamino compute-unit limit"
            }

        // Two assumptions are read back here rather than trusted, because the insert below adds a
        // price unconditionally where `setComputeUnitPrice` used to overwrite one in place.
        //
        // Where the limit landed: WalletCore chooses that — index 0, unless the first instruction
        // is `AdvanceNonceAccount`, which Kamino never builds. Anything but 0 is not the layout iOS
        // emits, and is refused here rather than sent to a co-signer that would silently decline
        // it.
        //
        // That it is the only one: Kamino's responses carry no compute budget today. Should one
        // ever arrive carrying its own *price*, `setComputeUnitLimit` would leave it untouched and
        // the insert below would make two `SetComputeUnitPrice` instructions — which the chain
        // rejects, after a full signing ceremony has already been spent on it.
        val instructions = KaminoTransactionDecoder.decode(withLimit).instructions
        val budgetIndices =
            instructions.indices.filter {
                instructions[it].programId == KaminoComputeBudget.PROGRAM_ID
            }
        check(budgetIndices == listOf(UNIT_LIMIT_INDEX)) {
            "expected the app's compute-unit limit alone at index $UNIT_LIMIT_INDEX, " +
                "found ComputeBudget instructions at $budgetIndices"
        }

        // Deliberately not `SolanaTransaction.setComputeUnitPrice`. The two helpers place their
        // instruction differently: the limit is *inserted at the front*, the price is *appended*.
        // Kamino's responses carry no compute budget, so that pair leaves the price behind every
        // instruction Kamino built, and an iOS co-signer — which emits the two at 0 and 1 and reads
        // the rest positionally — finds the wrong instruction where the price belongs, marks the
        // transaction unreadable and refuses to join. Swapping the call order does not help; the
        // limit still lands at index 0.
        return checkNotNull(
            SolanaTransaction.insertInstruction(
                withLimit,
                UNIT_PRICE_INDEX,
                KaminoComputeBudget.setUnitPriceInstructionJson(price),
            )
        ) {
            "WalletCore could not set the Kamino compute-unit price"
        }
    }

    private companion object {
        private const val UNIT_LIMIT_INDEX = 0

        /** Right after the limit, which is where iOS puts it. */
        private const val UNIT_PRICE_INDEX = UNIT_LIMIT_INDEX + 1
    }
}
