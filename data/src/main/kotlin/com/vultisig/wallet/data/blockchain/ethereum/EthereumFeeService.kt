package com.vultisig.wallet.data.blockchain.ethereum

import com.vultisig.wallet.data.api.EvmApi
import com.vultisig.wallet.data.api.EvmApiFactory
import com.vultisig.wallet.data.blockchain.FeeService
import com.vultisig.wallet.data.blockchain.model.BlockchainTransaction
import com.vultisig.wallet.data.blockchain.model.Eip1559
import com.vultisig.wallet.data.blockchain.model.Fee
import com.vultisig.wallet.data.blockchain.model.GasFees
import com.vultisig.wallet.data.blockchain.model.Swap
import com.vultisig.wallet.data.blockchain.model.Transfer
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.isLayer2
import com.vultisig.wallet.data.models.supportsLegacyGas
import com.vultisig.wallet.data.utils.increaseByPercent
import java.math.BigInteger
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import timber.log.Timber

class EthereumFeeService @Inject constructor(private val evmApiFactory: EvmApiFactory) :
    FeeService {

    override suspend fun calculateFees(transaction: BlockchainTransaction): Fee {
        if (transaction is Swap) {
            return calculateDefaultFees(transaction)
        }
        require(transaction is Transfer) {
            "Invalid Transaction Type ${transaction::class.simpleName}"
        }
        val chain = transaction.coin.chain
        val evmApi = evmApiFactory.createEvmApi(chain)
        val limit = calculateLimit(transaction, evmApi)

        val fees =
            if (chain.supportsLegacyGas) {
                calculateLegacyGasFees(limit, evmApi)
            } else {
                calculateEip1559Fees(limit, chain, false, evmApi)
            }

        val l1Fees =
            if (chain.isLayer2) {
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

        val calculatedLimit =
            if (isCoinTransfer) {
                evmApi.estimateGasForEthTransaction(
                    senderAddress = token.address,
                    recipientAddress = toAddress,
                    value = amount,
                    memo = memo,
                )
            } else {
                evmApi
                    .estimateGasForERC20Transfer(
                        senderAddress = token.address,
                        recipientAddress = toAddress,
                        contractAddress = token.contractAddress,
                        value = amount,
                    )
                    .increaseByPercent(50)
            }

        return maxOf(calculatedLimit, getDefaultLimit(transaction))
    }

    private fun calculateLayer1Fees(): BigInteger {
        return BigInteger.ZERO
    }

    override suspend fun calculateDefaultFees(transaction: BlockchainTransaction): Fee {
        val chain = transaction.coin.chain
        val defaultLimit = getDefaultLimit(transaction)
        val isSwap = transaction is Swap
        val evmApi = evmApiFactory.createEvmApi(chain)

        val fees =
            if (chain.supportsLegacyGas) {
                calculateLegacyGasFees(defaultLimit, evmApi)
            } else {
                calculateEip1559Fees(defaultLimit, chain, isSwap, evmApi)
            }

        val l1Fees =
            if (chain.isLayer2) {
                calculateLayer1Fees()
            } else {
                BigInteger.ZERO
            }

        return fees.addL1Amount(l1Fees)
    }

    private suspend fun calculateLegacyGasFees(limit: BigInteger, evmApi: EvmApi): GasFees {
        val gasPrice = evmApi.getGasPrice()

        return GasFees(price = gasPrice, limit = limit, amount = gasPrice * limit)
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

        val maxPriorityFeePerGas =
            calculateMaxPriorityFeePerGas(rewardsFeeHistory, chain, isSwap, evmApi)
        val baseNetworkPrice =
            calculateBaseNetworkPrice(baseNetworkPriceDeferred.await(), isSwap, chain)
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

    private fun calculateBaseNetworkPrice(
        baseNetworkPrice: BigInteger,
        swap: Boolean,
        chain: Chain,
    ): BigInteger {
        if (swap) {
            // Ethereum swap calldata embeds a short deadline (1inch ~1–5 min, Kyber 20 min).
            // The stored Eip1559.networkPrice ends up at baseFee × 1.5 for Ethereum
            // (× 1.1 for other chains), and calculateMaxFeePerGas then bumps it by a
            // further 20%, so the broadcast ceiling is baseFee × 1.8 + priorityFee on
            // Ethereum (× 1.32 elsewhere). This is sized to survive base-fee spikes
            // during the MPC review + sign window so the tx still lands before the
            // deadline.
            val bumpPercent = if (chain == Chain.Ethereum) 50 else 10
            return baseNetworkPrice.increaseByPercent(bumpPercent)
        }
        // Non-swap transfers: bump the committed base so the broadcast ceiling
        // (calculateMaxFeePerGas adds a further 20%) lands at baseFee × 1.5 + priorityFee
        // instead of baseFee × 1.2 + priorityFee. EIP-1559 raises the base fee up to 12.5%
        // per block, so a flat 20% margin is exhausted after ~1.5 blocks and a send can
        // stall in the mempool when the base fee climbs across the MPC review + sign window.
        // The extra headroom only improves inclusion robustness — the amount actually paid
        // stays near baseFee + priorityFee under EIP-1559.
        return baseNetworkPrice.increaseByPercent(NON_SWAP_BASE_FEE_BUMP_PERCENT)
    }

    private suspend fun calculateMaxPriorityFeePerGas(
        rewardsFeeHistory: List<BigInteger>,
        chain: Chain,
        isSwap: Boolean,
        evmApi: EvmApi,
    ): BigInteger {
        return if (chain.id == Chain.Avalanche.id) {
            evmApi.getMaxPriorityFeePerGas() // exception for avalanche
        } else if (chain == Chain.Ethereum && isSwap) {
            // Ethereum swaps: track the top of the recent reward window so the tx gets
            // included before the embedded DEX deadline (1inch OrderExpired revert was
            // the original failure mode). getFeeHistory() returns the window sorted
            // ascending, so lastOrNull() picks the max. The sample is clamped at
            // ETHEREUM_SWAP_PRIORITY_FEE_CAP so a single MEV-burst block doesn't
            // balloon the user-displayed bond (priorityFee × DEFAULT_SWAP_LIMIT), and
            // ETHEREUM_SWAP_PRIORITY_FEE_FLOOR keeps inclusion fast on a quiet window.
            if (rewardsFeeHistory.isEmpty()) {
                Timber.w("Fee history is empty for %s, using fallback", chain)
            }
            val sample = rewardsFeeHistory.lastOrNull() ?: BigInteger.ZERO
            val capped = sample.min(ETHEREUM_SWAP_PRIORITY_FEE_CAP)
            maxOf(capped, ETHEREUM_SWAP_PRIORITY_FEE_FLOOR)
        } else {
            when (chain) {
                // Arb and Mantle requires no miner tip
                Chain.Arbitrum,
                Chain.Mantle -> BigInteger.ZERO

                // Blast is a dead chain, with empty blocks with 0 miner tips
                Chain.Blast ->
                    maxOf(
                        rewardsFeeHistory.maxOrNull() ?: BigInteger.ZERO,
                        DEFAULT_MAX_PRIORITY_FEE_BLAST,
                    )

                // picked max from 10 previous blocks, then ensure inclusion. For l2 is quite low
                Chain.Base,
                Chain.Optimism ->
                    rewardsFeeHistory.maxOrNull() ?: DEFAULT_MAX_PRIORITY_FEE_PER_GAS_L2

                // polygon has min of 30 gwei, but some blocks comes with less rewards
                Chain.Polygon -> {
                    if (rewardsFeeHistory.isEmpty()) {
                        Timber.w("Fee history is empty for %s, using fallback", chain)
                    }
                    maxOf(
                        rewardsFeeHistory.median() ?: BigInteger.ZERO,
                        GWEI * DEFAULT_MAX_PRIORITY_FEE_POLYGON,
                    )
                }

                // picked medium with min of 1 GWEI (other EVM chains, plus non-swap Ethereum)
                else -> {
                    if (rewardsFeeHistory.isEmpty()) {
                        Timber.w("Fee history is empty for %s, using fallback", chain)
                    }
                    maxOf(rewardsFeeHistory.median() ?: BigInteger.ZERO, GWEI)
                }
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
        val chain = transaction.coin.chain

        return when {
            transaction is Swap && chain == Chain.Mantle -> DEFAULT_MANTLE_SWAP_LIMIT
            transaction is Swap -> DEFAULT_SWAP_LIMIT
            chain == Chain.Arbitrum -> DEFAULT_ARBITRUM_TRANSFER
            transaction is Transfer && transaction.coin.isNativeToken -> DEFAULT_COIN_TRANSFER_LIMIT
            transaction is Transfer -> DEFAULT_TOKEN_TRANSFER_LIMIT_WITH_MARGIN
            else -> error("Transaction type not supported: ${transaction::class.simpleName}")
        }
    }

    companion object {
        private val GWEI = BigInteger.TEN.pow(9)

        // Extra bump applied to the committed base fee on the non-swap send path. With the
        // further 20% added in calculateMaxFeePerGas this yields a baseFee × 1.5 + priorityFee
        // ceiling (~3 consecutive max EIP-1559 base-fee increases of 12.5%) so a transfer
        // survives a base-fee climb during the MPC review + sign window without stalling.
        private const val NON_SWAP_BASE_FEE_BUMP_PERCENT = 25

        private val DEFAULT_MAX_PRIORITY_FEE_PER_GAS_L2 = "20".toBigInteger()
        private val DEFAULT_MAX_PRIORITY_FEE_POLYGON = "30".toBigInteger()
        private val DEFAULT_MAX_PRIORITY_FEE_BLAST = BigInteger.TEN.pow(7) // 0.01 GWEI
        // Minimum tip for Ethereum swaps so a quiet window still lands the tx before the embedded
        // DEX deadline. Recalibrated from 2 GWEI: post-blob mainnet base fees and tips now sit
        // around 0.1 GWEI, where a 2 GWEI floor was ~20x the market and dominated the
        // user-displayed fee bond (gasLimit x maxFeePerGas) on small swaps — e.g. ~$2.8 of "network
        // fee" on a ~$7 swap. 0.5 GWEI keeps a comfortable inclusion margin (several times the
        // typical ~0.1 GWEI tip) without inflating the bond; busy windows are unaffected because
        // the
        // recent-window max reward is used whenever it exceeds this floor.
        private val ETHEREUM_SWAP_PRIORITY_FEE_FLOOR = GWEI.divide(BigInteger.valueOf(2))
        // Upper bound on the per-block reward sample so a single MEV-burst block doesn't
        // propagate as the authorized tip. EIP-1559 means the actual paid amount stays
        // near baseFee + priorityFee, but the user-displayed bond is sized off
        // maxPriorityFeePerGas × DEFAULT_SWAP_LIMIT (600k), so we cap the bond surface
        // even though inclusion would tolerate higher tips.
        private val ETHEREUM_SWAP_PRIORITY_FEE_CAP = GWEI * BigInteger.valueOf(10)

        val DEFAULT_SWAP_LIMIT = "600000".toBigInteger()
        val DEFAULT_COIN_TRANSFER_LIMIT = "23000".toBigInteger()
        val DEFAULT_TOKEN_TRANSFER_LIMIT = "150000".toBigInteger()

        // ERC-20 transfer gas-limit floor: the 150k base plus a 40% safety margin. Single
        // source of truth shared with BlockChainSpecificRepository so the signed gasLimit and
        // the displayed fee bond use the same floor and cannot drift (issue #4857).
        val DEFAULT_TOKEN_TRANSFER_LIMIT_WITH_MARGIN =
            DEFAULT_TOKEN_TRANSFER_LIMIT.increaseByPercent(40)

        val DEFAULT_ARBITRUM_TRANSFER = "160000".toBigInteger()
        val DEFAULT_MANTLE_SWAP_LIMIT = "3000000000".toBigInteger()
    }
}

// Returns the mid-index element, or null for an empty list. Callers pair it with
// a chain-specific floor via `maxOf(..., minFee)` so a missing sample degrades to
// that floor instead of throwing.
private fun List<BigInteger>.median(): BigInteger? = getOrNull(size / 2)
