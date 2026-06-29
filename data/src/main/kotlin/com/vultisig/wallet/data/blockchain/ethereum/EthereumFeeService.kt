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
import com.vultisig.wallet.data.models.isOpStackL2
import com.vultisig.wallet.data.models.oneInchChainId
import com.vultisig.wallet.data.models.supportsLegacyGas
import com.vultisig.wallet.data.utils.Numeric
import com.vultisig.wallet.data.utils.increaseByPercent
import java.math.BigInteger
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
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

        val l1Fees = calculateLayer1Fees(transaction, fees, evmApi)

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

    // OP-stack L2s charge an L1 data-availability fee on top of L2 execution gas. We price it via
    // the chain's GasPriceOracle predeploy so the displayed/reserved fee (and any max-send
    // headroom) reflects the true cost. Non-OP-stack chains — including the other L2s Arbitrum and
    // ZkSync, which fold their L1 component in elsewhere — contribute zero here. Estimation is
    // best-effort: a failed oracle call degrades to zero rather than blocking the whole fee calc.
    private suspend fun calculateLayer1Fees(
        transaction: BlockchainTransaction,
        fees: Fee,
        evmApi: EvmApi,
    ): BigInteger {
        val chain = transaction.coin.chain
        if (!chain.isOpStackL2) return BigInteger.ZERO
        if (fees !is Eip1559) return BigInteger.ZERO

        // Resolve the call context outside the try so a genuine programming error (unsupported
        // transaction type) surfaces instead of being masked as a zero L1 fee. Only the best-effort
        // oracle call below degrades to zero on failure.
        val context = resolveL1CallContext(transaction)

        return try {
            evmApi.getOpStackL1Fee(
                senderAddress = transaction.coin.address,
                to = context.to,
                value = context.value,
                data = context.data,
                gasLimit = fees.limit,
                maxFeePerGas = fees.maxFeePerGas,
                maxPriorityFeePerGas = fees.maxPriorityFeePerGas,
                chainId = chain.oneInchChainId().toBigInteger(),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "L1 data fee estimation failed for %s; treating as zero", chain)
            BigInteger.ZERO
        }
    }

    // Mirrors what each transaction type actually broadcasts (the L1 fee scales with calldata
    // size):
    // native sends carry the memo, ERC-20 sends carry the transfer calldata to the token contract,
    // and swaps carry the router calldata.
    private fun resolveL1CallContext(transaction: BlockchainTransaction): L1CallContext {
        val coin = transaction.coin
        return when (transaction) {
            is Transfer ->
                if (coin.isNativeToken) {
                    L1CallContext(
                        to = transaction.to,
                        value = transaction.amount,
                        data =
                            transaction.memo?.takeIf { it.isNotEmpty() }?.toByteArray()
                                ?: ByteArray(0),
                    )
                } else {
                    L1CallContext(
                        to = coin.contractAddress,
                        value = BigInteger.ZERO,
                        data = erc20TransferCallData(transaction.to, transaction.amount),
                    )
                }
            is Swap ->
                L1CallContext(
                    to = transaction.to,
                    value = if (coin.isNativeToken) transaction.amount else BigInteger.ZERO,
                    // The router calldata is the dominant part of a swap's L1 data fee, but it is
                    // not known here: gas estimation runs in parallel with the quote fetch, so
                    // every
                    // live caller builds the Swap with an empty callData. Pricing the empty payload
                    // would drop almost the entire L1 cost, so we fall back to a representative
                    // swap-calldata payload. If a caller ever supplies real calldata, we honour it.
                    data =
                        transaction.callData
                            .takeIf { it.isNotEmpty() }
                            ?.let { Numeric.hexStringToByteArray(it) }
                            ?: REPRESENTATIVE_SWAP_CALLDATA,
                )
            else ->
                error("Unsupported transaction type for L1 fee: ${transaction::class.simpleName}")
        }
    }

    // ERC-20 transfer(address,uint256) calldata, built with plain hex concatenation (no JNI) so
    // this
    // class stays unit-testable. Matches EvmApi.constructERC20TransferData.
    private fun erc20TransferCallData(recipient: String, amount: BigInteger): ByteArray {
        val methodId = "a9059cbb"
        val paddedAddress = recipient.removePrefix("0x").padStart(64, '0')
        val paddedValue = amount.toString(16).padStart(64, '0')
        return Numeric.hexStringToByteArray(methodId + paddedAddress + paddedValue)
    }

    private data class L1CallContext(val to: String, val value: BigInteger, val data: ByteArray)

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

        val l1Fees = calculateLayer1Fees(transaction, fees, evmApi)

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
            // Ethereum swaps bump the committed base 50% (×1.8 ceiling after calculateMaxFeePerGas)
            // to survive base-fee spikes across the MPC sign window before the embedded DEX
            // deadline — a lower bump caused stuck/expired ETH mainnet swaps. Other chains use 10%.
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
            transaction is Swap -> DEFAULT_SWAP_LIMIT
            chain == Chain.Arbitrum -> DEFAULT_ARBITRUM_TRANSFER
            transaction is Transfer && transaction.coin.isNativeToken -> DEFAULT_COIN_TRANSFER_LIMIT
            transaction is Transfer -> DEFAULT_TOKEN_TRANSFER_LIMIT_WITH_MARGIN
            else -> error("Transaction type not supported: ${transaction::class.simpleName}")
        }
    }

    companion object {
        private val GWEI = BigInteger.TEN.pow(9)

        // Stand-in calldata used to price a swap's OP-stack L1 data fee when the real router
        // calldata is unavailable at estimation time (see resolveL1CallContext). Sized to a typical
        // DEX/router swap payload (~512 bytes covers a 1inch/Kyber route or a THORChain
        // depositWithExpiry memo) and filled with non-zero bytes, which the OP-stack fee formula
        // charges at the higher 16-gas/byte rate — a deliberately conservative estimate so the
        // reserved fee does not under-cover the real L1 cost. Post-Ecotone/Fjord L1 fees are small
        // in absolute terms, so the conservative sizing adds negligible cost while avoiding a gross
        // underestimate.
        const val REPRESENTATIVE_SWAP_CALLDATA_BYTES = 512
        private val REPRESENTATIVE_SWAP_CALLDATA =
            ByteArray(REPRESENTATIVE_SWAP_CALLDATA_BYTES) { 1 }

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
    }
}

// Returns the mid-index element, or null for an empty list. Callers pair it with
// a chain-specific floor via `maxOf(..., minFee)` so a missing sample degrades to
// that floor instead of throwing.
private fun List<BigInteger>.median(): BigInteger? = getOrNull(size / 2)
