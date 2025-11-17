package com.vultisig.wallet.data.blockchain.sui

import com.vultisig.wallet.data.api.chains.SuiApi
import com.vultisig.wallet.data.blockchain.model.BasicFee
import com.vultisig.wallet.data.blockchain.model.BlockchainTransaction
import com.vultisig.wallet.data.blockchain.model.Fee
import com.vultisig.wallet.data.blockchain.FeeService
import com.vultisig.wallet.data.blockchain.model.GasFees
import com.vultisig.wallet.data.blockchain.model.Transfer
import com.vultisig.wallet.data.crypto.SuiHelper
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.utils.increaseByPercent
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import vultisig.keysign.v1.SuiCoin
import java.math.BigInteger
import javax.inject.Inject

/**
 * Service responsible for estimating and preparing gas payment details for Sui blockchain transactions.
 *
 * In Sui, every transaction requires gas fees to compensate validators for computation and storage costs.
 * Gas fees are paid in SUI tokens and depend on:
 *  - **Computation cost**: The amount of CPU cycles or execution units required by the transaction.
 *  - **Storage cost**: The cost of writing or modifying objects on-chain.
 *  - **Reference gas price**: An epoch-level network parameter that determines SUI token price per gas unit.
 *
 * This service calculates the required gas fees by simulating the transaction and combining computation
 * and storage costs, ensuring a safe gas budget with a small margin.
 *
 * Implementation details:
 *  - Uses the RPC `dry-run` / `dev-inspect` endpoint to retrieve computation units and storage effects.
 *  - Queries the current reference gas price from the node.
 *  - Computes total gas fee as:
 *      `totalGas = computationUnits * referenceGasPrice + storageUnits * storagePricePerUnit`
 *  - Applies a configurable safety margin (default +15%) to avoid transaction failure due to underestimation.
 *  - Ensures the final gas budget is not below the network minimum.
 *
 * Notes:
 *  - Gas fee estimates are approximate; always allow a safety margin.
 *  - Small transactions may still require a minimum network gas budget.
 *  - Reference documentation: [Sui Gas Concepts](https://docs.sui.io/concepts/tokenomics/gas-in-sui)
 */
class SuiFeeService @Inject constructor(
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
                gasBudget = SUI_DEFAULT_GAS_BUDGET,
            ),
            vaultPublicKeyECDSA = "",
            vaultLocalPartyID = "",
            libType = null,
            wasmExecuteContractPayload = null,
        )
    }

    override suspend fun calculateDefaultFees(transaction: BlockchainTransaction): Fee {
        val gasPrice = suiApi.getReferenceGasPrice()

        val estimatedFees = SUI_DEFAULT_GAS_BUDGET.increaseByPercent(15)

        return GasFees(
            price = gasPrice,
            limit = estimatedFees,
            amount = estimatedFees,
        )
        return BasicFee(amount = estimatedFees)
    }

    internal companion object {
        // Min gas budget accepeted by the network
        val MIN_NETWORK_GAS_BUDGET = 2000.toBigInteger()

        val SUI_DEFAULT_GAS_BUDGET = "3000000".toBigInteger()
    }
}