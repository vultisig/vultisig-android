package com.vultisig.wallet.data.securityscanner.blockaid

import com.vultisig.wallet.data.models.Chain

interface BlockaidRpcClientContract {
    suspend fun scanBitcoinTransaction(
        address: String,
        serializedTransaction: String
    ): BlockaidTransactionScanResponseJson

    suspend fun scanEVMTransaction(
        chain: Chain,
        from: String,
        to: String,
        amount: String,
        data: String
    ): BlockaidTransactionScanResponseJson

    suspend fun scanSolanaTransaction(
        address: String,
        serializedMessage: String
    ): BlockaidTransactionScanResponseJson

    suspend fun scanSuiTransaction(
        address: String,
        serializedTransaction: String
    ): BlockaidTransactionScanResponseJson
}