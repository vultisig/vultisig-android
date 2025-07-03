package com.vultisig.wallet.data.securityscanner

import com.vultisig.wallet.data.models.Chain

interface BlockaidRpcClientContract {
    suspend fun scanBitcoinTransaction(
        address: String,
        serializedTransaction: String
    ): BlockaidTransactionScanResponse

    suspend fun scanEVMTransaction(
        chain: Chain,
        from: String,
        to: String,
        amount: String,
        data: String
    ): BlockaidTransactionScanResponse

    suspend fun scanSolanaTransaction(
        address: String,
        serializedMessage: String
    ): BlockaidTransactionScanResponse

    suspend fun scanSuiTransaction(
        address: String,
        serializedTransaction: String
    ): BlockaidTransactionScanResponse
}