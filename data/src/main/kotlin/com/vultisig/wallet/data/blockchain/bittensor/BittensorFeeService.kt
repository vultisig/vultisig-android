package com.vultisig.wallet.data.blockchain.bittensor

import com.vultisig.wallet.data.api.BittensorApi
import com.vultisig.wallet.data.blockchain.FeeService
import com.vultisig.wallet.data.blockchain.model.BasicFee
import com.vultisig.wallet.data.blockchain.model.BlockchainTransaction
import com.vultisig.wallet.data.blockchain.model.Fee
import com.vultisig.wallet.data.blockchain.model.Swap
import com.vultisig.wallet.data.blockchain.model.Transfer
import com.vultisig.wallet.data.chains.helpers.BittensorHelper
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import java.math.BigInteger
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

internal class BittensorFeeService @Inject constructor(private val bittensorApi: BittensorApi) :
    FeeService {
    override suspend fun calculateFees(transaction: BlockchainTransaction): Fee {
        // Bittensor extrinsic build is Transfer-shaped. A Swap carries opaque callData so fall back
        // to the constant estimate — TAO has no live swap provider today.
        if (transaction is Swap) {
            return calculateDefaultFees(transaction)
        }
        require(transaction is Transfer) {
            "Invalid Transaction type: ${transaction::class.simpleName}"
        }

        val fromAddress = transaction.coin.address
        val toAddress = transaction.to
        val valHexPublicKey = transaction.vault.vaultHexPublicKey
        val amount = transaction.amount

        val keySignPayload =
            buildBittensorSpecific(
                fromAddress = fromAddress,
                toAddress = toAddress,
                amount = amount,
            )

        val serializedTransaction =
            BittensorHelper(valHexPublicKey).getZeroSignedTransaction(keySignPayload)

        val partialFee = bittensorApi.getPartialFee(serializedTransaction)

        return BasicFee(partialFee)
    }

    private suspend fun buildBittensorSpecific(
        fromAddress: String,
        toAddress: String,
        amount: BigInteger,
    ): KeysignPayload = coroutineScope {
        val bittensorCoin =
            Coins.coins[Chain.Bittensor]?.first { it.isNativeToken }
                ?: error("Bittensor Coin not found")

        val runtimeVersionDeferred = async { bittensorApi.getRuntimeVersion() }
        val nonceDeferred = async { bittensorApi.getNonce(fromAddress) }
        val blockNumberDeferred = async { bittensorApi.getBlockHeader() }
        val genesisHashDeferred = async { bittensorApi.getGenesisBlockHash() }

        val (specVersion, transactionVersion) = runtimeVersionDeferred.await()
        val blockNumber = blockNumberDeferred.await()
        val blockHash = bittensorApi.getBlockHashForNumber(blockNumber)

        KeysignPayload(
            coin = bittensorCoin,
            toAddress = toAddress,
            toAmount = amount,
            blockChainSpecific =
                BlockChainSpecific.Polkadot(
                    recentBlockHash = blockHash,
                    nonce = nonceDeferred.await(),
                    currentBlockNumber = blockNumber,
                    specVersion = specVersion.toLong().toUInt(),
                    transactionVersion = transactionVersion.toLong().toUInt(),
                    genesisHash = genesisHashDeferred.await(),
                    gas = BITTENSOR_DEFAULT_FEE.toString().toULong(),
                ),
            vaultPublicKeyECDSA = "",
            vaultLocalPartyID = "",
            libType = null,
            wasmExecuteContractPayload = null,
        )
    }

    override suspend fun calculateDefaultFees(transaction: BlockchainTransaction): Fee {
        return BasicFee(BITTENSOR_DEFAULT_FEE)
    }

    private companion object {
        val BITTENSOR_DEFAULT_FEE = BittensorHelper.DEFAULT_FEE_RAO.toBigInteger()
    }
}
