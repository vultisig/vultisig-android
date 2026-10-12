package com.vultisig.wallet.data.blockchain.near

import com.vultisig.wallet.data.api.NearApi
import com.vultisig.wallet.data.blockchain.FeeService
import com.vultisig.wallet.data.blockchain.model.BasicFee
import com.vultisig.wallet.data.blockchain.model.BlockchainTransaction
import com.vultisig.wallet.data.blockchain.model.Fee
import java.math.BigInteger
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * The upfront gas reservation a NEAR transfer must hold on top of its amount — the same
 * receiver-dependent figure the chain-specific step freezes into the payload, so the fee shown is
 * the reservation signed. It is an upper bound: nearcore refunds the unused receipt gas.
 */
internal class NearFeeService @Inject constructor(private val nearApi: NearApi) : FeeService {

    override suspend fun calculateFees(transaction: BlockchainTransaction): Fee = coroutineScope {
        val config = async { nearApi.getFeeConfig() }
        val block = async { nearApi.getFinalBlock() }
        BasicFee(
            NearFees.gasReservation(
                config = config.await(),
                gasPrice = block.await().gasPrice,
                senderIsReceiver = transaction.coin.address == transaction.to,
                receiverIsImplicit = NearAccountId.isImplicit(transaction.to),
            )
        )
    }

    override suspend fun calculateDefaultFees(transaction: BlockchainTransaction): Fee =
        BasicFee(DEFAULT_IMPLICIT_RESERVATION)

    private companion object {
        /**
         * Worst-case reservation (implicit receiver) on protocol 86 at the 1e9 receipt-price floor,
         * about 0.0076 NEAR, rounded up for a fee shown before the node is reachable.
         */
        val DEFAULT_IMPLICIT_RESERVATION: BigInteger = BigInteger("8000000000000000000000")
    }
}
