package com.vultisig.wallet.data.blockchain.ethereum

import com.vultisig.wallet.data.api.EvmApiFactory
import com.vultisig.wallet.data.blockchain.model.BlockchainTransaction
import com.vultisig.wallet.data.blockchain.model.Eip1559
import com.vultisig.wallet.data.blockchain.model.Fee
import com.vultisig.wallet.data.blockchain.FeeService
import com.vultisig.wallet.data.blockchain.model.Transfer
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject

class ZkFeeService @Inject constructor(
    private val evmApiFactory: EvmApiFactory,
) : FeeService {
    override suspend fun calculateFees(transaction: BlockchainTransaction): Fee = coroutineScope {
        require(transaction is Transfer) {
            "Transaction type not supported ${transaction::class.simpleName}"
        }
        val chain = transaction.coin.chain
        val coin = transaction.coin
        val toAddress = transaction.to
        val evmApi = evmApiFactory.createEvmApi(chain)
        val data = "0xffffffff"

        val feeEstimate = evmApi.zkEstimateFee(
            srcAddress = coin.address,
            dstAddress = toAddress,
            data = data
        )

        Eip1559(
            limit = feeEstimate.gasLimit,
            maxPriorityFeePerGas = feeEstimate.maxPriorityFeePerGas,
            maxFeePerGas = feeEstimate.maxFeePerGas,
            networkPrice = feeEstimate.maxFeePerGas,
            amount = feeEstimate.maxFeePerGas * feeEstimate.gasLimit,
        )
    }

    override suspend fun calculateDefaultFees(transaction: BlockchainTransaction): Fee {
        error("Rpc Error: Can't fetch fees")
    }
}
