package com.vultisig.wallet.data.blockchain.ethereum

import com.vultisig.wallet.data.api.EvmApiFactory
import com.vultisig.wallet.data.blockchain.FeeService
import com.vultisig.wallet.data.blockchain.model.BlockchainTransaction
import com.vultisig.wallet.data.blockchain.model.Eip1559
import com.vultisig.wallet.data.blockchain.model.Fee
import javax.inject.Inject

class ZkFeeService @Inject constructor(private val evmApiFactory: EvmApiFactory) : FeeService {
    override suspend fun calculateFees(transaction: BlockchainTransaction): Fee =
        estimateFee(transaction)

    override suspend fun calculateDefaultFees(transaction: BlockchainTransaction): Fee =
        estimateFee(transaction)

    private suspend fun estimateFee(transaction: BlockchainTransaction): Fee {
        val chain = transaction.coin.chain
        val coin = transaction.coin
        val toAddress = transaction.to
        val evmApi = evmApiFactory.createEvmApi(chain)

        val feeEstimate =
            evmApi.zkEstimateFee(
                srcAddress = coin.address,
                dstAddress = toAddress,
                data = PLACEHOLDER_CALL_DATA,
            )

        return Eip1559(
            limit = feeEstimate.gasLimit,
            maxPriorityFeePerGas = feeEstimate.clampedPriorityFee(),
            maxFeePerGas = feeEstimate.maxFeePerGas,
            networkPrice = feeEstimate.maxFeePerGas,
            amount = feeEstimate.maxFeePerGas * feeEstimate.gasLimit,
        )
    }

    companion object {
        private const val PLACEHOLDER_MEMO = "0xffffffff"

        /**
         * zkSync prices gas and pubdata by calldata size, so every `zks_estimateFee` call must send
         * the same placeholder payload or the previewed fee and the signed gas limit diverge.
         * Mirrors iOS `RpcEvmService.getGasInfoZk`: the UTF-8 bytes of [PLACEHOLDER_MEMO],
         * hex-encoded — a 10-byte payload, not the 4 bytes the literal string looks like.
         */
        internal val PLACEHOLDER_CALL_DATA: String =
            PLACEHOLDER_MEMO.toByteArray().joinToString(prefix = "0x", separator = "") { byte ->
                String.format("%02x", byte)
            }
    }
}
