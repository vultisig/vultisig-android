package com.vultisig.wallet.data.blockchain.solana

import com.vultisig.wallet.data.api.SolanaApi
import com.vultisig.wallet.data.blockchain.BlockchainTransaction
import com.vultisig.wallet.data.blockchain.Fee
import com.vultisig.wallet.data.blockchain.FeeService
import com.vultisig.wallet.data.blockchain.GasFees
import com.vultisig.wallet.data.blockchain.Transfer
import com.vultisig.wallet.data.chains.helpers.PRIORITY_FEE_LIMIT
import com.vultisig.wallet.data.chains.helpers.PRIORITY_FEE_PRICE
import com.vultisig.wallet.data.chains.helpers.SolanaHelper
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.utils.toUnit
import wallet.core.jni.CoinType
import java.math.BigDecimal
import java.math.BigInteger
import javax.inject.Inject

class SolanaFeeService @Inject constructor(
    private val solanaApi: SolanaApi,
) : FeeService {

    override suspend fun calculateFees(transaction: BlockchainTransaction): Fee {
        require(transaction is Transfer) {
            "Invalid Transaction Type: ${transaction::class.simpleName}"
        }
        val vaultHexPubKey = transaction.vault.vaultHexPublicKey
        val toAddress = transaction.to
        val coin = transaction.coin

        val keySignPayload = buildKeySignPayload()

        val serializedTx = SolanaHelper(vaultHexPubKey).getZeroSignedTransaction(keySignPayload)

        val baseFee = solanaApi.getFeeForMessage(serializedTx)
        val rentExemptionFee = calculateRentExemptionForTokens(toAddress, coin)

        val priorityFee = (PRIORITY_FEE_PRICE * PRIORITY_FEE_LIMIT).toBigInteger()
        val priorityAmount = priorityFee
            .toBigDecimal()
            .divide(BigDecimal.TEN.pow(6))
            .toBigInteger()

        return GasFees(
            price = PRIORITY_FEE_PRICE.toBigInteger(),
            limit = PRIORITY_FEE_LIMIT.toBigInteger(),
            amount = baseFee + priorityAmount + rentExemptionFee,
        )
    }

    private suspend fun calculateRentExemptionForTokens(toAddress: String, coin: Coin): BigInteger {
        val isToken = !coin.isNativeToken
        val contract = coin.contractAddress

        if (isToken) {
            val toTokenAddress =
                solanaApi.getTokenAssociatedAccountByOwner(toAddress, contract).first ?: ""

            return if (toTokenAddress.isEmpty()) {
                solanaApi.getMinimumBalanceForRentExemption()
            } else {
                BigInteger.ZERO
            }
        }

        return BigInteger.ZERO
    }

    // TODO: Build KeySignPayload
    private fun buildKeySignPayload(): KeysignPayload {
        error("")
    }

    override suspend fun calculateDefaultFees(transaction: BlockchainTransaction): Fee {
        require(transaction is Transfer) {
            "Invalid Transaction Type: ${transaction::class.simpleName}"
        }

        val priorityFee = (PRIORITY_FEE_PRICE * PRIORITY_FEE_LIMIT).toBigInteger()

        val priorityAmount = priorityFee
            .toBigDecimal()
            .divide(BigDecimal.TEN.pow(6))
            .toBigInteger()

        return GasFees(
            price = PRIORITY_FEE_PRICE.toBigInteger(),
            limit = PRIORITY_FEE_LIMIT.toBigInteger(),
            amount = DEFAULT_COIN_TRANSFER_BASE_FEE + priorityAmount,
        )
    }

    private companion object {
        val DEFAULT_COIN_TRANSFER_BASE_FEE = CoinType.SOLANA.toUnit("0.000105".toBigDecimal())
    }
}