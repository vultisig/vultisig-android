package com.vultisig.wallet.ui.models.swap

import com.vultisig.wallet.data.api.MayaChainApi
import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.api.errors.SwapException
import com.vultisig.wallet.data.api.models.thorchain.THORChainInboundAddress
import com.vultisig.wallet.data.models.SwapTransaction
import com.vultisig.wallet.data.models.THORChainSwapPayload
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

        val payload = transaction.payload
        val fetchInboundAddresses: suspend () -> List<THORChainInboundAddress> =
            when (payload) {
                is SwapPayload.ThorChain -> thorChainApi::getTHORChainInboundAddresses
                is SwapPayload.MayaChain -> mayaChainApi::getInboundAddresses
                else -> return
            }
        val swap =
            when (payload) {
                is SwapPayload.ThorChain -> payload.data
                is SwapPayload.MayaChain -> payload.data
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

        assertDepositTargetsLiveInbound(swap, inbound)
    }

    /**
     * The deposit goes to the vault and router the quote named, which can be stale by signing time
     * — vaults rotate — or come from a node that disagrees with the live inbound list. A deposit
     * to a retiring vault or the wrong router is not refunded reliably, so both must match the
     * inbound set fetched just now.
     *
     * A swap out of the protocol's own chain (RUNE, CACAO, secured assets) is a `MsgDeposit` with
     * no inbound vault: the quote names none and the payload carries the sender's own address in
     * its place, so there is nothing to compare.
     */
    private fun assertDepositTargetsLiveInbound(
        swap: THORChainSwapPayload,
        inbound: THORChainInboundAddress?,
    ) {
        if (swap.vaultAddress.equals(swap.fromAddress, ignoreCase = true)) return
        if (inbound == null || !swap.vaultAddress.equals(inbound.address, ignoreCase = true)) {
            throw SwapException.TradingHalted(INBOUND_CHANGED_MESSAGE)
        }
        val router = swap.routerAddress?.takeIf { it.isNotBlank() } ?: return
        if (!router.equals(inbound.router, ignoreCase = true)) {
            throw SwapException.TradingHalted(INBOUND_CHANGED_MESSAGE)
        }
    }

    /**
     * Re-checks the `EnableAdvSwapQueue` mimir at sign time for a THORChain limit order (memo
     * starts with `=<`). The mimir can flip while the user sits on the confirmation screen, and a
     * `=<` order placed while the queue is disabled can execute as an unprotected market swap — so
     * the gate is re-run here, fail-closed, just before signing.
     *
     * The read forces a network refresh: the mimir cache is a shared 30s-TTL singleton, and signing
     * normally happens within that window of the placement-time check, so a cached read would
     * replay the value that already let the order through and verify nothing.
     */
    private suspend fun assertAdvancedSwapQueueEnabledForLimitOrder(transaction: SwapTransaction) {
        val memo = transaction.memo ?: return
        if (!memo.startsWith(LimitSwapMemo.PREFIX)) return
        if (!thorMimirRepository.isAdvancedSwapQueueEnabled(forceRefresh = true)) {
            throw SwapException.TradingHalted(ADV_SWAP_QUEUE_DISABLED_MESSAGE)
        }
    }

    private companion object {
        const val SIGNING_BLOCKED_MESSAGE = "Source-chain trading is halted or unavailable"
        const val INBOUND_CHANGED_MESSAGE =
            "The swap's inbound vault has changed since the quote; get a new quote"
        const val ADV_SWAP_QUEUE_DISABLED_MESSAGE =
            "THORChain's advanced swap queue is disabled; limit orders can't be placed right now"
    }
}
