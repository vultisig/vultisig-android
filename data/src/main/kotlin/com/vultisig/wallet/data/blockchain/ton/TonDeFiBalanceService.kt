package com.vultisig.wallet.data.blockchain.ton

import com.vultisig.wallet.data.api.chains.ton.TonAccountStakingInfoJson
import com.vultisig.wallet.data.api.chains.ton.TonStakingApi
import com.vultisig.wallet.data.api.chains.ton.TonStakingPoolInfoJson
import com.vultisig.wallet.data.blockchain.DeFiService
import com.vultisig.wallet.data.blockchain.model.DeFiBalance
import com.vultisig.wallet.data.blockchain.model.StakingDetails
import com.vultisig.wallet.data.blockchain.model.StakingDetails.Companion.generateId
import com.vultisig.wallet.data.models.Chain
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
 */
class TonDeFiBalanceService(
    private val tonStakingApi: TonStakingApi,
    private val liquidStakingService: TonLiquidStakingService,
    private val stakingDetailsRepository: StakingDetailsRepository,
) : DeFiService {

    override suspend fun getRemoteDeFiBalance(address: String, vaultId: String): List<DeFiBalance> {
        return try {
            // Read first and independently of the nominator position: a vault can hold one, the
            // other, or both, and a liquid-only holder must not be answered with an empty list.
            val liquid = fetchLiquidBalance(address)

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
            // cached by BalanceRepository and hide the position until the next invalidation.
            getCacheDeFiBalance(address, vaultId)
        } catch (e: IOException) {
            Timber.w(e, "TonDeFiBalanceService: Network error fetching nominator-pool balance")
            getCacheDeFiBalance(address, vaultId)
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

    override suspend fun getCacheDeFiBalance(address: String, vaultId: String): List<DeFiBalance> {
        val cached = stakingDetailsRepository.getStakingDetailsByCoindId(vaultId, Coins.Ton.TON.id)
        // Only the nominator stake is persisted; the liquid position is a jetton balance, which
        // the wallet's own token cache already answers for offline.
        return tonDeFiBalances(cached?.stakeAmount ?: BigInteger.ZERO, BigInteger.ZERO)
    }

    /** Drops the cached Tonstakers position, so the next read goes to the chain. */
    suspend fun invalidateLiquidPosition(address: String) {
        liquidStakingService.invalidate(address)
    }

    /**
     * The vault's tsTON balance, or zero when it holds none. Best-effort: a lookup failure must not
     * cost the nominator position its row, and an absent liquid position is indistinguishable from
     * a zero one on this row.
     */
    private suspend fun fetchLiquidBalance(address: String): BigInteger =
        try {
            liquidStakingService.getPosition(address).tsTonBalance
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "TonDeFiBalanceService: Failed to fetch the Tonstakers position")
            BigInteger.ZERO
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

    private suspend fun persistStakedBalance(vaultId: String, staked: BigInteger, apr: Double?) {
        try {
            val details =
                StakingDetails(
                    id = Coins.Ton.TON.generateId(),
                    coin = Coins.Ton.TON,
                    stakeAmount = staked,
                    apr = apr,
                    estimatedRewards = null,
                    nextPayoutDate = null,
                    rewards = null,
                    rewardsCoin = null,
                )
            val existing =
                stakingDetailsRepository.getStakingDetailsByCoindId(vaultId, Coins.Ton.TON.id)
            when {
                existing == null -> stakingDetailsRepository.saveStakingDetails(vaultId, details)
                existing.stakeAmount != staked || existing.apr != apr ->
                    stakingDetailsRepository.updateStakingDetails(vaultId, details)
            }
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "TonDeFiBalanceService: Failed to persist staked TON balance")
        }
    }

    private fun TonAccountStakingInfoJson.stakedTotal(): BigInteger =
        BigInteger.valueOf(amount) + BigInteger.valueOf(pendingDeposit)
}
