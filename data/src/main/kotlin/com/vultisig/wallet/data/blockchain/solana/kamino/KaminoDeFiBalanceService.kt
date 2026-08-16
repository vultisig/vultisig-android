package com.vultisig.wallet.data.blockchain.solana.kamino

import com.vultisig.wallet.data.api.KaminoApi
import com.vultisig.wallet.data.api.KaminoUserPositionJson
import com.vultisig.wallet.data.blockchain.DeFiService
import com.vultisig.wallet.data.blockchain.model.DeFiBalance
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.repositories.KaminoPositionCacheRepository
import com.vultisig.wallet.data.repositories.KaminoVaultSelectionRepository
import java.math.BigDecimal
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
 * card there and must not show a balance here either, or the two surfaces disagree.
 *
 * Mirrors iOS `DefiBalanceService.kaminoEarnTotalBalanceFiatDecimal`.
 */
class KaminoDeFiBalanceService(
    private val kaminoApi: KaminoApi,
    private val selectionRepository: KaminoVaultSelectionRepository,
    private val positionCache: KaminoPositionCacheRepository,
) : DeFiService {

    override suspend fun getRemoteDeFiBalance(address: String, vaultId: String): List<DeFiBalance> {
        val enabled = enabledVaults(vaultId)
        if (enabled.isEmpty()) return emptyList()

        return try {
            // One call for the whole wallet. An empty list is a real answer — the wallet holds no
            // position — while a throw is not knowing, which is why it is caught below rather than
            // collapsed into zero.
            val positions = kaminoApi.getUserPositions(address).associateBy { it.vaultAddress }
            val cached = positionCache.getPositions(vaultId)
            Timber.d(
                "KaminoDeFiBalanceService: %d enabled vaults, %d positions held",
                enabled.size,
                positions.size,
            )

            val amounts =
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

            positionCache.savePositions(vaultId, amounts.mapKeys { it.key.address })
            balancesOf(amounts)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "KaminoDeFiBalanceService: failed to read positions")
            getCacheDeFiBalance(address, vaultId)
        }
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

    private suspend fun enabledVaults(vaultId: String): List<KaminoVault> {
        val enabled = selectionRepository.getSelectedVaults(vaultId).first()
        return KaminoVaultRegistry.ALLOW_LIST.filter { it.address in enabled }
    }

    /**
     * What the wallet's shares in [vault] redeem for, or null when that cannot be established.
     *
     * Zero shares need no share price, so a wallet holding nothing in a vault resolves without a
     * metrics call — and cannot be left unknown by one failing.
     */
    private suspend fun amountOf(
        vault: KaminoVault,
        position: KaminoUserPositionJson?,
    ): BigInteger? {
        val shares = KaminoPositionMath.decimalOrNull(position?.totalShares) ?: BigDecimal.ZERO
        if (shares.signum() == 0) return BigInteger.ZERO

        val tokensPerShare =
            runCatching { kaminoApi.getVaultMetrics(vault.address) }
                .getOrNull()
                ?.let { KaminoPositionMath.decimalOrNull(it.tokensPerShare) } ?: return null

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
                    )
                }

        return if (balances.isEmpty()) emptyList()
        else listOf(DeFiBalance(chain = Chain.Solana, balances = balances))
    }
}
