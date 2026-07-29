package com.vultisig.wallet.data.blockchain.ethereum

import com.vultisig.wallet.data.api.EvmApiFactory
import com.vultisig.wallet.data.blockchain.FeeService
import com.vultisig.wallet.data.blockchain.model.BlockchainTransaction
import com.vultisig.wallet.data.blockchain.model.Eip1559
import com.vultisig.wallet.data.blockchain.model.Fee
import com.vultisig.wallet.data.blockchain.model.Swap
import com.vultisig.wallet.data.blockchain.model.Transfer
import com.vultisig.wallet.data.common.add0x
import com.vultisig.wallet.data.utils.Numeric
import javax.inject.Inject

class ZkFeeService @Inject constructor(private val evmApiFactory: EvmApiFactory) : FeeService {
    override suspend fun calculateFees(transaction: BlockchainTransaction): Fee =
        estimateFee(transaction)

    override suspend fun calculateDefaultFees(transaction: BlockchainTransaction): Fee =
        estimateFee(transaction)

    private suspend fun estimateFee(transaction: BlockchainTransaction): Fee {
        val evmApi = evmApiFactory.createEvmApi(transaction.coin.chain)
        val call = resolveCall(transaction)

        val feeEstimate =
            evmApi.zkEstimateFee(
                srcAddress = transaction.coin.address,
                dstAddress = call.to,
                data = call.data,
            )

        return Eip1559(
            limit = feeEstimate.gasLimit,
            maxPriorityFeePerGas = feeEstimate.clampedPriorityFee(),
            maxFeePerGas = feeEstimate.maxFeePerGas,
            networkPrice = feeEstimate.maxFeePerGas,
            amount = feeEstimate.maxFeePerGas * feeEstimate.gasLimit,
        )
    }

    /**
     * zkSync meters both gas and pubdata by calldata byte, and the signed gas limit comes from this
     * same estimate — so the estimate has to describe the call the transaction will actually
     * broadcast rather than a fixed stand-in payload. A longer memo is a longer, costlier payload,
     * and a stand-in that ignores it both misprices the preview and under-funds the signed
     * transaction.
     */
    private fun resolveCall(transaction: BlockchainTransaction): ZkCall {
        val coin = transaction.coin
        return when (transaction) {
            is Transfer ->
                if (coin.isNativeToken) {
                    // A native send calls the recipient directly and carries the memo as its
                    // payload — no memo means no calldata.
                    ZkCall(to = transaction.to, data = memoCallData(transaction.memo).asCallData())
                } else {
                    // An ERC-20 send calls the token contract, not the recipient (see
                    // ERC20Helper). Estimating against the recipient priced a bare value
                    // transfer, so the signed limit could not cover the token transfer.
                    ZkCall(
                        to = coin.contractAddress,
                        data =
                            erc20TransferCallData(transaction.to, transaction.amount).asCallData(),
                    )
                }
            is Swap ->
                ZkCall(
                    to = transaction.to,
                    // Router calldata is fetched in parallel with the quote, so callers that build
                    // a Swap purely to price it leave it empty; there is nothing to build then.
                    data =
                        transaction.callData.takeIf { it.isNotEmpty() }?.add0x() ?: EMPTY_CALL_DATA,
                )
        }
    }

    private fun ByteArray.asCallData(): String = Numeric.toHexString(this)

    private data class ZkCall(val to: String, val data: String)

    internal companion object {
        internal const val EMPTY_CALL_DATA = "0x"
    }
}
