package com.vultisig.wallet.data.blockchain.solana.kamino

import com.vultisig.wallet.data.api.KaminoApi
import com.vultisig.wallet.data.api.KaminoUserPositionJson
import com.vultisig.wallet.data.blockchain.DeFiService
import com.vultisig.wallet.data.blockchain.model.DeFiBalance
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.repositories.KaminoPositionCacheRepository
import com.vultisig.wallet.data.repositories.KaminoVaultSelectionRepository
import java.math.BigInteger
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.supervisorScope
import timber.log.Timber

/**
 * DeFi-balance provider for the Kamino Earn vaults on Solana.
 *
 * A position is held as vault shares, which are worth a live, per-vault multiple of the underlying
 * token, so the balance reported here is the token amount those shares redeem for — denominated in
 * the vault's own underlying (USDC, SOL) rather than in shares, which the app has no price for and
 * deliberately keeps out of wallet-token discovery.
 *
 * Gated on the per-vault opt-in, matching the Earn tab: a vault the user has switched off shows no
 * card there and must not count here either.
 *
 * On a failed read the two surfaces do part company, deliberately. A card can say it does not know
 * the position and the user reads that; a portfolio total has no such affordance, so leaving the
 * position out would quietly understate every figure above it. This side therefore keeps the last
 * known size while the card shows the position as unavailable.
 *
 * Mirrors iOS `DefiBalanceService.kaminoEarnTotalBalanceFiatDecimal`.
 */
class KaminoDeFiBalanceService(
    private val kaminoApi: KaminoApi,
    private val selectionRepository: KaminoVaultSelectionRepository,
    private val positionCache: KaminoPositionCacheRepository,
) : DeFiService {

    override suspend fun getRemoteDeFiBalance(address: String, vaultId: String): List<DeFiBalance> {
        val amounts =
            try {
                // The opt-in read is inside the guard like every other read here: this service is
                // one of two the Solana provider awaits side by side, so an escaping throw would
                // take the healthy staking figure down with it.
                val enabled = enabledVaults(vaultId)
                if (enabled.isEmpty()) return emptyList()

                // One call for the whole wallet. An empty list is a real answer — the wallet holds
                // no position — while a throw is not knowing, which is why it is caught below
                // rather than collapsed into zero.
                val positions = kaminoApi.getUserPositions(address).associateBy { it.vaultAddress }
                val cached = positionCache.getPositions(vaultId)
                Timber.d(
                    "KaminoDeFiBalanceService: %d enabled vaults, %d positions held",
                    enabled.size,
                    positions.size,
                )

                supervisorScope {
                        enabled
                            .map { vault ->
                                async {
                                    // A vault whose own read failed keeps its last known size:
                                    // reporting zero would read as a deposit that has gone.
                                    val amount =
                                        amountOf(vault, positions[vault.address])
                                            ?: cached[vault.address]
                                    amount?.let { vault to it }
                                }
                            }
                            .awaitAll()
                    }
                    .filterNotNull()
                    .toMap()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "KaminoDeFiBalanceService: failed to read positions")
                return getCacheDeFiBalance(address, vaultId)
            }

        // Persisted outside the read's fallback: a snapshot the store refused to keep costs the
        // next cold start a stale figure, whereas discarding what was just read costs this one its
        // whole position.
        persistSnapshot(vaultId, amounts)
        return balancesOf(amounts)
    }

    override suspend fun getCacheDeFiBalance(address: String, vaultId: String): List<DeFiBalance> {
        return try {
            val enabled = enabledVaults(vaultId)
            if (enabled.isEmpty()) return emptyList()
            val cached = positionCache.getPositions(vaultId)
            balancesOf(
                enabled.mapNotNull { vault -> cached[vault.address]?.let { vault to it } }.toMap()
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "KaminoDeFiBalanceService: failed to read the cached positions")
            emptyList()
        }
    }

    private suspend fun persistSnapshot(vaultId: String, amounts: Map<KaminoVault, BigInteger>) {
        try {
            positionCache.savePositions(vaultId, amounts.mapKeys { it.key.address })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "KaminoDeFiBalanceService: failed to persist the position snapshot")
        }
    }

    private suspend fun enabledVaults(vaultId: String): List<KaminoVault> {
        val enabled = selectionRepository.getSelectedVaults(vaultId).first()
        return KaminoVaultRegistry.ALLOW_LIST.filter { it.address in enabled }
    }

    /**
     * What the wallet's shares in [vault] redeem for, or null when that cannot be established.
     *
     * No entry for the vault is a real answer — the wallet holds nothing in it — and zero shares
     * need no share price, so either resolves without a metrics call and cannot be left unknown by
     * one failing. A share count that will not parse, or one below zero, is not an answer: it is
     * read as not knowing, so the last known size stands rather than being overwritten by a zero.
     */
    private suspend fun amountOf(
        vault: KaminoVault,
        position: KaminoUserPositionJson?,
    ): BigInteger? {
        if (position == null) return BigInteger.ZERO

        val shares =
            KaminoPositionMath.decimalOrNull(position.totalShares)?.takeIf { it.signum() >= 0 }
                ?: return null
        if (shares.signum() == 0) return BigInteger.ZERO

        val metrics =
            try {
                kaminoApi.getVaultMetrics(vault.address)
            } catch (e: CancellationException) {
                // runCatching would hand a cancelled read back as "no metrics", so the vault would
                // quietly resolve from cache instead of the load stopping.
                throw e
            } catch (e: Exception) {
                Timber.w(e, "KaminoDeFiBalanceService: failed to read the metrics of a vault")
                null
            }

        // A share price of zero is not a price — it values a real deposit at nothing — so it counts
        // as not knowing. Answering zero would also persist over the position's last known size.
        val tokensPerShare =
            KaminoPositionMath.decimalOrNull(metrics?.tokensPerShare)?.takeIf { it.signum() > 0 }
                ?: return null

        return KaminoPositionMath.tokenAmount(shares, tokensPerShare, vault.tokenDecimals)
            .movePointRight(vault.tokenDecimals)
            .toBigInteger()
    }

    /**
     * One balance per underlying token, not per vault: two of the curated vaults are USDC, and the
     * balance pipeline resolves a chain's DeFi position by coin, so two entries for the same coin
     * would leave whichever came second unread.
     */
    private fun balancesOf(amounts: Map<KaminoVault, BigInteger>): List<DeFiBalance> {
        val balances =
            amounts
                .mapNotNull { (vault, amount) ->
                    vault.coin?.takeIf { amount.signum() > 0 }?.let { coin -> coin to amount }
                }
                .groupBy { (coin, _) -> coin.id.lowercase() }
                .map { (_, entries) ->
                    DeFiBalance.Balance(
                        coin = entries.first().first,
                        amount =
                            entries.fold(BigInteger.ZERO) { running, (_, amount) ->
                                running + amount
                            },
                        positionCount = entries.size,
                    )
                }

        return if (balances.isEmpty()) emptyList()
        else listOf(DeFiBalance(chain = Chain.Solana, balances = balances))
    }
}
