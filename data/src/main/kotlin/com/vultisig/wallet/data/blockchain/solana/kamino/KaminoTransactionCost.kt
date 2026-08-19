package com.vultisig.wallet.data.blockchain.solana.kamino

import com.vultisig.wallet.data.api.KaminoApi
import com.vultisig.wallet.data.api.SolanaApi
import com.vultisig.wallet.data.chains.helpers.SolanaHelper
import com.vultisig.wallet.data.utils.runCatchingCancellable
import java.math.BigInteger
import javax.inject.Inject

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
 * What a Kamino transaction costs its signer in SOL, beyond the amount itself.
 *
 * Shared by the initiating and the co-signing device on purpose. Both quote this figure on their
 * verify screens for the same transaction, and the only way two devices agree on a number is to
 * derive it the same way from the same inputs — the vault, the action, the relayed unit price and
 * the rent below, all of which are identical on both sides (issue #5644).
 *
 * The base term is deliberately the arithmetic iOS applies to a relayed payload —
 * `SolanaHelper.defaultFeeInLamports`, the same 1,000,000, plus price × limit — so an iPhone
 * co-signer quotes one figure for one transaction rather than a second one of its own.
 *
 * @param relayedUnitPrice the micro-lamports-per-compute-unit price recorded in the payload, which
 *   is the one the bytes were priced with. [KaminoComputeBudget.unitPriceFor] is idempotent, so
 *   passing it back through yields the same price rather than a second clamp.
 * @param rentReserve what the transaction spends creating accounts, from [KaminoDepositRentReserve]
 *   — zero for everything but a first native-SOL deposit.
 */
fun kaminoNetworkFeeLamports(
    vault: KaminoVault,
    action: KaminoAction,
    relayedUnitPrice: BigInteger?,
    rentReserve: BigInteger = BigInteger.ZERO,
): BigInteger =
    SolanaHelper.DefaultFeeInLamports +
        KaminoComputeBudget.priorityFeeLamports(
            vault = vault,
            action = action,
            networkPrice = relayedUnitPrice,
        ) +
        rentReserve

/**
 * The rent a Kamino deposit spends on accounts it has to create, in lamports.
 *
 * Only the wrapped-SOL vault pays this out of the same balance the deposit comes from, which is why
 * a token vault always answers zero: a token deposit pays its rent in SOL, not in the token being
 * deposited, and the token form has no reason to reserve against it.
 *
 * Every read here is about the signer's own accounts, so a co-signing device asking the same
 * questions about the same wallet gets the same answers as the device that built the transaction.
 */
class KaminoDepositRentReserve
@Inject
constructor(private val solanaApi: SolanaApi, private val kaminoApi: KaminoApi) {

    suspend operator fun invoke(vault: KaminoVault, walletAddress: String): BigInteger {
        if (vault.tokenMint != KaminoVaultRegistry.WRAPPED_SOL_MINT) return BigInteger.ZERO

        val tokenAccountRent = tokenAccountRentReserve()
        val wrappedSolRent =
            tokenAccountRent.takeUnless { tokenAccountExists(walletAddress, vault.tokenMint) }
                ?: BigInteger.ZERO
        val shareAccountRent =
            tokenAccountRent.takeUnless { tokenAccountExists(walletAddress, vault.sharesMint) }
                ?: BigInteger.ZERO
        val farmUserStateRent =
            FARM_USER_STATE_RENT_LAMPORTS.takeUnless { hasVaultPosition(walletAddress, vault) }
                ?: BigInteger.ZERO

        return wrappedSolRent + shareAccountRent + farmUserStateRent
    }

    private suspend fun tokenAccountRentReserve(): BigInteger =
        runCatchingCancellable { solanaApi.getMinimumBalanceForRentExemption() }
            .getOrNull()
            ?.takeIf { it.signum() > 0 } ?: SPL_TOKEN_ACCOUNT_RENT_LAMPORTS

    private suspend fun tokenAccountExists(walletAddress: String, mint: String): Boolean =
        runCatchingCancellable {
                solanaApi.getTokenAssociatedAccountByOwner(walletAddress, mint).first != null
            }
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
            .getOrDefault(false)
}
