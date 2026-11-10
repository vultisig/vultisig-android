package com.vultisig.wallet.data.blockchain.utxo

import com.vultisig.wallet.data.api.BlockChairApi
import com.vultisig.wallet.data.api.MempoolApi
import com.vultisig.wallet.data.blockchain.FeeService
import com.vultisig.wallet.data.blockchain.model.BasicFee
import com.vultisig.wallet.data.blockchain.model.BlockchainTransaction
import com.vultisig.wallet.data.blockchain.model.Fee
import com.vultisig.wallet.data.models.Chain
import java.math.BigInteger
import javax.inject.Inject
import kotlin.math.ceil
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

class UtxoFeeService
@Inject
constructor(private val blockChairApi: BlockChairApi, private val mempoolApi: MempoolApi) :
    FeeService {

    override suspend fun calculateFees(transaction: BlockchainTransaction): Fee {
        return calculateDefaultFees(transaction)
    }

    override suspend fun calculateDefaultFees(transaction: BlockchainTransaction): Fee {
        val amount = getDefaultGasFee(transaction.coin.chain)
        return BasicFee(amount)
    }

    suspend fun getDefaultGasFee(chain: Chain): BigInteger {
        val gas =
            when (chain) {
                Chain.Zcash -> "1000".toBigInteger()
                Chain.Cardano -> "180000".toBigInteger()
                Chain.Bitcoin -> {
                    val blockchairRate = blockChairApi.getBlockChairStats(chain)
                    // No usable next-block projection: keep the Blockchair x2.5 rate.
                    nextBlockByteFee(blockchairRate) ?: blockchairByteFee(blockchairRate)
                }
                else -> {
                    val gas = blockChairApi.getBlockChairStats(chain)
                    if (chain == Chain.Dogecoin) {
                        gas.multiply(BigInteger("5")).divide(BigInteger("20"))
                    } else {
                        blockchairByteFee(gas)
                    }
                }
            }
        return gas
    }

    private fun blockchairByteFee(rate: BigInteger): BigInteger =
        rate.multiply(BigInteger("5")).divide(BigInteger("2"))

    private suspend fun nextBlockByteFee(blockchairRate: BigInteger): BigInteger? {
        val median = fetchNextBlockMedianFee() ?: return null
        if (!median.isFinite() || median <= 0.0 || median > MAX_NEXT_BLOCK_MEDIAN_FEE) return null
        val medianMilli = BigInteger.valueOf(ceil(median * MILLI).toLong())
        val target = medianMilli.max(blockchairRate.multiply(BigInteger.valueOf(MILLI)))
        val withMargin = ceilDiv(target.multiply(MARGIN_PERCENT), MARGIN_DIVISOR)
        // Sends cannot be fee-bumped (no RBF), so the rate never drops below 2 sat/vB.
        return withMargin.max(MIN_BITCOIN_BYTE_FEE)
    }

    private suspend fun fetchNextBlockMedianFee(): Double? =
        try {
            withTimeoutOrNull(MEMPOOL_TIMEOUT) { mempoolApi.getNextBlockMedianFee() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.d(e, "Next-block fee projection unavailable")
            null
        }

    private fun ceilDiv(dividend: BigInteger, divisor: BigInteger): BigInteger =
        dividend.add(divisor).subtract(BigInteger.ONE).divide(divisor)

    private companion object {
        val MEMPOOL_TIMEOUT = 5.seconds
        const val MAX_NEXT_BLOCK_MEDIAN_FEE = 5000.0
        const val MILLI = 1000L
        val MARGIN_PERCENT: BigInteger = BigInteger.valueOf(125)
        val MARGIN_DIVISOR: BigInteger = BigInteger.valueOf(MILLI * 100)
        val MIN_BITCOIN_BYTE_FEE: BigInteger = BigInteger.valueOf(2)
    }
}
