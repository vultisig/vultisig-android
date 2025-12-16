package com.vultisig.wallet.data.blockchain.utxo

import com.vultisig.wallet.data.api.BlockChairApi
import com.vultisig.wallet.data.blockchain.FeeService
import com.vultisig.wallet.data.blockchain.model.BasicFee
import com.vultisig.wallet.data.blockchain.model.BlockchainTransaction
import com.vultisig.wallet.data.blockchain.model.Fee
import com.vultisig.wallet.data.models.Chain
import java.math.BigInteger
import javax.inject.Inject


class UtxoFeeService @Inject constructor(
    private val blockChairApi: BlockChairApi,
) : FeeService {

    override suspend fun calculateFees(transaction: BlockchainTransaction): Fee {
        val amount = getDefaultGasFee(transaction.coin.chain)
        return BasicFee(
            amount
        )
    }

    override suspend fun calculateDefaultFees(transaction: BlockchainTransaction): Fee {
        val amount = getDefaultGasFee(transaction.coin.chain)
        return BasicFee(
            amount
        )
    }


    suspend fun getDefaultGasFee(chain: Chain): BigInteger {
        val gas = when (chain) {
            Chain.Zcash -> "1000".toBigInteger()
            Chain.Cardano -> "180000".toBigInteger()

            else -> {
                val gas = blockChairApi.getBlockChairStats(chain)
                gas.multiply(BigInteger("5")).divide(BigInteger("2"))
            }
        }
        return gas
    }

}