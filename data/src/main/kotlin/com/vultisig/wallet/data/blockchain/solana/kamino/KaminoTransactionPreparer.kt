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
 * 2. Compute budget goes on first. WalletCore appends these when absent, so doing this before the
 *    memo is what leaves the memo last — which the validator then requires.
 * 3. The attribution memo goes on last.
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

        return checkNotNull(SolanaTransaction.setComputeUnitPrice(withLimit, price.toString())) {
            "WalletCore could not set the Kamino compute-unit price"
        }
    }
}
