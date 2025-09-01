package com.vultisig.wallet.data.blockchain.ethereum

import com.vultisig.wallet.data.api.EvmApi
import com.vultisig.wallet.data.blockchain.Eip1559
import com.vultisig.wallet.data.blockchain.Fee
import com.vultisig.wallet.data.blockchain.FeeService
import com.vultisig.wallet.data.blockchain.GasFees
import com.vultisig.wallet.data.blockchain.SmartContract
import com.vultisig.wallet.data.blockchain.Swap
import com.vultisig.wallet.data.blockchain.Transaction
import com.vultisig.wallet.data.blockchain.Transfer
import com.vultisig.wallet.data.blockchain.isSwap
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.isLayer2
import com.vultisig.wallet.data.models.supportsLegacyGas
import com.vultisig.wallet.data.utils.increaseByPercent
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.math.BigInteger

class EthereumFeeService(
    private val evmApi: EvmApi,
): FeeService {
    override suspend fun calculateFees(transaction: Transaction, limit: BigInteger): Fee {
        require(limit > BigInteger.ZERO) { "Limit should not be 0" }
        val chain = transaction.coin.chain

        val fees = if (chain.supportsLegacyGas) {
            calculateLegacyGasFees(limit)
        } else {
            calculateEip1559Fees(limit, chain, transaction.isSwap())
        }

        val l1Fees = if (chain.isLayer2) {
            calculateLayer1Fees(transaction)
        } else {
            BigInteger.ZERO
        }

        return fees.addL1Amount()
    }

    private fun Fee.addL1Amount(): Fee {
        val l1Amount = BigInteger.ZERO
        return if (this is GasFees) {
            this.copy(amount = this.amount + l1Amount)
        } else if (this is Eip1559) {
            this.copy(amount = this.amount + l1Amount)
        } else {
            error("Fee Type Not Supported")
        }
    }

    private fun calculateLayer1Fees(transaction: Transaction): BigInteger {
        return BigInteger.ZERO
    }

    private suspend fun calculateLegacyGasFees(limit: BigInteger): GasFees {
        val gasPrice = evmApi.getGasPrice()

        return GasFees(
            price = gasPrice,
            limit = limit,
            amount = gasPrice * limit,
        )
    }

    private suspend fun calculateEip1559Fees(limit: BigInteger, chain: Chain, isSwap: Boolean): Eip1559 = coroutineScope {
        val baseNetworkPriceDeferred = async { evmApi.getBaseFee() }
        val feeHistoryDeferred = async { evmApi.getFeeHistory() }

        val rewardsFeeHistory = feeHistoryDeferred.await()

        val maxPriorityFeePerGas = calculateMaxPriorityFeePerGas(rewardsFeeHistory, chain)
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
            baseNetworkPrice.increaseByPercent(10)
        }
        return baseNetworkPrice
    }

    private fun calculateMaxPriorityFeePerGas(
        rewardsFeeHistory: List<BigInteger>,
        chain: Chain
    ): BigInteger {
        return when (chain) {
            Chain.Arbitrum ->
                BigInteger.ZERO // arbitrum has no fee priority
            Chain.Base,
            Chain.Blast,
            Chain.Optimism,
                -> rewardsFeeHistory.maxOrNull() ?: DEFAULT_MAX_PRIORITY_FEE_PER_GAS_L2
            Chain.Polygon ->
                // polygon has min of 30 gwei, but some blocks comes with less rewards
                maxOf(rewardsFeeHistory[rewardsFeeHistory.size / 2], GWEI * "30".toBigInteger())
            else -> maxOf(a = rewardsFeeHistory[rewardsFeeHistory.size / 2], b = GWEI)
        }
    }

    private fun Transaction.getAmount(): BigInteger {
        return when (this) {
            is Transfer -> {
                when (coin.isNativeToken) {
                    true -> amount
                    false -> BigInteger.ZERO // amounts is encoded in call data for tokens
                }
            }
            is Swap, is SmartContract -> amount
        }
    }

    private companion object {
        private val COIN_TRANSFER_LIMIT = "21000".toBigInteger()

        private val DEFAULT_MAX_PRIORITY_FEE_PER_GAS_L2 = "20".toBigInteger()
        private val GWEI = BigInteger.TEN.pow(9)
    }
}


/*
    Avalanche("Avalanche", EVM, "Gwei"),
    CronosChain("CronosChain", EVM, "Gwei"),
    ZkSync("Zksync", EVM, "Gwei"), // si eip1559
    Mantle("Mantle", EVM, "Gwei"), // no eip1559 custom fees
 */