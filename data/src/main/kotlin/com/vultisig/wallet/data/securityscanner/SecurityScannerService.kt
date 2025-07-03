package com.vultisig.wallet.data.securityscanner

import com.vultisig.wallet.data.models.Chain
import timber.log.Timber

class SecurityScannerService(private val blockaidRpcClient: BlockaidRpcClientContract) : SecurityScannerServiceContract {

    override suspend fun scanTransaction(transaction: SecurityScannerTransaction) {
        when (val chain = transaction.chain) {
            Chain.Arbitrum,
            Chain.Avalanche,
            Chain.Base,
            Chain.Blast,
            Chain.BscChain,
            Chain.Ethereum,
            Chain.Optimism,
            Chain.Polygon -> scanEvmTransaction(transaction)

            Chain.Bitcoin -> scanBitcoinTransaction(transaction)
            Chain.Solana -> scanSolanaTransaction(transaction)
            Chain.Sui -> scanSuiTransaction(transaction)
            else -> throw UnsupportedOperationException("${chain.name} is not supported")
        }
    }

    private suspend fun scanEvmTransaction(transaction: SecurityScannerTransaction) {
        Timber.d("Scanning ${transaction.chain.name} transaction: $transaction")

        val scanResult = blockaidRpcClient.scanEVMTransaction(
            chain = transaction.chain,
            from = transaction.from,
            to = transaction.to,
            amount = "0x${transaction.amount.toString(16)}", // review
            data = transaction.data,
        )
    }

    private suspend fun scanBitcoinTransaction(transaction: SecurityScannerTransaction) {
        Timber.d("Scanning ${transaction.chain.name} transaction: $transaction")

        val scanResult = blockaidRpcClient.scanBitcoinTransaction(
            address = transaction.from,
            serializedTransaction = transaction.data,
        )
    }

    private suspend fun scanSolanaTransaction(transaction: SecurityScannerTransaction) {
        Timber.d("Scanning ${transaction.chain.name} transaction: $transaction")

        val scanResult = blockaidRpcClient.scanSolanaTransaction(
            address = transaction.from,
            serializedMessage = transaction.data,
        )
    }

    private suspend fun scanSuiTransaction(transaction: SecurityScannerTransaction) {
        Timber.d("Scanning ${transaction.chain.name} transaction: $transaction")

        val scanResult = blockaidRpcClient.scanSuiTransaction(
            address = transaction.from,
            serializedTransaction = transaction.data,
        )
    }

    override fun getProviderName(): String {
        return PROVIDER_NAME
    }

    override fun supportsChain(chain: Chain): Boolean {
        return chain in supportedChains
    }

    override fun getSupportedChains(): List<Chain> = supportedChains

    private companion object {
        private val supportedChains = listOf(
            Chain.Arbitrum,
            Chain.Avalanche,
            Chain.Base,
            Chain.Blast,
            Chain.BscChain,
            Chain.Bitcoin,
            Chain.Ethereum,
            Chain.Optimism,
            Chain.Polygon,
            Chain.Sui,
            Chain.Solana,
        )

        private val PROVIDER_NAME = "blockaid"
    }
}