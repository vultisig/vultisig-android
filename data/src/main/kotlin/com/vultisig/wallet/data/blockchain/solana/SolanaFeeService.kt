package com.vultisig.wallet.data.blockchain.solana

import com.vultisig.wallet.data.blockchain.BlockchainTransaction
import com.vultisig.wallet.data.blockchain.Fee
import com.vultisig.wallet.data.blockchain.FeeService

class SolanaFeeService: FeeService {
    override suspend fun calculateFees(transaction: BlockchainTransaction): Fee {
        TODO("Not yet implemented")
    }

    override suspend fun calculateDefaultFees(transaction: BlockchainTransaction): Fee {
        TODO("Not yet implemented")
    }
}