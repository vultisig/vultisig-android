package com.vultisig.wallet.data.blockchain

import com.vultisig.wallet.data.models.Chain
import java.math.BigInteger

interface FeeService {
    suspend fun calculateFees(chain: Chain, limit: BigInteger, isSwap: Boolean): Fee
}