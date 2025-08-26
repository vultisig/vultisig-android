package com.vultisig.wallet.data.securityscanner.blockaid

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.securityscanner.ProviderScannerServiceContract
import com.vultisig.wallet.data.securityscanner.SecurityScannerFeaturesType
import com.vultisig.wallet.data.securityscanner.SecurityScannerResult
import com.vultisig.wallet.data.securityscanner.SecurityScannerTransaction
import com.vultisig.wallet.data.securityscanner.runSecurityScan
import com.vultisig.wallet.data.utils.toHexString

class BlockaidScannerService(private val blockaidRpcClient: BlockaidRpcClientContract) :
    ProviderScannerServiceContract {

    override suspend fun scanTransaction(transaction: SecurityScannerTransaction) =
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

    private suspend fun scanEvmTransaction(transaction: SecurityScannerTransaction): SecurityScannerResult {
        return runSecurityScan(transaction) {
            blockaidRpcClient.scanEVMTransaction(
                chain = transaction.chain,
                from = transaction.from,
                to = transaction.to,
                amount = transaction.amount.toHexString(),
                data = transaction.data,
            ).toSecurityScannerResult(PROVIDER_NAME)
        }
    }

    private suspend fun scanBitcoinTransaction(transaction: SecurityScannerTransaction): SecurityScannerResult {
        return runSecurityScan(transaction) {
            blockaidRpcClient.scanBitcoinTransaction(
                address = transaction.from,
                serializedTransaction = transaction.data,
            ).toSecurityScannerResult(PROVIDER_NAME)
        }
    }

    private suspend fun scanSolanaTransaction(transaction: SecurityScannerTransaction): SecurityScannerResult {
        return runSecurityScan(transaction) {
            blockaidRpcClient.scanSolanaTransaction(
                address = transaction.from,
                serializedMessage = transaction.data,
            ).toSolanaSecurityScannerResult(PROVIDER_NAME)
        }
    }

    private suspend fun scanSuiTransaction(transaction: SecurityScannerTransaction): SecurityScannerResult {
        return runSecurityScan(transaction) {
            blockaidRpcClient.scanSuiTransaction(
                address = transaction.from,
                serializedTransaction = transaction.data,
            ).toSecurityScannerResult(PROVIDER_NAME)
        }
    }

    override fun getProviderName(): String {
        return PROVIDER_NAME
    }

    override fun supportsChain(chain: Chain, feature: SecurityScannerFeaturesType): Boolean {
        val supportedChainsByFeature = getSupportedChains()[feature] ?: return false

        return supportedChainsByFeature.any { it.raw == chain.raw }
    }

    override fun getSupportedChains(): Map<SecurityScannerFeaturesType, List<Chain>> {
        return mapOf(
            SecurityScannerFeaturesType.SCAN_TRANSACTION to supportedChains,
        )
    }

    override fun getSupportedFeatures(): List<SecurityScannerFeaturesType> {
        return listOf(SecurityScannerFeaturesType.SCAN_TRANSACTION)
    }

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