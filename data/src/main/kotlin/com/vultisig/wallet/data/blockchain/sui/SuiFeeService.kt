package com.vultisig.wallet.data.blockchain.sui

import com.vultisig.wallet.data.api.chains.SuiApi
import com.vultisig.wallet.data.blockchain.BasicFee
import com.vultisig.wallet.data.blockchain.BlockchainTransaction
import com.vultisig.wallet.data.blockchain.Fee
import com.vultisig.wallet.data.blockchain.FeeService
import com.vultisig.wallet.data.utils.increaseByPercent
import java.math.BigInteger

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
        TODO("Not yet implemented")
    }

    override suspend fun calculateDefaultFees(transaction: BlockchainTransaction): Fee {
        val gasPrice = suiApi.getReferenceGasPrice()

        val estimatedFees = (BASELINE_COMPUTATION_COIN_TRANSFER * gasPrice) + BASELINE_STORAGE
        val feesWithBuffer = estimatedFees.increaseByPercent(20)

        return BasicFee(amount = feesWithBuffer)
    }

    private companion object {
        val BASELINE_COMPUTATION_COIN_TRANSFER = BigInteger.valueOf(1200)
        // Baseline storage cost in MIST
        val BASELINE_STORAGE = BigInteger.valueOf(50)
    }
}