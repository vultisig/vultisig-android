package com.vultisig.wallet.data.blockchain.sui

import com.vultisig.wallet.data.api.chains.SuiApi
import com.vultisig.wallet.data.blockchain.BasicFee
import com.vultisig.wallet.data.blockchain.BlockchainTransaction
import com.vultisig.wallet.data.blockchain.Fee
import com.vultisig.wallet.data.blockchain.FeeService
import com.vultisig.wallet.data.blockchain.Transfer
import com.vultisig.wallet.data.utils.increaseByPercent

/**
 * Service that estimates and prepares gas payment details for Sui transactions.
 *
 * Implementation notes:
 *  - Use an RPC dry-run / dev-inspect endpoint to obtain computationUnits and storage effects.
 *  - Query the network reference gas price (an epoch-level value) from the node or SDK.
 *  - Compute: total = computationUnits * referenceGasPrice + storageUnits * storagePricePerUnit.
 *
 * Note:
 *  - Estimates are approximate as much as possible; always allow a small margin in the gas budget.
 */
class SuiFeeService(
    private val suiApi: SuiApi,
): FeeService {
    override suspend fun calculateFees(transaction: BlockchainTransaction): Fee {
        require(transaction is Transfer) {
            "Invalid Transfer type: ${transaction::class.simpleName}"
        }

        /*
        val dryRunResult = rpcClient.dryRunTransaction(txBytes)
        val gasUsed = dryRunResult.effects.gasUsed
        val dryRunGasUsed = gasUsed.computationCost + gasUsed.storageCost
        val gasPrice = rpcClient.getReferenceGasPrice()
        val coinOverhead = COINS * GAS_OVERHEAD_PER_COIN * gasPrice
        return SimpleFee(amount = dryRunGasUsed + coinOverhead)
         */

        error("")
    }

    override suspend fun calculateDefaultFees(transaction: BlockchainTransaction): Fee {
        val gasPrice = suiApi.getReferenceGasPrice()

        val estimatedFees = (BASELINE_COMPUTATION_COIN_TRANSFER * gasPrice) + BASELINE_STORAGE
        val feesWithBuffer = estimatedFees.increaseByPercent(20)

        return BasicFee(amount = feesWithBuffer)
    }

    private companion object {
        // Default Limit for
        val BASELINE_COMPUTATION_COIN_TRANSFER = "1300".toBigInteger()
        // Baseline storage cost in MIST
        val BASELINE_STORAGE = "50".toBigInteger()
    }
}