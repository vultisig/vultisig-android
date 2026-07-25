package com.vultisig.wallet.ui.models.swap

import com.vultisig.wallet.data.api.MayaChainApi
import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.api.errors.SwapException
import com.vultisig.wallet.data.api.models.thorchain.THORChainInboundAddress
import com.vultisig.wallet.data.models.SwapTransaction
import com.vultisig.wallet.data.models.payload.SwapPayload
import com.vultisig.wallet.data.models.swapAssetName
import com.vultisig.wallet.data.repositories.ThorMimirRepository
import com.vultisig.wallet.data.swap.limit.LimitSwapMemo
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import timber.log.Timber

/**
 * Live, sign-time safety gate for native THORChain and MayaChain swaps.
 *
 * Quotes can become stale between the swap form and signing. Fetching the protocol's current
 * inbound set here prevents spending source-chain gas on a deposit that the protocol will refund.
 * The check deliberately fails closed when the inbound status cannot be verified.
 */
internal class SwapInboundHaltPreflight
@Inject
constructor(
    private val thorChainApi: ThorChainApi,
    private val mayaChainApi: MayaChainApi,
    private val thorMimirRepository: ThorMimirRepository,
) {

    suspend fun assertSourceChainNotHalted(transaction: SwapTransaction) {
        assertAdvancedSwapQueueEnabledForLimitOrder(transaction)

        val fetchInboundAddresses: suspend () -> List<THORChainInboundAddress> =
            when (transaction.payload) {
                is SwapPayload.ThorChain -> thorChainApi::getTHORChainInboundAddresses
                is SwapPayload.MayaChain -> mayaChainApi::getInboundAddresses
                else -> return
            }

        val inboundAddresses =
            try {
                fetchInboundAddresses()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Unable to verify swap inbound status; blocking native swap")
                throw SwapException.TradingHalted(SIGNING_BLOCKED_MESSAGE)
            }

        val sourceChain = transaction.srcToken.chain.swapAssetName()
        val inbound =
            inboundAddresses.firstOrNull { it.chain.equals(sourceChain, ignoreCase = true) }

        if (inbound?.let { it.halted || it.globalTradingPaused || it.chainTradingPaused } == true) {
            throw SwapException.TradingHalted(SIGNING_BLOCKED_MESSAGE)
        }
    }

    /**
     * Re-checks the `EnableAdvSwapQueue` mimir at sign time for a THORChain limit order (memo
     * starts with `=<`). The mimir can flip while the user sits on the confirmation screen, and a
     * `=<` order placed while the queue is disabled can execute as an unprotected market swap — so
     * the gate is re-run here, fail-closed, just before signing.
     */
    private suspend fun assertAdvancedSwapQueueEnabledForLimitOrder(transaction: SwapTransaction) {
        val memo = transaction.memo ?: return
        if (!memo.startsWith(LimitSwapMemo.PREFIX)) return
        if (!thorMimirRepository.isAdvancedSwapQueueEnabled()) {
            throw SwapException.TradingHalted(ADV_SWAP_QUEUE_DISABLED_MESSAGE)
        }
    }

    private companion object {
        const val SIGNING_BLOCKED_MESSAGE = "Source-chain trading is halted or unavailable"
        const val ADV_SWAP_QUEUE_DISABLED_MESSAGE =
            "THORChain's advanced swap queue is disabled; limit orders can't be placed right now"
    }
}
