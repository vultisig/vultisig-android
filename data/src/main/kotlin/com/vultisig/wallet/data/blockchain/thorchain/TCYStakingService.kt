package com.vultisig.wallet.data.blockchain.thorchain

import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.blockchain.model.StakingDetails
import com.vultisig.wallet.data.blockchain.model.StakingDetails.Companion.generateId
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.settings.AppCurrency
import com.vultisig.wallet.data.repositories.StakingDetailsRepository
import com.vultisig.wallet.data.repositories.TokenPriceRepository
import com.vultisig.wallet.data.utils.SimpleCache
import com.vultisig.wallet.data.utils.toValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.supervisorScope
import timber.log.Timber
import wallet.core.jni.CoinType
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.util.Date
import javax.inject.Inject
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow

class TCYStakingService @Inject constructor(
    private val thorChainApi: ThorChainApi,
    private val tokenPriceRepository: TokenPriceRepository,
    private val stakingDetailsRepository: StakingDetailsRepository,
) {
    companion object {
        private const val TCY_DECIMALS = 8
        private const val BLOCKS_PER_DAY = 14_400L
        private const val SECONDS_PER_BLOCK = 6.0
        private const val DAYS_IN_YEAR = 365

        // Cache duration for constants (1 hour)
        private const val CONSTANTS_CACHE_DURATION_MS = 3600_000L
        private const val CONSTANT_CACHE_KEY = "constants-key"
    }

    private data class TcyConstants(
        val minRuneForDistribution: BigDecimal,
        val minTcyForDistribution: BigDecimal,
        val systemIncomeBps: Long,
    )

    private val constantsCache = SimpleCache<String, TcyConstants>(
        defaultExpirationMs = CONSTANTS_CACHE_DURATION_MS
    )

    fun getStakingDetails(address: String, vaultId: String): Flow<StakingDetails?> = flow {
        val cachedDetails =
            stakingDetailsRepository.getStakingDetails(vaultId, Coins.ThorChain.TCY.id)
        if (cachedDetails != null) {
            Timber.d("TCYStakingService: Emitting cached TCY staking position for vault $vaultId")
            emit(cachedDetails)
        }

        // Fetch fresh data from network
        val freshDetails = try {
            getStakingDetailsFromNetwork(address)
        } catch (e: Exception) {
            Timber.e(e, "TCYStakingService: Error fetching TCY staking details for vault $vaultId")

            if (cachedDetails != null) {
                Timber.d("TCYStakingService: Using cached TCY position due to error")
                emit(cachedDetails)
                return@flow
            }

            throw e
        }

        // Emit new fresh positions
        Timber.d("RujiStakingService: Emitting fresh TCY staking position for vault $vaultId")
        emit(freshDetails)

        // Update DB cache
        Timber.d("RujiStakingService: Saving fresh TCY position for vault $vaultId")
        stakingDetailsRepository.saveStakingDetails(vaultId, freshDetails)
    }.flowOn(Dispatchers.IO)

    suspend fun getStakingDetailsFromNetwork(
        address: String,
    ): StakingDetails = supervisorScope {
        try {
            // 1. Fetch staked amount
            val stakedResponse = thorChainApi.fetchTcyStakedAmount(address)
            val stakedAmount = stakedResponse.amount?.toBigIntegerOrNull() ?: BigInteger.ZERO
            val stakeDecimal = CoinType.THORCHAIN.toValue(stakedAmount)

            val rewardsCoin = Coin(
                chain = Chain.ThorChain,
                ticker = "RUNE",
                logo = "rune",
                address = "",
                decimal = 8,
                hexPublicKey = "",
                priceProviderID = "thorchain",
                contractAddress = "",
                isNativeToken = true
            )

            if (stakedAmount == BigInteger.ZERO) {
                return@supervisorScope StakingDetails(
                    id = Coins.ThorChain.TCY.generateId(),
                    coin = Coins.ThorChain.TCY,
                    stakeAmount = stakedAmount,
                    apr = null,
                    estimatedRewards = null,
                    nextPayoutDate = null,
                    rewards = null,
                    rewardsCoin = rewardsCoin
                )
            }

            // 2. Calculate APR and other metrics in parallel
            val apyDeferred = async { calculateTcyAPY(address, stakeDecimal) }
            val nextPayoutDeferred = async { calculateNextPayout() }
            val estimatedRewardDeferred = async { calculateEstimatedReward(stakeDecimal) }

            StakingDetails(
                id = Coins.ThorChain.TCY.generateId(),
                coin = Coins.ThorChain.TCY,
                stakeAmount = stakedAmount,
                apr = convertAPYtoAPR(apyDeferred.await()),
                estimatedRewards = estimatedRewardDeferred.await(),
                nextPayoutDate = nextPayoutDeferred.await(),
                rewards = null, // TCY auto-distributes, no pending rewards
                rewardsCoin = rewardsCoin
            )
        } catch (e: Exception) {
            Timber.e(e, "TCYStakingService: Failed to fetch TCY staking details from network")
            throw e
        }
    }

    private suspend fun calculateTcyAPY(
        address: String,
        stakedAmount: BigDecimal
    ): Double {
        return try {
            // Get prices
            val currency = AppCurrency.USD
            val tcyCoin = Coins.ThorChain.TCY
            val runeCoin = Coins.ThorChain.RUNE
            val tcyPrice = tokenPriceRepository.getCachedPrice(tcyCoin.id, currency)
                ?: BigDecimal.ZERO
            val runePrice = tokenPriceRepository.getCachedPrice(runeCoin.id, currency)
                ?: BigDecimal.ZERO

            if (tcyPrice <= BigDecimal.ZERO ||
                runePrice <= BigDecimal.ZERO ||
                stakedAmount == BigDecimal.ZERO
            ) {
                return 0.0
            }

            // Get user distributions
            val distributionData = thorChainApi.fetchTcyUserDistributions(address)
            val distributions = distributionData.distributions

            if (distributions.isEmpty()) {
                return 0.0
            }

            // Calculate total RUNE received
            val totalRuneSatoshis = distributions.sumOf { dist ->
                dist.amount.toBigIntegerOrNull() ?: BigInteger.ZERO
            }
            val totalRune = CoinType.THORCHAIN.toValue(totalRuneSatoshis)

            // Calculate average daily RUNE
            val days = distributions.size.toBigDecimal()
            val avgDailyRune = totalRune.divide(days, 8, RoundingMode.HALF_UP)

            // Annualize
            val annualRune = avgDailyRune.multiply(BigDecimal(DAYS_IN_YEAR))
            val annualUSD = annualRune.multiply(runePrice)

            // Calculate staked value in Currency
            val stakedValueUSD = stakedAmount.multiply(tcyPrice)

            // Calculate APY
            return if (stakedValueUSD > BigDecimal.ZERO) {
                (annualUSD.divide(stakedValueUSD, 4, RoundingMode.HALF_UP).toDouble() * 100)
            } else {
                0.0
            }
        } catch (t: Throwable) {
            Timber.e(t)
            0.0
        }
    }

    private fun convertAPYtoAPR(apy: Double): Double {
        if (apy <= 0) return 0.0

        // Convert percentage to decimal
        val apyDecimal = apy / 100.0

        // Calculate daily rate from APY
        // APY = (1 + daily_rate)^365 - 1
        // daily_rate = (1 + APY)^(1/365) - 1
        val dailyRate = (1 + apyDecimal).pow(1.0 / DAYS_IN_YEAR) - 1

        // APR = daily_rate * 365
        return dailyRate * DAYS_IN_YEAR
    }

    private suspend fun calculateNextPayout(): Date {
        val currentBlock = thorChainApi.getLastBlock()

        // Calculate next distribution block
        val nextDistributionBlock = ((currentBlock / BLOCKS_PER_DAY) + 1) * BLOCKS_PER_DAY

        // Calculate blocks remaining
        val blocksRemaining = nextDistributionBlock - currentBlock

        // Calculate seconds remaining
        val secondsRemaining = (blocksRemaining * SECONDS_PER_BLOCK).toLong()

        return Date(System.currentTimeMillis() + (secondsRemaining * 1000))
    }

    /// Calculate estimated TCY reward based on current module balance and accrual rate
    /// Logic mirrors: https://github.com/familiarcow/RUNE-Tools TCY.svelte calculateNextDistribution
    private suspend fun calculateEstimatedReward(stakedAmount: BigDecimal): BigDecimal? =
        supervisorScope {
            try {
                val currentBlock = thorChainApi.getLastBlock()

                val nextBlock =
                    (ceil(currentBlock.toDouble() / BLOCKS_PER_DAY) * BLOCKS_PER_DAY).toLong()
                val blocksRemaining = nextBlock - currentBlock

                // Get current accrued RUNE in tcy_stake module
                val moduleBalance = thorChainApi.fetchTcyModuleBalance()
                val runeCoin = moduleBalance.coins.firstOrNull { it.denom == "rune" }
                if (runeCoin == null) {
                    return@supervisorScope BigDecimal.ZERO
                }

                // Format to units
                val currentAccruedRune =
                    CoinType.THORCHAIN.toValue(
                        runeCoin.amount.toBigIntegerOrNull() ?: BigInteger.ZERO
                    )
                if (currentAccruedRune == BigDecimal.ZERO) {
                    return@supervisorScope BigDecimal.ZERO
                }

                // Calculate blocks since last distribution
                val lastDistributionBlock = (currentBlock / BLOCKS_PER_DAY) * BLOCKS_PER_DAY
                val blocksSinceLastDistribution = currentBlock - lastDistributionBlock

                if (blocksSinceLastDistribution <= 0) {
                    return@supervisorScope calculateUserShare(stakedAmount, currentAccruedRune)
                }

                // Calculate RUNE per block rate
                val runePerBlock =
                    currentAccruedRune.divide(
                        blocksSinceLastDistribution.toBigDecimal(),
                        8,
                        RoundingMode.HALF_UP
                    )

                // Calculate total estimated RUNE by next distribution
                val additionalRune = runePerBlock.multiply(BigDecimal(blocksRemaining))
                val totalEstimatedRune = currentAccruedRune.add(additionalRune)

                // Calculate user's share
                calculateUserShare(stakedAmount, totalEstimatedRune)
            } catch (t: Throwable) {
                Timber.e(t)
                return@supervisorScope null
            }
        }

    private suspend fun calculateUserShare(
        stakedAmount: BigDecimal,
        totalEstimatedRune: BigDecimal
    ): BigDecimal {
        val constants = fetchThorchainConstants()

        val rawMultiplier = totalEstimatedRune.divide(
            constants.minRuneForDistribution,
            2,
            RoundingMode.DOWN
        )

        // Calculate distribution amount
        val distributionMultiplier = BigDecimal(floor(rawMultiplier.toDouble()))
        val actualDistributionAmount =
            distributionMultiplier.multiply(constants.minRuneForDistribution)
        if (actualDistributionAmount <= BigDecimal.ZERO) {
            return BigDecimal.ZERO
        }

        // Get total staked TCY
        val totalStakedTcy = fetchTotalStakedTcy()
        if (totalStakedTcy == BigDecimal.ZERO) {
            return BigDecimal.ZERO
        }

        val userShare = stakedAmount.divide(totalStakedTcy, 8, RoundingMode.HALF_UP)

        return actualDistributionAmount.multiply(userShare)
    }

    private suspend fun fetchThorchainConstants(): TcyConstants {
        val data = thorChainApi.getConstants()

        val cacheConstats = constantsCache.get(CONSTANT_CACHE_KEY)

        if (cacheConstats != null) {
            return cacheConstats
        }

        val minRune =
            data.int64Values.minRuneForTCYStakeDistribution?.toBigDecimal() ?: BigDecimal.ZERO
        val minRuneDecimal =
            minRune.divide(10.0.toBigDecimal().pow(8))

        val minTcy =
            data.int64Values.minTcyForTCYStakeDistribution?.toBigDecimal() ?: BigDecimal.ZERO
        val minTcyDecimal = minTcy.divide(10.0.toBigDecimal().pow(8))

        val bps = data.int64Values.tcyStakeSystemIncomeBps ?: 0L

        val constants = TcyConstants(
            minRuneForDistribution = minRuneDecimal,
            minTcyForDistribution = minTcyDecimal,
            systemIncomeBps = bps
        )

        constantsCache.put(CONSTANT_CACHE_KEY, constants)

        return constants
    }

    private suspend fun fetchTotalStakedTcy(): BigDecimal {
        val response = thorChainApi.fetchTcyStakers()

        // Sum all staked amounts (they are in satoshis as strings)
        val totalSatoshis = response.tcyStakers.fold(BigDecimal.ZERO) { sum, staker ->
            val amount = try {
                BigDecimal(staker.amount)
            } catch (e: Exception) {
                Timber.e(e)
                BigDecimal.ZERO
            }
            sum + amount
        }

        return totalSatoshis.divide(
            BigDecimal.TEN.pow(TCY_DECIMALS),
            TCY_DECIMALS,
            RoundingMode.HALF_UP
        )
    }
}