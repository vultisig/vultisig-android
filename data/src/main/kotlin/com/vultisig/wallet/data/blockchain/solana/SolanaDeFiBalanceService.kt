package com.vultisig.wallet.data.blockchain.solana

import com.vultisig.wallet.data.blockchain.DeFiService
import com.vultisig.wallet.data.blockchain.model.DeFiBalance
import com.vultisig.wallet.data.blockchain.solana.kamino.KaminoDeFiBalanceService
import com.vultisig.wallet.data.blockchain.solana.staking.SolanaStakingDeFiBalanceService
import com.vultisig.wallet.data.models.Chain
import java.math.BigInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Solana's DeFi position, which is two independent things: native staking and the Kamino Earn
 * vaults. Neither implies the other — a wallet may hold either, both or none — so the chain's
 * balance is their sum, the way iOS totals it in `DefiBalanceService.totalBalanceInFiat`.
 *
 * Each side already falls back to its own last-known values rather than throwing, so one being
 * unreachable leaves the other's figure standing.
 */
class SolanaDeFiBalanceService(
    private val stakingBalanceService: SolanaStakingDeFiBalanceService,
    private val kaminoBalanceService: KaminoDeFiBalanceService,
) : DeFiService {

    override suspend fun getRemoteDeFiBalance(address: String, vaultId: String): List<DeFiBalance> =
        coroutineScope {
            val staking = async { stakingBalanceService.getRemoteDeFiBalance(address, vaultId) }
            val kamino = async { kaminoBalanceService.getRemoteDeFiBalance(address, vaultId) }
            merge(staking.await() + kamino.await())
        }

    override suspend fun getCacheDeFiBalance(address: String, vaultId: String): List<DeFiBalance> =
        merge(
            stakingBalanceService.getCacheDeFiBalance(address, vaultId) +
                kaminoBalanceService.getCacheDeFiBalance(address, vaultId)
        )

    /**
     * Sums the two sides per coin.
     *
     * Not a concatenation: the SOL Earn vault reports in the same coin native staking does, and the
     * balance pipeline resolves a chain's position by coin — matching on the first entry it finds
     * on the refresh path and on the last one it saw on the cached path. Either way, one of the two
     * SOL figures would be dropped and the row would silently disagree with itself between a cold
     * start and a refresh.
     */
    private fun merge(balances: List<DeFiBalance>): List<DeFiBalance> {
        val perCoin =
            balances
                .flatMap { it.balances }
                .groupBy { it.coin.id.lowercase() }
                .map { (_, group) ->
                    group.reduce { running, balance ->
                        running.copy(amount = running.amount + balance.amount)
                    }
                }
                .filter { it.amount > BigInteger.ZERO }

        return if (perCoin.isEmpty()) emptyList()
        else listOf(DeFiBalance(chain = Chain.Solana, balances = perCoin))
    }
}
