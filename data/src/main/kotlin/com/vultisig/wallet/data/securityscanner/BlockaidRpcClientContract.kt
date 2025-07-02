package com.vultisig.wallet.data.securityscanner

import com.vultisig.wallet.data.models.Chain

interface BlockaidRpcClientContract {
    suspend fun scanBitcoinTransaction(address: String, serializedTransaction: String)
    suspend fun scanEVMTransaction(chain: Chain, from: String, to: String, amount: String, data: String)
    suspend fun scanSolanaTransaction(address: String, serializedMessage: String)
    suspend fun scanSuiTransaction(address: String, serializedTransaction: String)
}