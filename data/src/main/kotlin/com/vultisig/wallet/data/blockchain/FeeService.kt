package com.vultisig.wallet.data.blockchain

import com.vultisig.wallet.data.models.Chain
import java.math.BigInteger

interface FeeService {
    suspend fun calculateFees(chain: Chain, limit: BigInteger, isSwap: Boolean, to: String? = null): Fee =
        throw NotImplementedError("Not implemented")

    suspend fun calculateFees(transaction: BlockchainTransaction): Fee

    suspend fun calculateDefaultFees(transaction: BlockchainTransaction): Fee
}

val supportsFeeService = setOf(
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
    // Utxo
    // Chain.Bitcoin,
    // Chain.BitcoinCash,
    // Chain.Litecoin,
    // Chain.Dogecoin,
    // Chain.Dash,
    // Chain.Zcash
)
