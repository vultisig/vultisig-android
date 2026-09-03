package com.vultisig.wallet.data.api.txstatus

import com.vultisig.wallet.data.api.EvmApi
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
            val api = evmApiFactory.createEvmApi(chain)
            when (api.getTxStatus(txHash)?.result?.status) {
                "0x1" -> TransactionResult.Confirmed
                // The receipt says only that it failed. Ask the chain why while the block is still
                // fresh enough for a node to replay it — this is the one moment the answer is
                // reliably available, and it is what lets history explain a slippage revert
                // instead of reporting a bare failure (#5802).
                "0x0" -> TransactionResult.Failed(api.revertReason(txHash) ?: GENERIC_REVERT_REASON)

                else -> TransactionResult.Pending
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "EVM status check failed for %s on %s", txHash, chain)
            TransactionResult.Pending
        }

    /**
     * The revert reason, or null if it cannot be established. Contained so a failing lookup can
     * only cost the explanation: letting it escape would turn a settled failure back into a pending
     * row, which would then be polled forever.
     */
    private suspend fun EvmApi.revertReason(txHash: String): String? =
        try {
            getRevertReason(txHash)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.d(e, "revert reason lookup failed for %s", txHash)
            null
        }

    private companion object {
        const val GENERIC_REVERT_REASON = "Transaction reverted"
    }
}
