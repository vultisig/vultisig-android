package com.vultisig.wallet.data.blockchain.polkadot

import com.vultisig.wallet.data.api.PolkadotApi
import com.vultisig.wallet.data.blockchain.BasicFee
import com.vultisig.wallet.data.blockchain.Fee
import com.vultisig.wallet.data.blockchain.FeeService
import com.vultisig.wallet.data.models.Chain
import java.math.BigInteger

class PolkadotFeeService(
    private val polkadotApi: PolkadotApi,
): FeeService {
    override suspend fun calculateFees(
        chain: Chain,
        limit: BigInteger,
        isSwap: Boolean,
        to: String?
    ): Fee {
        return BasicFee(POLKADOT_DEFAULT_FEE)
    }

    override suspend fun calculateDefaultFees(): Fee {
        return BasicFee(POLKADOT_DEFAULT_FEE)
    }

    private companion object {
        val POLKADOT_DEFAULT_FEE = "250_000_000".toBigInteger()
    }
}