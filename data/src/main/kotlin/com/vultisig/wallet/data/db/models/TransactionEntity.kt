package com.vultisig.wallet.data.db.models

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.vultisig.wallet.data.usecases.txstatus.TransactionResult
import java.util.UUID

enum class TransactionType {
    SEND,
    SWAP
}

// TransactionStatus.kt
enum class TransactionStatus {
    BROADCASTED,
    PENDING,
    CONFIRMED,
    FAILED
}

fun TransactionResult.toDbModel() = when(this){
    TransactionResult.Confirmed -> TransactionStatus.CONFIRMED
    is TransactionResult.Failed -> TransactionStatus.FAILED
    TransactionResult.NotFound -> TransactionStatus.FAILED
    TransactionResult.Pending -> TransactionStatus.PENDING
}

@Entity(
    tableName = "transaction_history",
    foreignKeys = [
        ForeignKey(
            entity = VaultEntity::class,
            parentColumns = ["id"],
            childColumns = ["vaultId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("vaultId"),
        Index("txHash", unique = true),
        Index("status"),
        Index("type"),
        Index("chain"),
        Index("timestamp")
    ]
)
data class TransactionHistoryEntity(
    @PrimaryKey
    @ColumnInfo("id")
    val id: String = UUID.randomUUID().toString(),

    // Common fields
    @ColumnInfo("vaultId")
    val vaultId: String,

    @ColumnInfo("type")
    val type: TransactionType,

    @ColumnInfo("status")
    val status: TransactionStatus,

    @ColumnInfo("chain")
    val chain: String,

    @ColumnInfo("timestamp")
    val timestamp: Long,

    @ColumnInfo("txHash")
    val txHash: String,

    @ColumnInfo("explorerUrl")
    val explorerUrl: String,

    @ColumnInfo("fiatValue")
    val fiatValue: String?,

    // Send-specific fields
    @ColumnInfo("fromAddress")
    val fromAddress: String?,

    @ColumnInfo("toAddress")
    val toAddress: String?,

    @ColumnInfo("amount")
    val amount: String?,

    @ColumnInfo("token")
    val token: String?,

    @ColumnInfo("tokenLogo")
    val tokenLogo: String?,

    @ColumnInfo("feeEstimate")
    val feeEstimate: String?,

    @ColumnInfo("memo")
    val memo: String?,

    // Swap-specific fields
    @ColumnInfo("fromToken")
    val fromToken: String?,

    @ColumnInfo("fromAmount")
    val fromAmount: String?,

    @ColumnInfo("fromChain")
    val fromChain: String?,

    @ColumnInfo("fromTokenLogo")
    val fromTokenLogo: String?,

    @ColumnInfo("toToken")
    val toToken: String?,

    @ColumnInfo("toAmount")
    val toAmount: String?,

    @ColumnInfo("toChain")
    val toChain: String?,

    @ColumnInfo("toTokenLogo")
    val toTokenLogo: String?,

    @ColumnInfo("provider")
    val provider: String?,

    @ColumnInfo("route")
    val route: String?,

    // Status metadata
    @ColumnInfo("confirmedAt")
    val confirmedAt: Long?,

    @ColumnInfo("failureReason")
    val failureReason: String?,

    @ColumnInfo("lastCheckedAt")
    val lastCheckedAt: Long?
)