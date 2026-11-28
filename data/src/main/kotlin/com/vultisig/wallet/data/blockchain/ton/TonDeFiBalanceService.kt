package com.vultisig.wallet.data.blockchain.ton

import com.vultisig.wallet.data.api.chains.ton.TonAccountStakingInfoJson
import com.vultisig.wallet.data.api.chains.ton.TonStakingApi
import com.vultisig.wallet.data.api.chains.ton.TonStakingPoolInfoJson
import com.vultisig.wallet.data.blockchain.DeFiService
import com.vultisig.wallet.data.blockchain.model.DeFiBalance
import com.vultisig.wallet.data.blockchain.model.StakingDetails
import com.vultisig.wallet.data.blockchain.model.StakingDetails.Companion.generateId
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.repositories.StakingDetailsRepository
import com.vultisig.wallet.data.utils.NetworkException
import java.io.IOException
import java.math.BigInteger
import kotlin.coroutines.cancellation.CancellationException
import timber.log.Timber

/**
 * Reads TON staking positions for the DeFi/Earn tab: the native nominator-pool stake and the
 * Tonstakers liquid position.
 *
 * The visible staked balance is the **primary** pool's active stake plus any `pending_deposit` — a
 * freshly placed stake sits in pending until the next validation cycle, so it must still surface
 * immediately. When an account holds positions in several pools, only the largest is treated as the
 * primary (mirrors vultisig-ios `TonStakeInteractor`). The position is decorated with the pool's
 * APY fetched from `/v2/staking/pool/{address}`.
 *
 * The Tonstakers position is the vault's tsTON balance, reported in tsTON so the DeFi row reads it
 * against the tsTON price. It is emitted as its own balance because the two are different
 * mechanisms with different receipts, and folding them together would deny the liquid position its
 * own row on the Portfolio DeFi list.
 *
 * Both positions are persisted as [StakingDetails] rows (the nominator stake keyed by
 * [Coins.Ton.TON], the liquid position by [Coins.Ton.TSTON]), so the cached read answers with both
 * and a failed read falls back to the last-known amount instead of dropping the row.
 */
class TonDeFiBalanceService(
    private val tonStakingApi: TonStakingApi,
    private val liquidStakingService: TonLiquidStakingService,
    private val stakingDetailsRepository: StakingDetailsRepository,
) : DeFiService {

    override suspend fun getRemoteDeFiBalance(address: String, vaultId: String): List<DeFiBalance> {
        // Read first, independently of the nominator position and outside the fallback: a vault can
        // hold one, the other, or both, a liquid-only holder must not be answered with an empty
        // list, and a nominator read that fails afterwards must not cost this answer its row.
        val liquid = resolveLiquidBalance(address, vaultId)

        return try {
            val primary =
                tonStakingApi.getNominatorPools(address).maxByOrNull { it.stakedTotal() }
                    ?: return persistAndEmpty(vaultId, liquid)

            val poolInfo = fetchPoolInfo(primary.pool)

            // Mirror TonDeFiPositionsViewModel: a liquid-staking Tonstakers (`liquidTF`) position
            // is
            // not a native nominator stake, so it must not count toward the DeFi total — otherwise
            // the same stake reads zero on the DeFi screen but nonzero in the Portfolio total. A
            // metadata miss (null implementation) is treated permissively so a transient tonapi
            // failure can't erase a genuine nominator position.
            val isNominatorStake =
                poolInfo?.implementation == null ||
                    TonNominatorPool.isNominatorImplementation(poolInfo.implementation)

            val staked = if (isNominatorStake) primary.stakedTotal() else BigInteger.ZERO
            val apr = poolInfo?.apy?.let { it / 100 }

            persistStakedBalance(vaultId, staked, apr)

            tonDeFiBalances(staked, liquid)
        } catch (e: NetworkException) {
            Timber.w(e, "TonDeFiBalanceService: Network error fetching nominator-pool balance")
            // Keep the last-known stake rather than erasing it; the empty result would otherwise be
            // cached by BalanceRepository and hide the position until the next invalidation. The
            // liquid balance is the one this read did get, so it is carried through rather than
            // dropped to zero.
            deFiBalancesFromCache(vaultId, liquid)
        } catch (e: IOException) {
            Timber.w(e, "TonDeFiBalanceService: Network error fetching nominator-pool balance")
            deFiBalancesFromCache(vaultId, liquid)
        }
    }

    /**
     * Best-effort pool metadata (implementation + APY). A lookup failure must neither drop an
     * otherwise-valid position nor misclassify it, so a miss returns `null` and callers treat that
     * permissively. tonapi returns `apy` as a percentage (e.g. `13.27` = 13.27%); the caller scales
     * it to the fraction `StakingDetails.apr` stores (the DeFi screen's `formatPercentage` ×100).
     */
    private suspend fun fetchPoolInfo(poolAddress: String): TonStakingPoolInfoJson? =
        try {
            tonStakingApi.getStakingPool(poolAddress)
        } catch (e: NetworkException) {
            Timber.w(e, "TonDeFiBalanceService: Failed to fetch TON staking pool info")
            null
        } catch (e: IOException) {
            Timber.w(e, "TonDeFiBalanceService: Failed to fetch TON staking pool info")
            null
        }

    override suspend fun getCacheDeFiBalance(address: String, vaultId: String): List<DeFiBalance> =
        // tsTON is usually not a wallet coin, so the wallet's token cache can't answer for it: the
        // liquid position has to come from its own persisted row, or a cache-only reader (the vault
        // list, the header's first emission) drops it.
        deFiBalancesFromCache(vaultId, cachedAmount(vaultId, Coins.Ton.TSTON))

    /** The persisted nominator stake, paired with whatever the liquid read produced. */
    private suspend fun deFiBalancesFromCache(
        vaultId: String,
        liquid: BigInteger,
    ): List<DeFiBalance> {
        return tonDeFiBalances(cachedAmount(vaultId, Coins.Ton.TON), liquid)
    }

    private suspend fun cachedAmount(vaultId: String, coin: Coin): BigInteger =
        stakingDetailsRepository.getStakingDetailsByCoindId(vaultId, coin.id)?.stakeAmount
            ?: BigInteger.ZERO

    /** Drops the cached Tonstakers position, so the next read goes to the chain. */
    suspend fun invalidateLiquidPosition(address: String) {
        liquidStakingService.invalidate(address)
    }

    /**
     * The vault's tsTON balance. A successful read (zero included: the position was closed) is
     * persisted; a failed one falls back to the last persisted amount, so a transient tonapi failure
     * neither erases the row from the cache nor costs the nominator position its row.
     */
    private suspend fun resolveLiquidBalance(address: String, vaultId: String): BigInteger {
        val fetched = fetchLiquidBalance(address) ?: return cachedAmount(vaultId, Coins.Ton.TSTON)
        persistDetails(vaultId, Coins.Ton.TSTON, fetched, apr = null)
        return fetched
    }

    /** The vault's tsTON balance, or `null` when the lookup failed. */
    private suspend fun fetchLiquidBalance(address: String): BigInteger? =
        try {
            liquidStakingService.getPosition(address).tsTonBalance
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "TonDeFiBalanceService: Failed to fetch the Tonstakers position")
            null
        }

    /** One balance per mechanism, each omitted when it holds nothing. */
    private fun tonDeFiBalances(staked: BigInteger, tsTon: BigInteger): List<DeFiBalance> {
        val balances = buildList {
            if (staked.signum() > 0) {
                add(DeFiBalance.Balance(coin = Coins.Ton.TON, amount = staked))
            }
            if (tsTon.signum() > 0) {
                add(DeFiBalance.Balance(coin = Coins.Ton.TSTON, amount = tsTon))
            }
        }
        return if (balances.isEmpty()) emptyList() else listOf(DeFiBalance(Chain.Ton, balances))
    }

    private suspend fun persistAndEmpty(vaultId: String, liquid: BigInteger): List<DeFiBalance> {
        persistStakedBalance(vaultId, BigInteger.ZERO, apr = null)
        return tonDeFiBalances(BigInteger.ZERO, liquid)
    }

    private suspend fun persistStakedBalance(vaultId: String, staked: BigInteger, apr: Double?) =
        persistDetails(vaultId, Coins.Ton.TON, staked, apr)

    /** Upserts [coin]'s row, skipping the write when nothing changed. Best-effort. */
    private suspend fun persistDetails(
        vaultId: String,
        coin: Coin,
        amount: BigInteger,
        apr: Double?,
    ) {
        try {
            val details =
                StakingDetails(
                    id = coin.generateId(),
                    coin = coin,
                    stakeAmount = amount,
                    apr = apr,
                    estimatedRewards = null,
                    nextPayoutDate = null,
                    rewards = null,
                    rewardsCoin = null,
                )
            val existing = stakingDetailsRepository.getStakingDetailsByCoindId(vaultId, coin.id)
            when {
                existing == null -> stakingDetailsRepository.saveStakingDetails(vaultId, details)
                existing.stakeAmount != amount || existing.apr != apr ->
                    stakingDetailsRepository.updateStakingDetails(vaultId, details)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "TonDeFiBalanceService: Failed to persist %s balance", coin.ticker)
        }
    }

    private fun TonAccountStakingInfoJson.stakedTotal(): BigInteger =
        BigInteger.valueOf(amount) + BigInteger.valueOf(pendingDeposit)
}
