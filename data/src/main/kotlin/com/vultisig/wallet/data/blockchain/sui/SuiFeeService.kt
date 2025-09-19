package com.vultisig.wallet.data.blockchain.sui

import com.vultisig.wallet.data.api.chains.SuiApi
import com.vultisig.wallet.data.blockchain.BasicFee
import com.vultisig.wallet.data.blockchain.BlockchainTransaction
import com.vultisig.wallet.data.blockchain.Fee
import com.vultisig.wallet.data.blockchain.FeeService
import com.vultisig.wallet.data.blockchain.Transfer
import com.vultisig.wallet.data.crypto.SuiHelper
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.Transaction
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.utils.increaseByPercent
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import vultisig.keysign.v1.SuiCoin
import java.math.BigInteger
import kotlin.collections.first

/**
 * Service that estimates and prepares gas payment details for Sui transactions.
 *
 * Implementation notes:
 *  - Use an RPC dry-run / dev-inspect endpoint to obtain computationUnits and storage effects.
 *  - Query the network reference gas price (an epoch-level value) from the node or SDK.
 *  - Compute: total = computationUnits * referenceGasPrice + storageUnits * storagePricePerUnit.
 *
 * Note:
 *  - Estimates are approximate as much as possible; always allow a small margin in the gas budget.
 */
class SuiFeeService(
    private val suiApi: SuiApi,
): FeeService {
    override suspend fun calculateFees(transaction: BlockchainTransaction): Fee = coroutineScope {
        require(transaction is Transfer) {
            "Invalid Transfer type: ${transaction::class.simpleName}"
        }
        val fromAddress = transaction.coin.address

        val referenceGasPriceDeferred = async { suiApi.getReferenceGasPrice() }
        val allCoinsDeferred = async { suiApi.getAllCoins(fromAddress) }

        val keySignPayload =
            buildKeySignPayload(transaction, referenceGasPriceDeferred, allCoinsDeferred)

        val txSerialized = SuiHelper.getZeroSignedTransaction(keySignPayload)

        val dryRunResult = suiApi.dryRunTransaction(txSerialized)

        val gasUsed = dryRunResult.effects.gasUsed
        val totalGasUsed = gasUsed.computationCost.toBigInteger() + gasUsed.storageCost.toBigInteger()
        val coinOverhead = GAS_OVERHEAD * referenceGasPriceDeferred.await()

        BasicFee(amount = totalGasUsed + coinOverhead)
    }

    private suspend fun buildKeySignPayload(
        transaction: Transfer,
        referenceGasPriceDeferred: Deferred<BigInteger>,
        allCoinsDeferred: Deferred<List<SuiCoin>>
    ): KeysignPayload {
        val coin = transaction.coin
        val toAddress = transaction.to
        val toAmount = transaction.amount

        return KeysignPayload(
            coin = coin,
            toAddress = toAddress,
            toAmount = toAmount,
            blockChainSpecific = BlockChainSpecific.Sui(
                referenceGasPrice = referenceGasPriceDeferred.await(),
                coins = allCoinsDeferred.await(),
            ),
            vaultPublicKeyECDSA = "",
            vaultLocalPartyID = "",
            libType = null,
            wasmExecuteContractPayload = null,
        )
    }

    override suspend fun calculateDefaultFees(transaction: BlockchainTransaction): Fee {
        val gasPrice = suiApi.getReferenceGasPrice()

        val estimatedFees = (BASELINE_COMPUTATION_COIN_TRANSFER * gasPrice) + BASELINE_STORAGE
        val feesWithBuffer = estimatedFees.increaseByPercent(20)

        return BasicFee(amount = feesWithBuffer)
    }

    private companion object {
        // Default Limit for
        val BASELINE_COMPUTATION_COIN_TRANSFER = "1300".toBigInteger()
        // Baseline storage cost in MIST
        val BASELINE_STORAGE = "50".toBigInteger()
        // GasOverhead
        val GAS_OVERHEAD = 150.toBigInteger() // 10 coins with extra overhead per coin
    }
}