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