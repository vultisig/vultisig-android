package com.vultisig.wallet.data.blockchain

import com.vultisig.wallet.data.models.Coin
import java.math.BigInteger

sealed interface BlockchainTransaction {
    val coin: Coin
    val vault: VaultData
    val amount: BigInteger
    val isMax: Boolean
}

data class Transfer(
    override val coin: Coin,
    override val vault: VaultData,
    override val isMax: Boolean = false,
    override val amount: BigInteger,
    val to: String,
    val memo: String? = null,
) : BlockchainTransaction

data class Swap(
    override val coin: Coin,
    override val vault: VaultData,
    override val isMax: Boolean = false,
    override val amount: BigInteger,
    val to: String,
    val callData: String,
    val approvalData: String?,
    val limit: BigInteger = BigInteger.ZERO,
): BlockchainTransaction

data class VaultData(
    val vaultHexPublicKey: String,
    val vaultHexChainCode: String,
)