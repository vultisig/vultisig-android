package com.vultisig.wallet.data.blockchain.solana

import com.vultisig.wallet.data.api.SolanaApi
import com.vultisig.wallet.data.blockchain.BlockchainTransaction
import com.vultisig.wallet.data.blockchain.Fee
import com.vultisig.wallet.data.blockchain.FeeService
import com.vultisig.wallet.data.blockchain.Transfer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class SolanaFeeService @Inject constructor(
    private val solanaApi: SolanaApi,
) : FeeService {
    
    override suspend fun calculateFees(transaction: BlockchainTransaction): Fee = withContext(Dispatchers.IO) {
        require(transaction is Transfer) {
            "Invalid Transaction Type: ${transaction::class.simpleName}"
        }

        error("")
    }

    override suspend fun calculateDefaultFees(transaction: BlockchainTransaction): Fee {
        TODO("Not yet implemented")
    }
}