package com.vultisig.wallet.data.blockchain.xrp

import com.vultisig.wallet.data.api.RippleApi
import com.vultisig.wallet.data.api.RippleServerStateResponseJson
import com.vultisig.wallet.data.api.getBaseReserve
import com.vultisig.wallet.data.blockchain.model.Fee
import com.vultisig.wallet.data.blockchain.FeeService
import com.vultisig.wallet.data.blockchain.model.BlockchainTransaction
import com.vultisig.wallet.data.blockchain.model.RippleFees
import com.vultisig.wallet.data.blockchain.model.Transfer
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import java.math.BigInteger
import javax.inject.Inject

/**
 * Fee service implementation for the XRP Ledger.
 *
 * How XRP transaction fees work:
 * - The XRP Ledger uses a dynamic fee model, not a fixed fee schedule.
 * - Each transaction must include a "base fee", which is adjusted (depending on priority in
 *   our case we choose medium/high).
 *
 * Implementation details:
 * - Query the XRPL server for the current fee and network state (server load factor).
 * - Dynamically estimate a safe transaction fee.
 * - If the destination account does not yet exist, add the "account reserve" cost
 *   (a one-time minimum balance requirement for account activation).
 *
 * Reference: https://xrpl.org/docs/concepts/transactions/transaction-cost
 */
class RippleFeeService @Inject constructor(
    private val rippleApi: RippleApi
) : FeeService {
    override suspend fun calculateFees(
        transaction: BlockchainTransaction,
    ): Fee = supervisorScope {
        require(transaction is Transfer) {
            "Invalid Transaction type: ${transaction::class.simpleName}"
        }
        val toAddress = transaction.to

        val serverStateDeferred = async { rippleApi.fetchServerState() }
        val accountStateDeferred = async { rippleApi.fetchAccountsInfo(toAddress) }

        val computedFee = computeServerStateFee(serverStateDeferred)
        val networkFee = maxOf(computedFee, MIN_PROTOCOL_FEE)

        // Fetch the destination, check if it does not exist, and include reservedBase fees
        val accountData = accountStateDeferred.await()?.result?.accountData

        val accountActivationFee: BigInteger = if (accountData == null) {
            serverStateDeferred.await().getBaseReserve()
        } else {
            BigInteger.ZERO
        }

        RippleFees(
            networkFee = networkFee,
            accountActivationFee = accountActivationFee,
            amount = networkFee + accountActivationFee
        )
    }

    /**
     * Computes the transaction fee based on XRPL server state.
     * Formula:
     *   fee = (base_fee * load_factor / load_base) * 2
     *
     * x2 multiplier adds safety against sudden fee spikes
     */
    private suspend fun computeServerStateFee(
        serverStateDeferred: Deferred<RippleServerStateResponseJson>
    ): BigInteger {
        val state = serverStateDeferred.await().result?.state
            ?: error("XRPFeeService RPC Error: Can't fetch fees")

        val baseServerState =
            ((state.validateLedger.baseFee * state.loadFactor) / state.loadBase).toBigInteger()

        return baseServerState * 2.toBigInteger()
    }

    override suspend fun calculateDefaultFees(transaction: BlockchainTransaction): Fee {
        return RippleFees(
            networkFee = DEFAULT_RIPPLE_FEE,
            accountActivationFee = BigInteger.ZERO,
            amount = DEFAULT_RIPPLE_FEE,
        )
    }

    private companion object {
        private val MIN_PROTOCOL_FEE = 15.toBigInteger()
        private val DEFAULT_RIPPLE_FEE = 400.toBigInteger()
    }
}