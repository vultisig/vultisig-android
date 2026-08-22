package com.vultisig.wallet.data.blockchain.solana.kamino

import com.vultisig.wallet.data.api.KaminoApi
import com.vultisig.wallet.data.api.SolanaApi
import com.vultisig.wallet.data.chains.helpers.SolanaHelper
import com.vultisig.wallet.data.utils.runCatchingCancellable
import java.math.BigInteger
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import timber.log.Timber

/**
 * Rent-exempt minimum for a 165-byte SPL token account. Used only when the live read fails —
 * reserving nothing would be worse than reserving a value that has not moved in practice.
 */
val SPL_TOKEN_ACCOUNT_RENT_LAMPORTS: BigInteger = BigInteger.valueOf(2_039_280)

/**
 * Rent-exempt minimum Kamino charges when a first deposit creates the farms `UserState`. The
 * account size is Kamino-owned, so keep the observed mainnet lamport value here rather than
 * pretending the app can derive it from SPL token-account rent.
 */
val FARM_USER_STATE_RENT_LAMPORTS: BigInteger = BigInteger.valueOf(6_299_080)

/**
 * What the Solana runtime deducts per signature. These transactions carry exactly one.
 *
 * Deliberately not [SolanaHelper.DefaultFeeInLamports]: that 1,000,000 is a placeholder both
 * platforms *quote*, two hundred times what is charged. Padding a displayed figure costs nothing;
 * padding the figure a wallet is measured against refuses transactions it can afford, which is why
 * [kaminoChargeableLamports] exists alongside [kaminoNetworkFeeLamports].
 */
val SOLANA_SIGNATURE_FEE_LAMPORTS: BigInteger = BigInteger.valueOf(5_000)

/**
 * What a Kamino transaction costs its signer in SOL, beyond the amount itself.
 *
 * Shared by the initiating and the co-signing device on purpose. Both quote this figure on their
 * verify screens for the same transaction, and the only way two devices agree on a number is to
 * derive it the same way from the same inputs — the vault, the action, the unit price and the rent
 * below, all of which are identical on both sides (issue #5644).
 *
 * The base term is deliberately the arithmetic iOS applies to a relayed payload —
 * `SolanaHelper.defaultFeeInLamports`, the same 1,000,000, plus price × limit — so an iPhone
 * co-signer quotes one figure for one transaction rather than a second one of its own.
 *
 * @param unitPrice the micro-lamports-per-compute-unit price the bytes were priced with: read from
 *   the network on the device that builds them, taken from the payload on the device that receives
 *   them. [KaminoComputeBudget.unitPriceFor] is idempotent, so passing a recorded price back
 *   through yields the same price rather than a second clamp.
 * @param rentReserve what the transaction spends creating accounts, from [KaminoRentReserve] — the
 *   caller decides whether this transaction's rent belongs in the figure it is quoting.
 */
fun kaminoNetworkFeeLamports(
    vault: KaminoVault,
    action: KaminoAction,
    unitPrice: BigInteger?,
    rentReserve: BigInteger = BigInteger.ZERO,
): BigInteger =
    costLamports(
        base = SolanaHelper.DefaultFeeInLamports,
        vault = vault,
        action = action,
        unitPrice = unitPrice,
        rentReserve = rentReserve,
    )

/**
 * The least SOL the same transaction can be charged: the signature fee the runtime deducts, the
 * priority fee the bytes carry, and the rent for the accounts it creates.
 *
 * Separate from [kaminoNetworkFeeLamports] because the two answer different questions. That figure
 * goes on a screen and has to equal what an iPhone co-signer derives from the same payload, so its
 * base term is the 1,000,000-lamport placeholder both platforms pad with. This one decides whether
 * a wallet may submit at all, and there the padding is a refusal rather than a rounding: a Max
 * deposit into the SOL vault reserves the padded base and is charged 5,000, so it leaves exactly
 * 995,000 lamports behind — and a gate built on the padded figure then demands 1,400,000 for the
 * withdraw that exits the position, which the chain charges 405,000 for. The position could only be
 * unwound by funding SOL from outside the app.
 *
 * Every other term is shared with the quoted figure on purpose: what the wallet is checked against
 * must not drift from what it is told, beyond the padding this exists to drop.
 */
fun kaminoChargeableLamports(
    vault: KaminoVault,
    action: KaminoAction,
    unitPrice: BigInteger?,
    rentReserve: BigInteger = BigInteger.ZERO,
): BigInteger =
    costLamports(
        base = SOLANA_SIGNATURE_FEE_LAMPORTS,
        vault = vault,
        action = action,
        unitPrice = unitPrice,
        rentReserve = rentReserve,
    )

private fun costLamports(
    base: BigInteger,
    vault: KaminoVault,
    action: KaminoAction,
    unitPrice: BigInteger?,
    rentReserve: BigInteger,
): BigInteger =
    base +
        KaminoComputeBudget.priorityFeeLamports(
            vault = vault,
            action = action,
            networkPrice = unitPrice,
        ) +
        rentReserve

/**
 * The rent a Kamino transaction spends in SOL on accounts it has to create, in lamports.
 *
 * Always in SOL, whatever the vault's underlying token: a USDC deposit creates its share account
 * and its farms user-state out of the wallet's SOL, not out of the USDC being deposited. Whether
 * that rent belongs in a given figure is the caller's question, not this class's — a Max amount
 * reserves it only when the amount itself comes out of the same balance, and a quoted fee carries
 * it only where both devices carry it (issues #5607, #5644).
 *
 * Every read here is about the signer's own accounts, so a co-signing device asking the same
 * questions about the same wallet gets the same answers as the device that built the transaction.
 */
class KaminoRentReserve
@Inject
constructor(private val solanaApi: SolanaApi, private val kaminoApi: KaminoApi) {

    suspend operator fun invoke(
        vault: KaminoVault,
        walletAddress: String,
        action: KaminoAction,
    ): BigInteger =
        when (action) {
            KaminoAction.DEPOSIT -> depositRent(vault, walletAddress)
            KaminoAction.WITHDRAW -> withdrawRent(vault, walletAddress)
        }

    /**
     * Rent a deposit spends on the accounts it creates.
     *
     * Not a wrapped-SOL special case: a captured mainnet USDC deposit carries
     * `AssociatedToken::createIdempotent`, `kVault::deposit`, `farms::initialize_user` and
     * `farms::stake`, so a token vault pays for the share account and the farms user-state exactly
     * as the SOL vault does. Only the wrap itself is SOL-vault-only — nothing else wraps — so only
     * that term is gated on the mint.
     *
     * Each term is waived when the account already exists, which is what makes a redeposit cost the
     * fee alone. `createIdempotent` charges nothing for an account that is already there.
     *
     * The reads are independent of one another, so they run together: this sits in front of a
     * verify screen that cannot quote a fee until it answers, on both the initiating and the
     * co-signing device.
     */
    private suspend fun depositRent(vault: KaminoVault, walletAddress: String): BigInteger =
        coroutineScope {
            val tokenAccountRent = async { tokenAccountRentReserve() }
            val wrapsSol = vault.tokenMint == KaminoVaultRegistry.WRAPPED_SOL_MINT
            val hasWrappedSolAccount = async {
                wrapsSol && tokenAccountExists(walletAddress, vault.tokenMint)
            }
            val hasShareAccount = async { tokenAccountExists(walletAddress, vault.sharesMint) }
            val hasPosition = async { hasVaultPosition(walletAddress, vault) }

            val rent = tokenAccountRent.await()
            val wrappedSolRent =
                if (!wrapsSol || hasWrappedSolAccount.await()) BigInteger.ZERO else rent
            val shareAccountRent = if (hasShareAccount.await()) BigInteger.ZERO else rent
            val farmUserStateRent =
                if (hasPosition.await()) BigInteger.ZERO else FARM_USER_STATE_RENT_LAMPORTS

            wrappedSolRent + shareAccountRent + farmUserStateRent
        }

    /**
     * Rent a withdraw spends on the accounts it creates: the destination it pays the tokens into,
     * and the share account a staked exit releases the shares into. The captured unstaked withdraw
     * opens with one `AssociatedToken::createIdempotent` before `kVault::withdraw`; the staked
     * shape runs `farms::unstake` and `farms::withdraw_unstaked_deposits` ahead of it, and those
     * shares have to land in the wallet's own share account, created the same idempotent way.
     *
     * Two terms are the whole of it rather than a floor because the app already refuses anything
     * else: `KaminoTransactionValidator` rejects a Kamino transaction that creates an account other
     * than these two, so a third creation is one no device here would sign in the first place.
     *
     * The share term is not gated on the position being staked. Shares that are already unstaked
     * are sitting in that very account, so a wallet without one has no unstaked shares to withdraw
     * — and `createIdempotent` charges nothing for an account that is already there, which is what
     * keeps the ordinary exit costing the fee alone.
     */
    private suspend fun withdrawRent(vault: KaminoVault, walletAddress: String): BigInteger =
        coroutineScope {
            val tokenAccountRent = async { tokenAccountRentReserve() }
            val hasDestination = async { tokenAccountExists(walletAddress, vault.tokenMint) }
            val hasShareAccount = async { tokenAccountExists(walletAddress, vault.sharesMint) }

            val rent = tokenAccountRent.await()
            val destinationRent = if (hasDestination.await()) BigInteger.ZERO else rent
            val shareAccountRent = if (hasShareAccount.await()) BigInteger.ZERO else rent

            destinationRent + shareAccountRent
        }

    private suspend fun tokenAccountRentReserve(): BigInteger =
        runCatchingCancellable { solanaApi.getMinimumBalanceForRentExemption() }
            .onFailure {
                Timber.w(it, "Kamino rent-exemption read failed, reserving the pinned minimum")
            }
            .getOrNull()
            ?.takeIf { it.signum() > 0 } ?: SPL_TOKEN_ACCOUNT_RENT_LAMPORTS

    private suspend fun tokenAccountExists(walletAddress: String, mint: String): Boolean =
        runCatchingCancellable {
                solanaApi.getTokenAssociatedAccountByOwner(walletAddress, mint).first != null
            }
            // A failed read reserves the rent for an account that may already exist, which
            // overstates the fee rather than understating it — but it is a guess either way, and a
            // silent one is a fee nobody can account for afterwards.
            .onFailure { Timber.w(it, "Kamino token-account read failed for mint %s", mint) }
            .getOrDefault(false)

    /**
     * Whether the wallet already has a farm UserState account for this vault, so a redeposit does
     * not need to pay its rent again.
     *
     * Goes through [KaminoWithdrawEligibility.resolve] rather than checking for a bare entry in the
     * response: the same endpoint keeps a zero-share row for a wallet that has fully exited, and
     * the withdraw flow already treats that row as no position. Checking entry presence alone would
     * disagree with that and waive the rent on an account that may no longer exist.
     */
    private suspend fun hasVaultPosition(walletAddress: String, vault: KaminoVault): Boolean =
        runCatchingCancellable {
                val entry =
                    kaminoApi.getUserPositions(walletAddress).firstOrNull {
                        it.vaultAddress == vault.address
                    }
                KaminoWithdrawEligibility.resolve(entry, vault.sharesDecimals) is
                    KaminoWithdrawEligibility.Withdrawable
            }
            .onFailure { Timber.w(it, "Kamino positions read failed for vault %s", vault.address) }
            .getOrDefault(false)
}
