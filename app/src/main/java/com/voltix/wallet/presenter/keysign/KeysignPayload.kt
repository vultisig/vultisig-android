package com.voltix.wallet.presenter.keysign

import com.voltix.wallet.chains.UtxoInfo
import com.voltix.wallet.models.Coin
import com.voltix.wallet.models.ERC20ApprovePayload
import com.voltix.wallet.models.THORChainSwapPayload
import com.voltix.wallet.models.Vault
import java.math.BigInteger

sealed class BlockChainSpecific {
    data class UTXO(val byteFee: BigInteger, val utxoes: List<UtxoInfo>) : BlockChainSpecific()
    data class Ethereum(
        val maxFeePerGasWei: BigInteger,
        val priorityFeeWei: BigInteger,
        val nonce: Long,
        val gasLimit: BigInteger,
    ) : BlockChainSpecific()

    data class THORChain(val accountNumber: BigInteger, val sequence: BigInteger) :
        BlockChainSpecific()

    data class Cosmos(
        val accountNumber: BigInteger,
        val sequence: BigInteger,
        val gas: BigInteger,
    ) :
        BlockChainSpecific()

    data class Solana(val recentBlockHash: String, val priorityFee: BigInteger) :
        BlockChainSpecific()

    data class Sui(val referenceGasPrice: BigInteger, val coins: List<Map<String, String>>) :
        BlockChainSpecific()

    data class Polkadot(
        val recentBlockHash: String,
        val nonce: BigInteger,
        val currentBlockNumber: BigInteger,
        val specVersion: UInt,
        val transactionVersion: UInt,
        val genesisHash: String,
    ) : BlockChainSpecific()
}

data class KeysignPayload(
    val coin: Coin,
    val toAddress: String,
    val toAmount: BigInteger,
    val blockChainSpecific: BlockChainSpecific,
    val memo: String?,
    val swapPayload: THORChainSwapPayload?,
    val approvePayload: ERC20ApprovePayload?,
    val vaultPublicKeyECDSA: String,
) {

    fun getKeysignMessages(vault: Vault): List<String> {
        throw Exception("Not implemented")
    }

}