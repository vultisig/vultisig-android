package com.vultisig.wallet.data.blockchain

import com.vultisig.wallet.data.blockchain.model.BlockchainTransaction
import com.vultisig.wallet.data.blockchain.model.Fee
import com.vultisig.wallet.data.models.Chain
import java.math.BigInteger

interface FeeService {
    @Deprecated("use calculateFees(transaction)")
    suspend fun calculateFees(chain: Chain, limit: BigInteger, isSwap: Boolean, to: String? = null): Fee =
        throw NotImplementedError("Not implemented")

    suspend fun calculateFees(transaction: BlockchainTransaction): Fee

    suspend fun calculateDefaultFees(transaction: BlockchainTransaction): Fee
}

internal val supportsFeeService = setOf(
    // Evm
    Chain.Arbitrum,
    Chain.Avalanche,
    Chain.Base,
    Chain.CronosChain,
    Chain.BscChain,
    Chain.Blast,
    Chain.Ethereum,
    Chain.Optimism,
    Chain.Polygon,
    Chain.ZkSync,
    Chain.Mantle,
    // Non EVM
    Chain.Polkadot,
    Chain.Ripple,
    Chain.Ton,
    Chain.Sui,
    Chain.Tron,
    Chain.Solana,
)
