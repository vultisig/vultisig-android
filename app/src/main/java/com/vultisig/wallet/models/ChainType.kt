package com.vultisig.wallet.models

internal enum class ChainType(description: String) {
    UTXO("Unspent Transaction Output"),
    EVM("Ethereum Virtual Machine"),
    Solana("Solana"),
    Sui("Sui"),
    THORChain("THORChain"),
    Cosmos("Cosmos"),
    Polkadot("Polkadot"),
}