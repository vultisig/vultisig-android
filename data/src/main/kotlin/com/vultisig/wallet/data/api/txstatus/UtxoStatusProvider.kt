package com.vultisig.wallet.data.api.txstatus

import com.vultisig.wallet.data.api.BlockChairApi
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.usecases.txstatus.TransactionResult
import com.vultisig.wallet.data.usecases.txstatus.TransactionStatusProvider
import javax.inject.Inject

class UtxoStatusProvider @Inject constructor(
    private val blockChairApi: BlockChairApi,
) : TransactionStatusProvider {


    override suspend fun checkStatus(txHash: String, chain: Chain): TransactionResult {
        val response = blockChairApi.getTsStatus(chain, txHash)
        val txData = response?.data?.get(txHash)
        return try {
            when {
                txData == null -> {
                    TransactionResult.NotFound
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
                    val confirmations = response.context.state - txData.transaction.blockId + 1

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
        } catch (_: Exception) {
                TransactionResult.NotFound
            }
        }
    }