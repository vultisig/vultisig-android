package com.vultisig.wallet.data.securityscanner

import com.vultisig.wallet.data.models.Chain

class BlockaidService(val blockaidRpcClient: BlockaidRpcClientContract) {

    fun scanTransaction() {

    }

    fun supportsChain(chain: Chain): Boolean {
        return chain in supportedChains
    }

    fun getSupportedChains(): List<Chain> = supportedChains

    private companion object {
        private fun Chain.toName(): String {
            return when (this) {
                Chain.Arbitrum -> "arbitrum"
                Chain.Avalanche -> "avalanche"
                Chain.Base -> "base"
                Chain.Blast -> "blast"
                Chain.BscChain -> "bsc"
                Chain.Bitcoin -> "bitcoin"
                Chain.Ethereum -> "ethereum"
                Chain.Optimism -> "optimism"
                Chain.Polygon -> "polygon"
                Chain.Sui -> "sui"
                Chain.Solana -> "solana"
                else -> error("Chain: ${this.name} not supported by Blockaid")
            }
        }
        val supportedChains = listOf(
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
    }
}