package com.vultisig.wallet.data.blockchain.sui

import com.vultisig.wallet.data.api.chains.SuiApi
import com.vultisig.wallet.data.blockchain.BasicFee
import com.vultisig.wallet.data.blockchain.BlockchainTransaction
import com.vultisig.wallet.data.blockchain.Fee
import com.vultisig.wallet.data.blockchain.FeeService
import com.vultisig.wallet.data.blockchain.GasFees
import com.vultisig.wallet.data.blockchain.Transfer
import com.vultisig.wallet.data.crypto.SuiHelper
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.utils.increaseByPercent
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import vultisig.keysign.v1.SuiCoin
import java.math.BigInteger

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
 *  - Docs: https://docs.sui.io/concepts/tokenomics/gas-in-sui
 */
class SuiFeeService(
    private val suiApi: SuiApi,
) : FeeService {
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

        // Simulate Tx, and get safe gas budget, avoid subtracting storageRebate for safety
        val dryRunResult = suiApi.dryRunTransaction(txSerialized)
        val gasUsed = dryRunResult.effects.gasUsed
        val gasBudget = gasUsed.computationCost.toBigInteger() + gasUsed.storageCost.toBigInteger()
        val safeGasBudget = gasBudget.increaseByPercent(15)

        // Check against min gas required by network, in some edge cases for small tx
        // you might get less than 2000
        val finalSafeGasBudget = if (safeGasBudget < MIN_NETWORK_GAS_BUDGET) {
            MIN_NETWORK_GAS_BUDGET
        } else {
            safeGasBudget
        }

        // fetch current gas price
        val gasPrice = referenceGasPriceDeferred.await()

        GasFees(
            price = gasPrice,
            limit = finalSafeGasBudget,
            amount = finalSafeGasBudget,
        )
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

        val estimatedFees = DEFAULT_GAS_BUDGET.increaseByPercent(15)

        return GasFees(
            price = gasPrice,
            limit = DEFAULT_GAS_BUDGET,
            amount = estimatedFees,
        )
        return BasicFee(amount = estimatedFees)
    }

    private companion object {
        // Min gas budget accepeted by the network
        val MIN_NETWORK_GAS_BUDGET = 2000.toBigInteger()

        val DEFAULT_GAS_BUDGET = "3000000".toBigInteger()
    }
}