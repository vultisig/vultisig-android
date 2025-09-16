package com.vultisig.wallet.data.blockchain.xrp

import com.vultisig.wallet.data.blockchain.Fee
import com.vultisig.wallet.data.blockchain.FeeService
import com.vultisig.wallet.data.models.Chain
import java.math.BigInteger

class XRPFeeService: FeeService {
    override suspend fun calculateFees(
        chain: Chain,
        limit: BigInteger,
        isSwap: Boolean
    ): Fee {
        TODO("Not yet implemented")
    }

    override suspend fun calculateDefaultFees(): Fee {
        TODO("Not yet implemented")
    }
}