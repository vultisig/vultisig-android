package com.vultisig.wallet.data.blockchain

interface FeeService {
    suspend fun calculateFees(transaction: Transaction): Fee
}