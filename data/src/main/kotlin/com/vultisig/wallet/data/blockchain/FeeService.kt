package com.vultisig.wallet.data.blockchain

import java.math.BigInteger

interface FeeService {
    suspend fun calculateFees(transaction: Transaction, limit: BigInteger): Fee
}