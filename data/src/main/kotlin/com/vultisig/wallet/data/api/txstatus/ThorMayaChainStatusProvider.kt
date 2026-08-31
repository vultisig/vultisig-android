package com.vultisig.wallet.data.api.txstatus

import com.vultisig.wallet.data.api.models.cosmos.CosmosEnvelopedTxResponse
import com.vultisig.wallet.data.api.models.cosmos.CosmosTxBody
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.usecases.txstatus.TransactionResult
import com.vultisig.wallet.data.usecases.txstatus.TransactionStatusProvider
import com.vultisig.wallet.data.utils.bodyOrThrow
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode
import java.util.concurrent.ConcurrentHashMap
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
 * Midgard's `actions` endpoint exposes the action `type` ("refund"/"failed" vs
 * "swap"/"addLiquidity"/…) and a human-readable reason in `metadata.refund.reason` or
 * `metadata.failed.reason`, which is what we surface in the UI. `type == "failed"` (e.g. deposits
 * paused, slip-limit hit) is reported with `status == "success"` by Midgard; the network usually
 * refunds those, but the refund is a separate outbound tx and isn't observable until it appears in
 * `action.out`. We therefore only classify `type == "failed"` as [TransactionResult.Refunded] once
 * at least one outbound tx with a non-blank txID is present; otherwise we report
 * [TransactionResult.Failed] so we don't tell the user their funds are back while they're still in
 * flight (or were never refunded).
 *
 * Note the deliberate asymmetry: `type == "refund"` is reported as [TransactionResult.Refunded]
 * immediately without checking `action.out`. The network has explicitly decided to refund, and
 * that's the user-facing answer regardless of whether the outbound leg has landed yet — observing
 * the outbound is the network's bookkeeping, not new information for the user. `type == "failed"`
 * carries no such commitment, which is why it gates on the outbound being observed.
 *
 * Midgard only indexes recognized *actions* (swap/addLiquidity/withdraw/savers/loan/bond/…). A
 * plain native transfer (e.g. sending a secured asset) or a native deposit the node rejected during
 * execution never becomes an action, so `/v2/actions` returns an empty array for it. Treating that
 * as [TransactionResult.Pending] left such txs stuck "in progress" forever while the app kept
 * polling Midgard. When no action is found we therefore consult the native node's
 * `cosmos/tx/v1beta1/txs/{hash}` endpoint, which reports the committed tx's result code directly:
 * non-zero → [TransactionResult.Failed] with the node's `raw_log`, and 404/not-yet-committed →
 * [TransactionResult.Pending] so polling continues. A successful native result is trusted
 * immediately for memos Midgard does not index; for Midgard-indexed memos it is trusted only after
 * repeated empty action responses, giving Midgard's action index time to surface refunds.
 */
class ThorMayaChainStatusProvider
internal constructor(
    private val httpClient: HttpClient,
    private val nowMillis: () -> Long,
) : TransactionStatusProvider {

    @Inject constructor(httpClient: HttpClient) : this(httpClient, System::currentTimeMillis)

    private val midgardUrls =
        mapOf(
            Chain.ThorChain to THORCHAIN_MIDGARD_ACTIONS_URL,
            Chain.MayaChain to MAYACHAIN_MIDGARD_ACTIONS_URL,
        )

    private val nativeTxUrls =
        mapOf(
            Chain.ThorChain to THORCHAIN_NATIVE_TX_URL,
            Chain.MayaChain to MAYACHAIN_NATIVE_TX_URL,
        )

    private val emptyActionStreaks = ConcurrentHashMap<EmptyActionKey, EmptyActionStreak>()

    override suspend fun checkStatus(txHash: String, chain: Chain): TransactionResult {
        val baseUrl = midgardUrls[chain] ?: return TransactionResult.Failed("Unknown chain")
        return try {
            val response: MidgardActionsResponse =
                httpClient.get(baseUrl) { parameter(MIDGARD_TXID_PARAM, txHash) }.bodyOrThrow()
            val action =
                response.actions.firstOrNull()
                    ?: return checkNativeStatusAfterEmptyAction(txHash, chain)
            emptyActionStreaks.remove(EmptyActionKey(chain, txHash))
            mapAction(action)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "THOR/Maya status check failed for %s on %s", txHash, chain)
            TransactionResult.Pending
        }
    }

    /**
     * Resolve the ambiguous "no Midgard action yet" case. Native failures are terminal, native 404
     * stays pending, and native success is gated for memo types Midgard is expected to index.
     */
    private suspend fun checkNativeStatusAfterEmptyAction(
        txHash: String,
        chain: Chain,
    ): TransactionResult {
        val key = EmptyActionKey(chain, txHash)

        val nativeStatus = checkNativeStatus(txHash, chain)
        if (nativeStatus.result != TransactionResult.Confirmed) {
            if (nativeStatus.result is TransactionResult.Failed) {
                emptyActionStreaks.remove(key)
            }
            return nativeStatus.result
        }

        if (nativeStatus.hasMemo && !nativeStatus.memo.isMidgardIndexedMemo()) {
            emptyActionStreaks.remove(key)
            return TransactionResult.Confirmed
        }

        val streak =
            emptyActionStreaks.compute(key) { _, current ->
                val now = nowMillis()
                current?.copy(count = current.count + 1, lastObservedAtMillis = now)
                    ?: EmptyActionStreak(
                        count = 1,
                        firstObservedAtMillis = now,
                        lastObservedAtMillis = now,
                    )
            } ?: EmptyActionStreak(
                count = 0,
                firstObservedAtMillis = nowMillis(),
                lastObservedAtMillis = nowMillis(),
            )

        return if (
            streak.count >= MIN_INDEXABLE_EMPTY_ACTION_POLLS &&
                streak.lastObservedAtMillis - streak.firstObservedAtMillis >=
                    MIN_INDEXABLE_EMPTY_ACTION_AGE_MS
        ) {
            emptyActionStreaks.remove(key)
            TransactionResult.Confirmed
        } else {
            TransactionResult.Pending
        }
    }

    private suspend fun checkNativeStatus(txHash: String, chain: Chain): NativeStatusResult {
        val nativeTxUrl =
            nativeTxUrls[chain]
                ?: return NativeStatusResult(
                    result = TransactionResult.Pending,
                    memo = null,
                    hasMemo = false,
                )
        val response = httpClient.get("$nativeTxUrl/$txHash")
        if (response.status == HttpStatusCode.NotFound) {
            return NativeStatusResult(
                result = TransactionResult.Pending,
                memo = null,
                hasMemo = false,
            )
        }
        val envelope = response.bodyOrThrow<CosmosEnvelopedTxResponse>()
        val txResponse = envelope.txResponse
        val result =
            when (txResponse.code) {
                0 -> TransactionResult.Confirmed
                null -> TransactionResult.Pending
                else ->
                    TransactionResult.Failed(nonBlankOr(txResponse.rawLog, DEFAULT_FAILED_REASON))
            }
        val memo = envelope.tx?.body.extractMemo()
        return NativeStatusResult(result = result, memo = memo.value, hasMemo = memo.isPresent)
    }

    private fun CosmosTxBody?.extractMemo(): NativeMemo {
        if (this == null) return NativeMemo(value = null, isPresent = false)
        val msgDepositMemo =
            messages.firstNotNullOfOrNull { message ->
                message.memo.takeIf { message.type == THOR_MSG_DEPOSIT_TYPE && !it.isNullOrBlank() }
            }
        if (msgDepositMemo != null) return NativeMemo(value = msgDepositMemo, isPresent = true)
        return NativeMemo(value = memo, isPresent = memo != null)
    }

    private fun String?.isMidgardIndexedMemo(): Boolean {
        val op = this?.trim()?.takeIf { it.isNotEmpty() }?.substringBefore(":")?.uppercase()
            ?: return false
        return op in MIDGARD_INDEXED_MEMO_OPS
    }

    private fun mapAction(action: MidgardAction): TransactionResult =
        when {
            action.type == ACTION_TYPE_REFUND ->
                TransactionResult.Refunded(
                    reason = nonBlankOr(action.metadata?.refund?.reason, DEFAULT_REFUND_REASON)
                )
            action.type == ACTION_TYPE_FAILED -> {
                val failed = action.metadata?.failed
                Timber.w("Midgard reported failure: code=%s memo=%s", failed?.code, failed?.memo)
                val reason = nonBlankOr(failed?.reason, DEFAULT_FAILED_REASON)
                if (action.out.any { !it.txID.isNullOrBlank() }) TransactionResult.Refunded(reason)
                else TransactionResult.Failed(reason)
            }
            action.status == ACTION_STATUS_SUCCESS -> TransactionResult.Confirmed
            else -> TransactionResult.Pending
        }

    private fun nonBlankOr(value: String?, default: String): String =
        value?.takeUnless { it.isBlank() } ?: default

    private companion object {
        const val THORCHAIN_MIDGARD_ACTIONS_URL =
            "https://gateway.liquify.com/chain/thorchain_midgard/v2/actions"
        const val MAYACHAIN_MIDGARD_ACTIONS_URL = "https://midgard.mayachain.info/v2/actions"
        const val THORCHAIN_NATIVE_TX_URL =
            "https://gateway.liquify.com/chain/thorchain_api/cosmos/tx/v1beta1/txs"
        const val MAYACHAIN_NATIVE_TX_URL = "https://mayanode.mayachain.info/cosmos/tx/v1beta1/txs"
        const val MIDGARD_TXID_PARAM = "txid"
        const val ACTION_TYPE_REFUND = "refund"
        const val ACTION_TYPE_FAILED = "failed"
        const val ACTION_STATUS_SUCCESS = "success"
        const val DEFAULT_REFUND_REASON = "Transaction refunded"
        const val DEFAULT_FAILED_REASON = "Transaction failed"
        const val MIN_INDEXABLE_EMPTY_ACTION_POLLS = 2
        const val MIN_INDEXABLE_EMPTY_ACTION_AGE_MS = 15_000L
        const val THOR_MSG_DEPOSIT_TYPE = "/types.MsgDeposit"
        val MIDGARD_INDEXED_MEMO_OPS =
            setOf(
                "=",
                "=<",
                "SWAP",
                "S",
                "M=<",
                "+",
                "ADD",
                "A",
                "-",
                "WITHDRAW",
                "WD",
                "$+",
                "$-",
                "LOAN+",
                "LOAN-",
                "BOND",
                "UNBOND",
                "LEAVE",
                "POOL+",
                "POOL-",
            )
    }
}

private data class EmptyActionKey(val chain: Chain, val txHash: String)

private data class EmptyActionStreak(
    val count: Int,
    val firstObservedAtMillis: Long,
    val lastObservedAtMillis: Long,
)

private data class NativeStatusResult(
    val result: TransactionResult,
    val memo: String?,
    val hasMemo: Boolean,
)

private data class NativeMemo(val value: String?, val isPresent: Boolean)

@Serializable
internal data class MidgardActionsResponse(val actions: List<MidgardAction> = emptyList())

@Serializable
internal data class MidgardAction(
    @SerialName("type") val type: String? = null,
    @SerialName("status") val status: String? = null,
    @SerialName("out") val out: List<MidgardTransaction> = emptyList(),
    @SerialName("metadata") val metadata: MidgardActionMetadata? = null,
)

@Serializable internal data class MidgardTransaction(@SerialName("txID") val txID: String? = null)

@Serializable
internal data class MidgardActionMetadata(
    @SerialName("refund") val refund: MidgardRefund? = null,
    @SerialName("failed") val failed: MidgardFailedMetadata? = null,
)

@Serializable internal data class MidgardRefund(@SerialName("reason") val reason: String? = null)

@Serializable
internal data class MidgardFailedMetadata(
    @SerialName("code") val code: String? = null,
    @SerialName("memo") val memo: String? = null,
    @SerialName("reason") val reason: String? = null,
)
