package com.vultisig.wallet.data.api.txstatus

import com.vultisig.wallet.data.api.EvmApiFactory
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.usecases.txstatus.TransactionResult
import com.vultisig.wallet.data.usecases.txstatus.TransactionStatusProvider
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import timber.log.Timber

class EvmStatusProvider @Inject constructor(private val evmApiFactory: EvmApiFactory) :
    TransactionStatusProvider {

    override suspend fun checkStatus(txHash: String, chain: Chain): TransactionResult =
        try {
            val evmJson = evmApiFactory.createEvmApi(chain).getTxStatus(txHash)
            when (evmJson?.result?.status) {
                "0x1" -> TransactionResult.Confirmed
                "0x0" -> TransactionResult.Failed("Transaction reverted")
                else -> TransactionResult.Pending
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "EVM status check failed for %s on %s", txHash, chain)
            TransactionResult.Pending
        }
}
