package com.vultisig.wallet.data.blockchain.bitcoin

import com.vultisig.wallet.data.api.BlockChairApi
import com.vultisig.wallet.data.blockchain.BlockchainTransaction
import com.vultisig.wallet.data.blockchain.Fee
import com.vultisig.wallet.data.blockchain.FeeService
import com.vultisig.wallet.data.blockchain.Transfer
import com.vultisig.wallet.data.chains.helpers.UtxoHelper
import javax.inject.Inject

class UtxoFeeServizce @Inject constructor(
    private val utxoApi: BlockChairApi
): FeeService  {
    override suspend fun calculateFees(transaction: BlockchainTransaction): Fee {
        require(transaction is Transfer) {
            "Transaction type not supported: ${transaction::class.simpleName}"
        }
        val address = transaction.coin.address
        val chain = transaction.coin.chain
        val coinType = transaction.coin.coinType
        val vaultHexPublicKey = transaction.vault.vaultHexPublicKey
        val vaultHexChainCode = transaction.vault.vaultHexChainCode

        val helper = UtxoHelper(coinType, vaultHexPublicKey, vaultHexChainCode)

        val blockchairInfo = utxoApi.getAddressInfo(chain, address) ?: error("Can't fetch address Info")

        error("")
    }

    override suspend fun calculateDefaultFees(transaction: BlockchainTransaction): Fee {
        TODO("Not yet implemented")
    }
}