package com.vultisig.wallet.data.blockchain.polkadot

import com.vultisig.wallet.data.api.PolkadotApi
import com.vultisig.wallet.data.blockchain.BasicFee
import com.vultisig.wallet.data.blockchain.Fee
import com.vultisig.wallet.data.blockchain.FeeService
import com.vultisig.wallet.data.blockchain.BlockchainTransaction
import com.vultisig.wallet.data.blockchain.Transfer
import com.vultisig.wallet.data.chains.helpers.PolkadotHelper
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.math.BigInteger

/**
 * Fee service for the Polkadot blockchain.
 *
 * Polkadot transaction fees are calculated using a weight-based fee model:
 *   - Base fee: fixed minimum charged for every transaction
 *   - Weight fee: proportional to the transaction's computational cost
 *   - Length fee: proportional to the transaction's size in bytes
 *   - Tip: optional, paid by user to prioritize transaction inclusion
 *
 * Formula (simplified):
 *   final_fee = base_fee + (weight * coeff) + (length * coeff) + tip
 *
 * In this implementation:
 *   - We prepare a valid Polkadot transaction payload.
 *   - We can call `queryInfo` on the payload to get the chain-calculated dynamic fee.
 *   - Get the real fee from partialFee (RPC Endpoint already calculate everything for us)
 *
 * https://docs.polkadot.com/polkadot-protocol/parachain-basics/blocks-transactions-fees/fees/
 */
class PolkadotFeeService(
    private val polkadotApi: PolkadotApi,
): FeeService {
    override suspend fun calculateFees(
        transaction: BlockchainTransaction,
    ): Fee {
        require(transaction is Transfer) {
            "Invalid Transaction type: ${transaction::class.simpleName}"
        }

        val fromAddress = transaction.coin.address
        val toAddress = transaction.to
        val valHexPublicKey = transaction.vault.vaultHexPublicKey
        val amount = transaction.amount

        val keySignPayload = buildPolkadotSpecific(
            fromAddress = fromAddress,
            toAddress = toAddress,
            amount = amount,
        )

        val serializedTransaction =
            PolkadotHelper(valHexPublicKey).getZeroSignedTransaction(keySignPayload)

        val partialFee = polkadotApi.getPartialFee(serializedTransaction)

        return BasicFee(partialFee)
    }

    private suspend fun buildPolkadotSpecific(
        fromAddress: String,
        toAddress:String,
        amount: BigInteger,
    ): KeysignPayload = coroutineScope {
        val polkadotCoin = Coins.coins[Chain.Polkadot]
            ?.first { it.isNativeToken }
            ?: error("Polkadot Coin not found")

        val runtimeVersionDeferred = async { polkadotApi.getRuntimeVersion() }
        val blockHashDeferred = async { polkadotApi.getBlockHash() }
        val nonceDeferred = async { polkadotApi.getNonce(fromAddress) }
        val blockHeaderDeferred = async { polkadotApi.getBlockHeader() }
        val genesisHashDeferred = async { polkadotApi.getGenesisBlockHash() }

        val (specVersion, transactionVersion) = runtimeVersionDeferred.await()

        KeysignPayload(
            coin = polkadotCoin,
            toAddress = toAddress,
            toAmount = amount,
            blockChainSpecific = BlockChainSpecific.Polkadot(
                recentBlockHash = blockHashDeferred.await(),
                nonce = nonceDeferred.await(),
                currentBlockNumber = blockHeaderDeferred.await(),
                specVersion = specVersion.toLong().toUInt(),
                transactionVersion = transactionVersion.toLong().toUInt(),
                genesisHash = genesisHashDeferred.await()
            ),
            vaultPublicKeyECDSA = "",
            vaultLocalPartyID = "",
            libType = null,
            wasmExecuteContractPayload = null,
        )
    }

    override suspend fun calculateDefaultFees(transaction: BlockchainTransaction): Fee {
        return BasicFee(POLKADOT_DEFAULT_FEE)
    }

    private companion object {
        val POLKADOT_DEFAULT_FEE = "250000000".toBigInteger()
    }
}