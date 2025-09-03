package com.vultisig.wallet.data.blockchain.ethereum

import com.vultisig.wallet.data.api.EvmApi
import com.vultisig.wallet.data.api.EvmApiFactory
import com.vultisig.wallet.data.blockchain.Eip1559
import com.vultisig.wallet.data.blockchain.Fee
import com.vultisig.wallet.data.blockchain.FeeService
import com.vultisig.wallet.data.blockchain.GasFees
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.isLayer2
import com.vultisig.wallet.data.models.supportsLegacyGas
import com.vultisig.wallet.data.utils.increaseByPercent
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.math.BigInteger
import javax.inject.Inject

class EthereumFeeService @Inject constructor(
    private val evmApiFactory: EvmApiFactory,
) : FeeService {
    override suspend fun calculateFees(chain: Chain, limit: BigInteger, isSwap: Boolean): Fee {
        require(limit > BigInteger.ZERO) { "Limit should not be 0" }
        val evmApi = evmApiFactory.createEvmApi(chain)

        val fees = if (chain.supportsLegacyGas) {
            calculateLegacyGasFees(limit, evmApi)
        } else {
            calculateEip1559Fees(limit, chain, isSwap, evmApi)
        }

        val l1Fees = if (chain.isLayer2) {
            calculateLayer1Fees()
        } else {
            BigInteger.ZERO
        }

        return fees.addL1Amount(l1Fees)
    }

    private fun calculateLayer1Fees(): BigInteger {
        return BigInteger.ZERO
    }

    private suspend fun calculateLegacyGasFees(limit: BigInteger, evmApi: EvmApi): GasFees {
        val gasPrice = evmApi.getGasPrice()

        return GasFees(
            price = gasPrice,
            limit = limit,
            amount = gasPrice * limit,
        )
    }

    private suspend fun calculateEip1559Fees(
        limit: BigInteger,
        chain: Chain,
        isSwap: Boolean,
        evmApi: EvmApi,
    ): Eip1559 = coroutineScope {
        val baseNetworkPriceDeferred = async { evmApi.getBaseFee() }
        val feeHistoryDeferred = async { evmApi.getFeeHistory() }

        val rewardsFeeHistory = feeHistoryDeferred.await()

        val maxPriorityFeePerGas = calculateMaxPriorityFeePerGas(rewardsFeeHistory, chain, evmApi)
        val baseNetworkPrice = calculateBaseNetworkPrice(baseNetworkPriceDeferred.await(), isSwap)
        val maxFeePerGas = calculateMaxFeePerGas(baseNetworkPrice, maxPriorityFeePerGas)

        Eip1559(
            limit = limit,
            networkPrice = baseNetworkPrice,
            maxFeePerGas = maxFeePerGas,
            maxPriorityFeePerGas = maxPriorityFeePerGas,
            amount = maxFeePerGas * limit,
        )
    }

    private fun calculateMaxFeePerGas(
        baseNetworkPrice: BigInteger,
        maxPriorityFeePerGas: BigInteger,
    ): BigInteger {
        return baseNetworkPrice.increaseByPercent(20).add(maxPriorityFeePerGas)
    }

    private fun calculateBaseNetworkPrice(baseNetworkPrice: BigInteger, swap: Boolean): BigInteger {
        if (swap) {
            return baseNetworkPrice.increaseByPercent(10)
        }
        return baseNetworkPrice
    }

    private suspend fun calculateMaxPriorityFeePerGas(
        rewardsFeeHistory: List<BigInteger>,
        chain: Chain,
        evmApi: EvmApi,
    ): BigInteger {
        return if (chain.id == Chain.Avalanche.id) {
            evmApi.getMaxPriorityFeePerGas() // exception for avalanche
        } else {
            when (chain) {
                Chain.Arbitrum ->
                    BigInteger.ZERO // arbitrum has no fee priority
                Chain.Base,
                Chain.Blast,
                Chain.Optimism,
                    -> rewardsFeeHistory.maxOrNull() ?: DEFAULT_MAX_PRIORITY_FEE_PER_GAS_L2

                Chain.Polygon ->
                    // polygon has min of 30 gwei, but some blocks comes with less rewards
                    maxOf(rewardsFeeHistory[rewardsFeeHistory.size / 2], GWEI * POLYGON_DEFAULT)

                else -> maxOf(a = rewardsFeeHistory[rewardsFeeHistory.size / 2], b = GWEI)
            }
        }
    }

    // TODO: Show properly fee amount on the UI (upcoming PR)
    private fun Fee.addL1Amount(l1FeesAmount: BigInteger): Fee {
        return if (this is GasFees) {
            this.copy(amount = this.amount + l1FeesAmount)
        } else if (this is Eip1559) {
            this.copy(amount = this.amount + l1FeesAmount)
        } else {
            error("Fee Type Not Supported")
        }
    }

    companion object {
        private val DEFAULT_MAX_PRIORITY_FEE_PER_GAS_L2 = "20".toBigInteger()
        private val GWEI = BigInteger.TEN.pow(9)
        private val POLYGON_DEFAULT = "30".toBigInteger()

        val DEFAULT_SWAP_LIMIT = "600000"
        val DEFAULT_COIN_TRANSFER = "23000"
        val DEFAULT_TOKEN_TRANSFER = "120000"
        val DEFAULT_ARBITRUM_TRANSFER = "160000"
    }
}