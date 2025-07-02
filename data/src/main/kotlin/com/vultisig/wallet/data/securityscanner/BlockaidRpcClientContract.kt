package com.vultisig.wallet.data.securityscanner

interface BlockaidRpcClientContract {
    suspend fun scanBitcoinTransaction(serializedTransaction: String)
    suspend fun scanEVMTransaction(from: String, to: String, amount: String, data: String)
    suspend fun scanSolanaTransaction(serializedMessage: String)
    suspend fun scanSuiTransaction(serializedTransaction: String)
}