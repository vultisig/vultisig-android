package com.vultisig.wallet.data.api.txstatus

import com.vultisig.wallet.data.api.BlockChairApi
import com.vultisig.wallet.data.api.models.BlockChainStatusDeserialized
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.usecases.txstatus.TransactionResult
import com.vultisig.wallet.data.usecases.txstatus.TransactionStatusProvider
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import timber.log.Timber

class UtxoStatusProvider @Inject constructor(private val blockChairApi: BlockChairApi) :
    TransactionStatusProvider {

    override suspend fun checkStatus(txHash: String, chain: Chain): TransactionResult =
        try {
            val response = blockChairApi.getTsStatus(chain, txHash)
            val (txData, context) =
                when (response) {
                    is BlockChainStatusDeserialized.Result ->
                        response.data.data?.get(txHash) to response.data.context
                    else -> null to null
                }
            when {
                txData == null -> TransactionResult.Pending
                txData.transaction == null -> TransactionResult.NotFound
                txData.transaction.blockId == -1 -> TransactionResult.Pending
                txData.transaction.blockId == null -> TransactionResult.NotFound
                else -> {
                    val confirmations =
                        context?.state?.minus(txData.transaction.blockId)?.plus(1) ?: 0
                    if (confirmations > 0) TransactionResult.Confirmed
                    else TransactionResult.Pending
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "UTXO status check failed for %s on %s", txHash, chain)
            TransactionResult.Pending
        }
}
