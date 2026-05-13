package com.vultisig.wallet.data.api.txstatus

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.usecases.txstatus.TransactionResult
import com.vultisig.wallet.data.usecases.txstatus.TransactionStatusProvider
import com.vultisig.wallet.data.utils.bodyOrThrow
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import timber.log.Timber

/**
 * Resolves THORChain / MayaChain inbound-tx status using Midgard `/v2/actions?txid=...`.
 *
 * The previous implementation treated any HTTP 200 from `/thorchain/tx/status/{hash}` as
 * [TransactionResult.Confirmed], but the network returns 200 for both completed and refunded
 * inbound txs — so refunded LP/swap/savers/loan/bond txs were shown as successful in history.
 * Midgard's `actions` endpoint exposes the action `type` ("refund" vs "swap"/"addLiquidity"/…) and
 * a human-readable `metadata.refund.reason`, which is what we surface in the UI.
 */
class ThorMayaChainStatusProvider @Inject constructor(private val httpClient: HttpClient) :
    TransactionStatusProvider {

    private val midgardUrls =
        mapOf(
            Chain.ThorChain to "https://gateway.liquify.com/chain/thorchain_midgard/v2/actions",
            Chain.MayaChain to "https://midgard.mayachain.info/v2/actions",
        )

    override suspend fun checkStatus(txHash: String, chain: Chain): TransactionResult {
        val baseUrl = midgardUrls[chain] ?: return TransactionResult.Failed("Unknown chain")
        return try {
            val response: MidgardActionsResponse =
                httpClient.get("$baseUrl?txid=$txHash").bodyOrThrow()
            val action = response.actions.firstOrNull() ?: return TransactionResult.Pending
            mapAction(action)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "THOR/Maya status check failed for %s on %s", txHash, chain)
            TransactionResult.Pending
        }
    }

    private fun mapAction(action: MidgardAction): TransactionResult =
        when {
            action.type == ACTION_TYPE_REFUND ->
                TransactionResult.Refunded(
                    reason =
                        action.metadata?.refund?.reason?.takeUnless { it.isBlank() }
                            ?: DEFAULT_REFUND_REASON
                )
            action.status == ACTION_STATUS_SUCCESS -> TransactionResult.Confirmed
            else -> TransactionResult.Pending
        }

    private companion object {
        const val ACTION_TYPE_REFUND = "refund"
        const val ACTION_STATUS_SUCCESS = "success"
        const val DEFAULT_REFUND_REASON = "Transaction refunded"
    }
}

@Serializable
internal data class MidgardActionsResponse(val actions: List<MidgardAction> = emptyList())

@Serializable
internal data class MidgardAction(
    @SerialName("type") val type: String? = null,
    @SerialName("status") val status: String? = null,
    @SerialName("metadata") val metadata: MidgardActionMetadata? = null,
)

@Serializable
internal data class MidgardActionMetadata(@SerialName("refund") val refund: MidgardRefund? = null)

@Serializable internal data class MidgardRefund(@SerialName("reason") val reason: String? = null)
