package com.vultisig.wallet.data.blockchain.ethereum

import com.vultisig.wallet.data.api.EvmApi
import com.vultisig.wallet.data.api.EvmApiFactory
import com.vultisig.wallet.data.blockchain.model.BlockchainTransaction
import com.vultisig.wallet.data.blockchain.model.Eip1559
import com.vultisig.wallet.data.blockchain.model.Fee
import com.vultisig.wallet.data.blockchain.FeeService
import com.vultisig.wallet.data.blockchain.model.GasFees
import com.vultisig.wallet.data.blockchain.model.Transfer
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

    @Deprecated("Use calculateFees(transaction: BlockchainTransaction): Fee")
    override suspend fun calculateFees(chain: Chain, limit: BigInteger, isSwap: Boolean, to: String?): Fee {
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

    override suspend fun calculateFees(transaction: BlockchainTransaction): Fee {
        require(transaction is Transfer) {
            "Invalid Transaction Type ${transaction::class.simpleName}"
        }
        val chain = transaction.coin.chain
        val evmApi = evmApiFactory.createEvmApi(chain)
        val limit = calculateLimit(transaction, evmApi)

        val fees = if (chain.supportsLegacyGas) {
            calculateLegacyGasFees(limit, evmApi)
        } else {
            calculateEip1559Fees(limit, chain, false, evmApi)
        }

        val l1Fees = if (chain.isLayer2) {
            calculateLayer1Fees()
        } else {
            BigInteger.ZERO
        }

        return fees.addL1Amount(l1Fees)
    }

    private suspend fun calculateLimit(transaction: Transfer, evmApi: EvmApi): BigInteger {
        val isCoinTransfer = transaction.coin.isNativeToken
        val token = transaction.coin
        val toAddress = transaction.to
        val amount = transaction.amount
        val memo = transaction.memo

        val calculatedLimit = if (isCoinTransfer) {
            evmApi.estimateGasForEthTransaction(
                senderAddress = token.address,
                recipientAddress = toAddress,
                value = amount,
                memo = memo,
            )
        } else {
            evmApi.estimateGasForERC20Transfer(
                senderAddress = token.address,
                recipientAddress = toAddress,
                contractAddress = token.contractAddress,
                value = amount,
            ).increaseByPercent(50)
        }

        return maxOf(calculatedLimit, getDefaultLimit(transaction))
    }

    private fun calculateLayer1Fees(): BigInteger {
        return BigInteger.ZERO
    }

    override suspend fun calculateDefaultFees(transaction: BlockchainTransaction): Fee {
        val chain = transaction.coin.chain
        val defaultLimit = getDefaultLimit(transaction)
        val isSwap = transaction is Transfer

        return calculateFees(chain,  defaultLimit, isSwap)
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
                // Arb and Mantle requires no miner tip
                Chain.Arbitrum, Chain.Mantle -> BigInteger.ZERO

                // picked max from 10 previous blocks, then ensure inclusion. For l2 is quite low
                Chain.Base,
                Chain.Blast,
                Chain.Optimism, -> rewardsFeeHistory.maxOrNull() ?: DEFAULT_MAX_PRIORITY_FEE_PER_GAS_L2

                // polygon has min of 30 gwei, but some blocks comes with less rewards
                Chain.Polygon -> maxOf(rewardsFeeHistory[rewardsFeeHistory.size / 2], GWEI * DEFAULT_MAX_PRIORITY_FEE_POLYGON)

                // picked medium with min of 1 GWEI (ETH etc..)
                else -> maxOf(a = rewardsFeeHistory[rewardsFeeHistory.size / 2], b = GWEI)
            }
        }
    }

    private fun Fee.addL1Amount(l1FeesAmount: BigInteger): Fee {
        return if (this is GasFees) {
            this.copy(amount = this.amount + l1FeesAmount)
        } else if (this is Eip1559) {
            this.copy(amount = this.amount + l1FeesAmount)
        } else {
            error("Fee Type Not Supported")
        }
    }

    private fun getDefaultLimit(transaction: BlockchainTransaction): BigInteger {
        require(transaction is Transfer) {
            "Transaction type not supported: ${transaction::class.simpleName}"
        }
        val isCoinTransfer = transaction.coin.isNativeToken
        val chain = transaction.coin.chain

        return when {
            chain == Chain.Arbitrum -> DEFAULT_ARBITRUM_TRANSFER
            isCoinTransfer -> DEFAULT_COIN_TRANSFER_LIMIT
            else -> DEFAULT_TOKEN_TRANSFER_LIMIT.increaseByPercent(40)
        }
    }

    companion object {
        private val GWEI = BigInteger.TEN.pow(9)

        private val DEFAULT_MAX_PRIORITY_FEE_PER_GAS_L2 = "20".toBigInteger()
        private val DEFAULT_MAX_PRIORITY_FEE_POLYGON = "30".toBigInteger()

        val DEFAULT_SWAP_LIMIT = "600000".toBigInteger()
        val DEFAULT_COIN_TRANSFER_LIMIT = "23000".toBigInteger()
        val DEFAULT_TOKEN_TRANSFER_LIMIT = "150000".toBigInteger()

        val DEFAULT_ARBITRUM_TRANSFER = "160000".toBigInteger()
        val DEFAULT_MANTLE_SWAP_LIMIT = "3000000000".toBigInteger()
    }
}