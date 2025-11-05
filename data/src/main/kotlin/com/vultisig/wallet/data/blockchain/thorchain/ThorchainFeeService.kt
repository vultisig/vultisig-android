package com.vultisig.wallet.data.blockchain.thorchain

import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.blockchain.model.BlockchainTransaction
import com.vultisig.wallet.data.blockchain.model.Fee
import com.vultisig.wallet.data.blockchain.FeeService
import com.vultisig.wallet.data.blockchain.model.GasFees
import com.vultisig.wallet.data.models.Chain
import javax.inject.Inject

class ThorchainFeeService @Inject constructor(
    private val thorChainApi: ThorChainApi,
): FeeService {
    override suspend fun calculateFees(transaction: BlockchainTransaction): Fee {
        val chain = transaction.coin.chain
        val (feeAmount, feeLimit) = when (chain) {
            Chain.ThorChain ->
                Pair(thorChainApi.getTHORChainNativeTransactionFee(), THORCHAIN_DEFAULT_GAS)
            Chain.MayaChain ->
                Pair(MAYA_DEFAULT_GAS, MAYA_DEFAULT_GAS)
            else -> error("Chain not supported: ${chain.name}")
        }

        return GasFees(
            limit = feeLimit,
            amount = feeAmount,
        )
    }

    override suspend fun calculateDefaultFees(transaction: BlockchainTransaction): Fee {
        val chain = transaction.coin.chain
        return when (chain) {
            Chain.ThorChain ->
                GasFees(
                    limit = THORCHAIN_DEFAULT_GAS,
                    amount = THORCHAIN_DEFAULT_AMOUNT,
                )
            Chain.MayaChain ->
                GasFees(
                    limit = MAYA_DEFAULT_GAS,
                    amount = MAYA_DEFAULT_GAS,
                )
            else -> error("Chain not supported: ${chain.name}")
        }
    }

    private companion object {
        private val THORCHAIN_DEFAULT_AMOUNT = "2000000".toBigInteger()
        private val THORCHAIN_DEFAULT_GAS = "20000000".toBigInteger()

        private val MAYA_DEFAULT_GAS = "2000000000".toBigInteger()
    }
}