package com.vultisig.wallet.data.blockchain.thorchain

import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.blockchain.model.StakingDetails
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.repositories.TokenPriceRepository
import com.vultisig.wallet.data.utils.SimpleCache
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
) {
    companion object {
        private const val TCY_DECIMALS = 8
        private const val RUNE_DECIMALS = 8
        private const val BLOCKS_PER_DAY = 14_400L
        private const val SECONDS_PER_BLOCK = 6.0
        private const val DAYS_IN_YEAR = 365
        
        // Cache duration for constants (1 hour)
        private const val CONSTANTS_CACHE_DURATION_MS = 3600_000L
    }
    
    private data class TcyConstants(
        val minRuneForDistribution: BigDecimal,
        val minTcyForDistribution: BigDecimal,
        val systemIncomeBps: Int
    )
    
    private var cachedConstants: SimpleCache<String, TcyConstants> = SimpleCache()
    private var constantsCacheTimestamp: Long = 0
    
    suspend fun getStakingDetails(
        address: String,
        tcyCoin: Coin,
        runeCoin: Coin
    ): StakingDetails = coroutineScope {
        // 1. Fetch staked amount
        val stakedResponse = thorChainApi.fetchTcyStakedAmount(address)
        val stakedAmount = stakedResponse.amount?.toBigIntegerOrNull() ?: BigInteger.ZERO
        error("")

        /*if (stakedAmount == BigInteger.ZERO) {
            return@coroutineScope StakingDetails(
                stakeAmount = BigInteger.ZERO,
                apr = 0.0,
                estimatedRewards = null,
                nextPayoutDate = null,
                rewards = null,
                rewardsCoin = null
            )
        } */
        
        // 2. Fetch data in parallel
       // val apyDeferred = async { calculateTcyAPY(tcyCoin, runeCoin, address, stakedAmount) }
       // val nextPayoutDeferred = async { calculateNextPayout() }
       // val estimatedRewardDeferred = async { calculateEstimatedReward(stakedAmount) }
        
        /*val apy = apyDeferred.await()
        val apr = convertAPYtoAPR(apy)
        val nextPayout = nextPayoutDeferred.await()
        val estimatedReward = estimatedRewardDeferred.await()
        
        // 3. Create RUNE coin for rewards
        val rewardsCoin = Coin(
            chain = Chain.ThorChain,
            ticker = "RUNE",
            logo = "rune",
            address = "",
            decimal = RUNE_DECIMALS,
            hexPublicKey = "",
            priceProviderID = "thorchain",
            contractAddress = "",
            isNativeToken = true
        )
        
        StakingDetails(
            stakeAmount = stakedAmount,
            apr = apr,
            estimatedRewards = estimatedReward,
            nextPayoutDate = nextPayout,
            rewards = null, // TCY auto-distributes, no pending rewards
            rewardsCoin = rewardsCoin
        ) */
    }
    
    private fun amountToDecimal(amount: BigInteger, decimals: Int): BigDecimal {
        return amount.toBigDecimal().divide(
            BigDecimal.TEN.pow(decimals),
            decimals,
            RoundingMode.HALF_UP
        )
    }
    
    /*private suspend fun calculateTcyAPY(
        tcyCoin: Coin,
        runeCoin: Coin,
        address: String,
        stakedAmount: BigInteger
    ): Double {
        // Get prices
        val tcyPrice = tokenPriceRepository.getTokenPrice(tcyCoin.priceProviderID)?.price ?: 0.0
        val runePrice = tokenPriceRepository.getTokenPrice(runeCoin.priceProviderID)?.price ?: 0.0
        
        if (tcyPrice <= 0 || runePrice <= 0 || stakedAmount == BigInteger.ZERO) {
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
            try {
                BigInteger(dist.amount)
            } catch (e: Exception) {
                BigInteger.ZERO
            }
        }
        
        val totalRune = amountToDecimal(totalRuneSatoshis, RUNE_DECIMALS)
        
        // Calculate average daily RUNE
        val days = distributions.size
        val avgDailyRune = totalRune.divide(BigDecimal(days), 8, RoundingMode.HALF_UP)
        
        // Annualize
        val annualRune = avgDailyRune.multiply(BigDecimal(DAYS_IN_YEAR))
        val annualUSD = annualRune.multiply(BigDecimal(runePrice))
        
        // Calculate staked value in USD
        val stakedDecimal = amountToDecimal(stakedAmount, TCY_DECIMALS)
        val stakedValueUSD = stakedDecimal.multiply(BigDecimal(tcyPrice))
        
        // Calculate APY
        return if (stakedValueUSD > BigDecimal.ZERO) {
            (annualUSD.divide(stakedValueUSD, 4, RoundingMode.HALF_UP).toDouble() * 100)
        } else {
            0.0
        }
    } */
    
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
        // Get current block height
        val currentBlock = thorChainApi.getLastBlock()
        
        // Calculate next distribution block
        val nextDistributionBlock = ((currentBlock / BLOCKS_PER_DAY) + 1) * BLOCKS_PER_DAY
        
        // Calculate blocks remaining
        val blocksRemaining = nextDistributionBlock - currentBlock
        
        // Calculate seconds remaining
        val secondsRemaining = (blocksRemaining * SECONDS_PER_BLOCK).toLong()
        
        // Return date
        return Date(System.currentTimeMillis() + (secondsRemaining * 1000))
    }
    
    /*private suspend fun calculateEstimatedReward(stakedAmount: BigInteger): BigDecimal = coroutineScope {
        // Get current block height
        val currentBlock = thorChainApi.getLastBlock()
        
        // Calculate next distribution block
        val nextBlock = (ceil(currentBlock.toDouble() / BLOCKS_PER_DAY) * BLOCKS_PER_DAY).toLong()
        val blocksRemaining = nextBlock - currentBlock
        
        // Get current accrued RUNE in tcy_stake module
        val moduleBalance = fetchTcyModuleBalance()
        val currentAccruedRune = amountToDecimal(moduleBalance, RUNE_DECIMALS)
        
        if (currentAccruedRune == BigDecimal.ZERO) {
            return@coroutineScope BigDecimal.ZERO
        }
        
        // Calculate blocks since last distribution
        val lastDistributionBlock = (currentBlock / BLOCKS_PER_DAY) * BLOCKS_PER_DAY
        val blocksSinceLastDistribution = currentBlock - lastDistributionBlock
        
        if (blocksSinceLastDistribution <= 0) {
            // Just after distribution, use current accrued amount
            return@coroutineScope calculateUserShare(stakedAmount, currentAccruedRune)
        }
        
        // Calculate RUNE per block rate
        val runePerBlock = currentAccruedRune.divide(
            BigDecimal(blocksSinceLastDistribution),
            8,
            RoundingMode.HALF_UP
        )
        
        // Calculate total estimated RUNE by next distribution
        val additionalRune = runePerBlock.multiply(BigDecimal(blocksRemaining))
        val totalEstimatedRune = currentAccruedRune.add(additionalRune)
        
        // Calculate user's share
        calculateUserShare(stakedAmount, totalEstimatedRune)
    } */
    
    /*private suspend fun calculateUserShare(
        stakedAmount: BigInteger,
        totalEstimatedRune: BigDecimal
    ): BigDecimal {
        // Get constants
        val constants = fetchThorchainConstants()
        
        // Calculate actual distribution amount based on MinRuneForTCYStakeDistribution
        val rawMultiplier = totalEstimatedRune.divide(
            constants.minRuneForDistribution,
            2,
            RoundingMode.DOWN
        )
        val distributionMultiplier = BigDecimal(floor(rawMultiplier.toDouble()))
        val actualDistributionAmount = distributionMultiplier.multiply(constants.minRuneForDistribution)
        
        if (actualDistributionAmount <= BigDecimal.ZERO) {
            return BigDecimal.ZERO
        }
        
        // Get total staked TCY
        val totalStakedTcy = fetchTotalStakedTcy()
        
        if (totalStakedTcy == BigDecimal.ZERO) {
            return BigDecimal.ZERO
        }
        
        // Calculate user's share
        val stakedDecimal = amountToDecimal(stakedAmount, TCY_DECIMALS)
        val userShare = stakedDecimal.divide(totalStakedTcy, 8, RoundingMode.HALF_UP)
        
        // Calculate user's estimated reward
        return actualDistributionAmount.multiply(userShare)
    } */
    
    private suspend fun fetchTcyModuleBalance(): BigInteger {
        // TODO: Use endpoint
        return BigInteger.ZERO
    }
    
    private suspend fun fetchTotalStakedTcy(): BigDecimal {
        // TODO: Use endpoint
        return BigDecimal.ONE
    }
}