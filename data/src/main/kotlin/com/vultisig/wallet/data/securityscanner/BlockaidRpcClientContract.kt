package com.vultisig.wallet.data.securityscanner

interface BlockaidRpcClientContract {
    fun scanBitcoinTransaction(serializedTransaction: String)
    fun scanEVMTransaction(from: String, to: String, amount: String, data: String)
    fun scanSolanaTransaction(serializedMessage: String)
    fun scanSuiTransaction(serializedTransaction: String)
}