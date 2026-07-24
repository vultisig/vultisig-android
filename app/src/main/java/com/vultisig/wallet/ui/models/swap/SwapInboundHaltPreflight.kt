package com.vultisig.wallet.ui.models.swap

import com.vultisig.wallet.data.api.MayaChainApi
import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.api.errors.SwapException
import com.vultisig.wallet.data.api.models.thorchain.THORChainInboundAddress
import com.vultisig.wallet.data.models.SwapTransaction
import com.vultisig.wallet.data.models.payload.SwapPayload
import com.vultisig.wallet.data.models.swapAssetName
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
constructor(private val thorChainApi: ThorChainApi, private val mayaChainApi: MayaChainApi) {

    suspend fun assertSourceChainNotHalted(transaction: SwapTransaction) {
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

    private companion object {
        const val SIGNING_BLOCKED_MESSAGE = "Source-chain trading is halted or unavailable"
    }
}
