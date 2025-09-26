package com.vultisig.wallet.data.blockchain.solana

import com.vultisig.wallet.data.api.SolanaApi
import com.vultisig.wallet.data.blockchain.BlockchainTransaction
import com.vultisig.wallet.data.blockchain.Fee
import com.vultisig.wallet.data.blockchain.FeeService
import com.vultisig.wallet.data.blockchain.GasFees
import com.vultisig.wallet.data.blockchain.Transfer
import com.vultisig.wallet.data.chains.helpers.PRIORITY_FEE_LIMIT
import com.vultisig.wallet.data.chains.helpers.PRIORITY_FEE_PRICE
import com.vultisig.wallet.data.chains.helpers.SolanaHelper
import com.vultisig.wallet.data.models.payload.KeysignPayload
import java.math.BigDecimal
import javax.inject.Inject

class SolanaFeeService @Inject constructor(
    private val solanaApi: SolanaApi,
) : FeeService {

    override suspend fun calculateFees(transaction: BlockchainTransaction): Fee {
        require(transaction is Transfer) {
            "Invalid Transaction Type: ${transaction::class.simpleName}"
        }
        val vaultHexPubKey = transaction.vault.vaultHexPublicKey
        val keySignPayload = buildKeySignPayload()

        val serializedTx = SolanaHelper(vaultHexPubKey).getZeroSignedTransaction(keySignPayload)

        val baseFee = solanaApi.getFeeForMessage(serializedTx)
        val priorityFee = (PRIORITY_FEE_PRICE * PRIORITY_FEE_LIMIT).toBigInteger()
        val priorityAmount = priorityFee
            .toBigDecimal()
            .divide(BigDecimal.TEN.pow(6))
            .toBigInteger()

        return GasFees(
            price = PRIORITY_FEE_PRICE.toBigInteger(),
            limit = PRIORITY_FEE_LIMIT.toBigInteger(),
            amount = baseFee + priorityAmount,
        )
    }

    private fun buildKeySignPayload(): KeysignPayload {
        error("")
    }

    override suspend fun calculateDefaultFees(transaction: BlockchainTransaction): Fee {
        TODO("Not yet implemented")
    }

    private companion object {
        const val DEFAULT_BASE_FEE = ""
    }
}