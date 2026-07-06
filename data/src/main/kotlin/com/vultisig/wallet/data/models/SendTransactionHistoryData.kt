package com.vultisig.wallet.data.models

import com.vultisig.wallet.data.db.models.TransactionHistoryEntity
import com.vultisig.wallet.data.db.models.TransactionStatus
import com.vultisig.wallet.data.db.models.TransactionType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable sealed interface TransactionHistoryData

/**
 * Runtime-only wrapper for payloads the app cannot deserialize. Never serialized by Room —
 * [TransactionHistoryDataConverter.toJson] writes [rawPayload] directly.
 */
data class UnknownTransactionHistoryData(val rawPayload: String = "") : TransactionHistoryData

data class CommonTransactionHistoryData(
    val confirmedAt: Long?,
    val failureReason: String?,
    val lastCheckedAt: Long?,
    val vaultId: String,
    val type: TransactionType,
    val status: TransactionStatus,
    val chain: String,
    val timestamp: Long,
    val txHash: String,
    val explorerUrl: String,
    /**
     * Chain head block number at broadcast; see [TransactionHistoryEntity.broadcastBlockNumber].
     */
    val broadcastBlockNumber: Long? = null,
)

@Serializable
@SerialName("send")
data class SendTransactionHistoryData(
    val fromAddress: String,
    val toAddress: String,
    val amount: String,
    val token: String,
    val tokenLogo: String,
    val feeEstimate: String,
    val memo: String,
    val fiatValue: String,
) : TransactionHistoryData

@Serializable
@SerialName("swap")
data class SwapTransactionHistoryData(
    val fromToken: String,
    val fromAmount: String,
    val fromChain: String,
    val fromTokenLogo: String,
    val toToken: String,
    val toAmount: String,
    val toChain: String,
    val toTokenLogo: String,
    val provider: String,
    val fiatValue: String,
    /**
     * SwapKit `/v3/swap` swap id, persisted as the `/track` correlation key so a cross-chain
     * SwapKit swap's Success is gated on the destination-leg settlement rather than the
     * source-chain deposit (see
     * [com.vultisig.wallet.data.usecases.RefreshPendingTransactionsUseCase]). Null for non-SwapKit
     * providers and for legacy rows recorded before this field existed (serialized as JSON into the
     * `payload` column, so a default keeps old rows readable — no Room migration).
     */
    val swapId: String? = null,
    /**
     * Destination token's contract (jetton master) address in registry (`EQ…`) form, or empty for a
     * native destination. Persisted so a same-chain TON (Omniston) swap — whose `/track` response
     * only ever describes the source deposit leg — can be resolved on-chain: a filled quote appears
     * as an incoming transfer of this master. Default-valued so legacy rows stay readable (no Room
     * migration). See [com.vultisig.wallet.data.api.txstatus.SwapKitTrackingService].
     */
    val toContractAddress: String = "",
    /** Whether the destination token is the chain's native coin; see [toContractAddress]. */
    val toIsNative: Boolean = false,
    /**
     * Source token's on-chain address (the vault's own address on the source chain), used as the
     * `owner_address` when resolving a TON (Omniston) swap's settlement on-chain. Default-valued so
     * legacy rows stay readable. See [toContractAddress].
     */
    val fromAddress: String = "",
    /**
     * Expected destination output as a raw, machine-parseable plain decimal (e.g. `12.5`), distinct
     * from the display-formatted [toAmount] (which carries grouping separators, `M`/`B` suffixes
     * and locale decimal symbols, so it is not reliably parseable). Used as the native-destination
     * fill threshold when resolving a TON (Omniston) swap on-chain. Default-valued so legacy rows
     * stay readable. See [com.vultisig.wallet.data.api.txstatus.SwapKitTrackingService].
     */
    val toAmountDecimal: String = "",
) : TransactionHistoryData

internal fun TransactionHistoryData.toEntity(
    genericData: CommonTransactionHistoryData
): TransactionHistoryEntity =
    TransactionHistoryEntity(
        id = buildTransactionHistoryId(genericData.chain, genericData.txHash),
        vaultId = genericData.vaultId,
        type = genericData.type,
        status = genericData.status,
        chain = genericData.chain,
        timestamp = genericData.timestamp,
        txHash = genericData.txHash,
        explorerUrl = genericData.explorerUrl,
        confirmedAt = genericData.confirmedAt,
        failureReason = genericData.failureReason,
        lastCheckedAt = genericData.lastCheckedAt,
        broadcastBlockNumber = genericData.broadcastBlockNumber,
        payload = this,
    )

/** Deterministic id `"$chain:$txHash"`. The separator must not appear in either component. */
internal fun buildTransactionHistoryId(chain: String, txHash: String): String {
    require(chain.isNotEmpty()) { "chain must not be empty" }
    require(txHash.isNotEmpty()) { "txHash must not be empty" }
    require(':' !in chain) { "chain must not contain ':' — got '$chain'" }
    require(':' !in txHash) { "txHash must not contain ':' — got '$txHash'" }
    return "$chain:$txHash"
}
