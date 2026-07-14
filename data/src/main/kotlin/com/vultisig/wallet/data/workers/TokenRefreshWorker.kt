package com.vultisig.wallet.data.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.repositories.DEFI_ONLY_THORCHAIN_DENOMS
import com.vultisig.wallet.data.repositories.TokenRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import timber.log.Timber

@HiltWorker
internal class TokenRefreshWorker
@AssistedInject
constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val tokenRepository: TokenRepository,
    private val vaultRepository: VaultRepository,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {

        val inputVaultId = inputData.getString(ARG_VAULT_ID)
        val inputChainId = inputData.getString(ARG_CHAIN)

        val vaults =
            if (inputVaultId == null) {
                vaultRepository.getAll()
            } else {
                listOf(vaultRepository.get(inputVaultId) ?: return Result.failure())
            }

        // Any transient failure below reschedules the whole run (with backoff) instead of
        // dropping the affected chain permanently.
        var hasRefreshFailure = false

        for (vault in vaults) {
            val allVaultChains = vault.coins.map { it.chain }.toSet()
            val chains =
                if (inputChainId == null) {
                    allVaultChains
                } else {
                    allVaultChains.filter { it.id == inputChainId }
                }

            val disabledCoinIds = vaultRepository.getDisabledCoinIds(vaultId = vault.id)
            val enabledCoinIds =
                try {
                    vaultRepository.getEnabledTokens(vaultId = vault.id).first()
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    Timber.e(e)
                    return retryOrFail()
                }

            for (chain in chains) {
                try {
                    cleanupDeFiOnlyTokens(vault, chain)
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    Timber.e(e)
                }

                try {
                    tokenRepository
                        .getRefreshTokens(chain, vault)
                        .filter { disabledCoinIds.none { disabledId -> disabledId == it.id } }
                        .forEach { refreshToken ->
                            addRefreshTokenToVault(enabledCoinIds, refreshToken, vault)
                        }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    Timber.e(e)
                    hasRefreshFailure = true
                }

                if (chains.size > 1 && vaults.size > 1)
                    delay(1000) // should be removed when we use api without rate limit
            }
        }
        return if (hasRefreshFailure) retryOrFail() else Result.success()
    }

    // WorkManager has no built-in attempt cap, so bound it here to stop rescheduling permanent
    // failures.
    private fun retryOrFail(): Result =
        if (runAttemptCount < MAX_REFRESH_ATTEMPTS) Result.retry() else Result.failure()

    private suspend fun cleanupDeFiOnlyTokens(vault: Vault, chain: Chain) {
        if (chain != Chain.ThorChain) return
        vault.coins
            .filter { it.chain == chain && it.contractAddress in DEFI_ONLY_THORCHAIN_DENOMS }
            .forEach { vaultRepository.deleteTokenFromVault(vault.id, it) }
    }

    private suspend fun addRefreshTokenToVault(
        enabledCoinIds: List<Coin>,
        refreshToken: Coin,
        vault: Vault,
    ) {
        val existing =
            enabledCoinIds.firstOrNull { enabledCoinId ->
                enabledCoinId.id.equals(refreshToken.id, ignoreCase = true)
            }
        if (existing != null) {
            // The token is already tracked. The id match is case-insensitive, so a stale entry
            // whose curated identity has since been corrected (e.g. a ticker recased from "BRUNE"
            // to "bRUNE", or a canonicalized contractAddress/decimal) would otherwise be kept
            // forever. Overwrite it with the freshly derived identity when they disagree.
            if (existing.needsIdentityCorrection(refreshToken)) {
                vaultRepository.deleteTokenFromVault(vault.id, existing)
                vaultRepository.addTokenToVault(vault.id, refreshToken)
            }
            return
        }

        val withSameContractCoin =
            enabledCoinIds.firstOrNull { enabledCoin ->
                enabledCoin.contractAddress == refreshToken.contractAddress
            }

        withSameContractCoin?.let { vaultRepository.deleteTokenFromVault(vault.id, it) }

        vaultRepository.addTokenToVault(vault.id, refreshToken)
    }

    // True when a persisted coin's curated identity (case-sensitive ticker, contractAddress, or
    // decimal) has drifted from the freshly derived one and should be overwritten. Deliberately
    // excludes address/hexPublicKey, which are vault-derived and always match for the same id.
    private fun Coin.needsIdentityCorrection(refreshToken: Coin): Boolean =
        ticker != refreshToken.ticker ||
            contractAddress != refreshToken.contractAddress ||
            decimal != refreshToken.decimal

    companion object {
        const val ARG_VAULT_ID = "vault_id"
        const val ARG_CHAIN = "chain_id"
        const val MAX_REFRESH_ATTEMPTS = 3
    }
}
