package com.vultisig.wallet.data.blockchain.ton

import com.vultisig.wallet.data.api.chains.ton.TonAccountStakingInfoJson
import com.vultisig.wallet.data.api.chains.ton.TonStakingApi
import com.vultisig.wallet.data.blockchain.DeFiService
import com.vultisig.wallet.data.blockchain.model.DeFiBalance
import com.vultisig.wallet.data.blockchain.model.StakingDetails
import com.vultisig.wallet.data.blockchain.model.StakingDetails.Companion.generateId
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.repositories.StakingDetailsRepository
import java.io.IOException
import java.math.BigInteger
import timber.log.Timber

/**
 * Reads native TON nominator-pool staking positions for the DeFi/Earn tab.
 *
 * The visible staked balance is the **primary** pool's active stake plus any `pending_deposit` — a
 * freshly placed stake sits in pending until the next validation cycle, so it must still surface
 * immediately. When an account holds positions in several pools, only the largest is treated as the
 * primary (mirrors vultisig-ios `TonStakeInteractor`). The position is decorated with the pool's
 * APY fetched from `/v2/staking/pool/{address}`.
 */
class TonDeFiBalanceService(
    private val tonStakingApi: TonStakingApi,
    private val stakingDetailsRepository: StakingDetailsRepository,
) : DeFiService {

    override suspend fun getRemoteDeFiBalance(address: String, vaultId: String): List<DeFiBalance> {
        return try {
            val primary =
                tonStakingApi.getNominatorPools(address).maxByOrNull { it.stakedTotal() }
                    ?: return persistAndEmpty(vaultId)

            val staked = primary.stakedTotal()
            // APY decoration is best-effort: a missing pool info must not drop the position.
            val apr = tonStakingApi.getStakingPool(primary.pool)?.apy

            persistStakedBalance(vaultId, staked, apr)

            if (staked == BigInteger.ZERO) {
                emptyList()
            } else {
                listOf(tonDeFiBalance(staked))
            }
        } catch (e: IOException) {
            Timber.w(e, "TonDeFiBalanceService: Network error fetching nominator-pool balance")
            emptyList()
        }
    }

    override suspend fun getCacheDeFiBalance(address: String, vaultId: String): List<DeFiBalance> {
        val cached = stakingDetailsRepository.getStakingDetailsByCoindId(vaultId, Coins.Ton.TON.id)
        val staked = cached?.stakeAmount ?: return emptyList()
        if (staked == BigInteger.ZERO) return emptyList()
        return listOf(tonDeFiBalance(staked))
    }

    private fun tonDeFiBalance(staked: BigInteger): DeFiBalance =
        DeFiBalance(
            chain = Chain.Ton,
            balances = listOf(DeFiBalance.Balance(coin = Coins.Ton.TON, amount = staked)),
        )

    private suspend fun persistAndEmpty(vaultId: String): List<DeFiBalance> {
        persistStakedBalance(vaultId, BigInteger.ZERO, apr = null)
        return emptyList()
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
