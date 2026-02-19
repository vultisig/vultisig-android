package com.vultisig.wallet.data.api.txstatus

import com.vultisig.wallet.data.api.BlockChairApi
import com.vultisig.wallet.data.api.models.quotes.BlockChainStatusDeserialized
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.usecases.txstatus.TransactionResult
import com.vultisig.wallet.data.usecases.txstatus.TransactionStatusProvider
import javax.inject.Inject
import kotlin.text.get

class UtxoStatusProvider @Inject constructor(
    private val blockChairApi: BlockChairApi,
) : TransactionStatusProvider {


    override suspend fun checkStatus(txHash: String, chain: Chain): TransactionResult {
        val response = blockChairApi.getTsStatus(
            chain,
            txHash
        )
        val txData = when (response) {
            is BlockChainStatusDeserialized.Result -> response.data.data?.get(txHash)
            is BlockChainStatusDeserialized.Empty -> null
            null -> null
        }
        val context = when (response) {
            is BlockChainStatusDeserialized.Result -> response.data.context
            is BlockChainStatusDeserialized.Empty -> null
            null -> null
        }
        return try {
            when {
                txData == null -> {
                    TransactionResult.Pending
                }

                txData.transaction == null -> {
                    TransactionResult.NotFound
                }

                txData.transaction.blockId == -1 -> {
                    TransactionResult.Pending
                }

                txData.transaction.blockId == null -> {
                    TransactionResult.NotFound
                }

                else -> {
                    val confirmations =
                        context?.state?.minus(txData.transaction.blockId)?.plus(1) ?: 0

                    when {
                        confirmations <= 0 -> {
                            TransactionResult.Pending
                        }

                        else -> {
                            TransactionResult.Confirmed
                        }
                    }
                }
            }
        } catch (e: Exception) {
            TransactionResult.Failed(e.message.toString())
        }
    }
}