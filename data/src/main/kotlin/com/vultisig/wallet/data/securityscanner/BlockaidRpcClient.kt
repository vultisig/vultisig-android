package com.vultisig.wallet.data.securityscanner

class BlockaidRpcClient: BlockaidRpcClientContract {
    override fun scanBitcoinTransaction(serializedTransaction: String) {
        TODO("Not yet implemented")
    }

    override fun scanEVMTransaction(from: String, to: String, amount: String, data: String) {
        TODO("Not yet implemented")
    }

    override fun scanSolanaTransaction(serializedMessage: String) {
        TODO("Not yet implemented")
    }

    override fun scanSuiTransaction(serializedTransaction: String) {
        TODO("Not yet implemented")
    }
}