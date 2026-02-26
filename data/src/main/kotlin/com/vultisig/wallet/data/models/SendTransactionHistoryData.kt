package com.vultisig.wallet.data.models

import com.vultisig.wallet.data.db.models.TransactionHistoryEntity
import com.vultisig.wallet.data.db.models.TransactionStatus
import com.vultisig.wallet.data.db.models.TransactionType

sealed interface TransactionHistoryData

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

    val fiatValue: String?,
)

data class SendTransactionHistoryData(
    val fromAddress: String,
    val toAddress: String,
    val amount: String,
    val token: String,
    val tokenLogo: String,
    val feeEstimate: String,
    val memo: String,
) : TransactionHistoryData

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
) : TransactionHistoryData


internal fun TransactionHistoryData.toEntity(
    genericData: CommonTransactionHistoryData,
): TransactionHistoryEntity = when (this) {
    is SendTransactionHistoryData -> TransactionHistoryEntity(
        vaultId = genericData.vaultId,
        type = TransactionType.SEND,
        status = genericData.status,
        chain = genericData.chain,
        timestamp = genericData.timestamp,
        txHash = genericData.txHash,
        explorerUrl = genericData.explorerUrl,
        fiatValue = genericData.fiatValue,
        // Send fields
        fromAddress = fromAddress,
        toAddress = toAddress,
        amount = amount,
        token = token,
        tokenLogo = tokenLogo,
        feeEstimate = feeEstimate,
        memo = memo,
        // Swap fields null
        fromToken = null,
        fromAmount = null,
        fromChain = null,
        fromTokenLogo = null,
        toToken = null,
        toAmount = null,
        toChain = null,
        toTokenLogo = null,
        provider = null,
        route = null,
        // Status metadata
        confirmedAt = genericData.confirmedAt,
        failureReason = genericData.failureReason,
        lastCheckedAt = genericData.lastCheckedAt,
    )

    is SwapTransactionHistoryData -> TransactionHistoryEntity(
        vaultId = genericData.vaultId,
        type = TransactionType.SWAP,
        status = genericData.status,
        chain = genericData.chain,
        timestamp = genericData.timestamp,
        txHash = genericData.txHash,
        explorerUrl = genericData.explorerUrl,
        fiatValue = genericData.fiatValue,
        // Send fields null
        fromAddress = null,
        toAddress = null,
        amount = null,
        token = null,
        tokenLogo = null,
        feeEstimate = null,
        memo = null,
        // Swap fields
        fromToken = fromToken,
        fromAmount = fromAmount,
        fromChain = fromChain,
        fromTokenLogo = fromTokenLogo,
        toToken = toToken,
        toAmount = toAmount,
        toChain = toChain,
        toTokenLogo = toTokenLogo,
        provider = provider,
        route = null,
        // Status metadata
        confirmedAt = genericData.confirmedAt,
        failureReason = genericData.failureReason,
        lastCheckedAt = genericData.lastCheckedAt,
    )
}


