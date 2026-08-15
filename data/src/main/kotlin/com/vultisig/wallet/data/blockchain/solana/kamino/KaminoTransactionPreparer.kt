package com.vultisig.wallet.data.blockchain.solana.kamino

import com.vultisig.wallet.data.api.KaminoApi
import io.ktor.util.decodeBase64Bytes
import java.math.BigInteger
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
            wrappedSolAccount = wrappedSolAccountFor(vault, walletAddress),
        )

        return withMemo
    }

    /**
     * The wrapped-SOL associated token account a wrap must pay into, derived from the signer and
     * the vault's own mint rather than read out of the transaction being checked.
     *
     * Null for vaults whose underlying is a plain token, which have no wrap to make.
     */
    private fun wrappedSolAccountFor(vault: KaminoVault, walletAddress: String): String? {
        if (vault.tokenMint != KaminoVaultRegistry.WRAPPED_SOL_MINT) return null
        return runCatching {
                SolanaAddress(walletAddress)
                    .defaultTokenAddress(KaminoVaultRegistry.WRAPPED_SOL_MINT)
            }
            .getOrNull()
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

        // WalletCore chooses where the limit goes — index 0, unless the first instruction is
        // `AdvanceNonceAccount`, which Kamino never builds — so the position is observed rather
        // than
        // assumed. Anything but 0 is not the layout iOS emits, and is refused here rather than sent
        // to a co-signer that would silently decline it.
        val limitIndex =
            KaminoTransactionDecoder.decode(withLimit).instructions.indexOfFirst {
                it.programId == KaminoComputeBudget.PROGRAM_ID
            }
        check(limitIndex == UNIT_LIMIT_INDEX) {
            "WalletCore put the Kamino compute-unit limit at index $limitIndex, not $UNIT_LIMIT_INDEX"
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
