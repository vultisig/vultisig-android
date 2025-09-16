package com.vultisig.wallet.data.blockchain.xrp

import com.vultisig.wallet.data.api.RippleApi
import com.vultisig.wallet.data.api.RippleServerStateResponseJson
import com.vultisig.wallet.data.blockchain.BasicFee
import com.vultisig.wallet.data.blockchain.Fee
import com.vultisig.wallet.data.blockchain.FeeService
import com.vultisig.wallet.data.models.Chain
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import java.math.BigInteger

/**
 * Fee service implementation for the XRP Ledger.
 *
 * Responsibilities:
 * - Query the XRPL server for current fee and network state.
 * - Estimate transaction fees dynamically based on server load.
 * - Add account activation cost if the destination account does not yet exist.
 *
 * Reference: https://xrpl.org/docs/concepts/transactions/transaction-cost
 */

class XRPFeeService(
    private val rippleApi: RippleApi
) : FeeService {
    override suspend fun calculateFees(
        chain: Chain,
        limit: BigInteger,
        isSwap: Boolean,
        to: String?
    ): Fee = supervisorScope {
        val serverStateDeferred = async { rippleApi.fetchServerState() }
        val accountStateDeferred = async { rippleApi.fetchAccountsInfo(to!!) }

        val computedFee = computeServerStateFee(serverStateDeferred)
        val networkFee = maxOf(computedFee, MIN_PROTOCOL_FEE)

        val accountData = accountStateDeferred.await()?.result?.accountData
        val serverState = serverStateDeferred.await().result?.state
        val accountActivationFee: BigInteger = if (accountData == null) {
            serverState?.validateLedger?.reservedBase?.toBigInteger()
                ?: error("XRPFeeService RPC Error: Can't fetch fees")
        } else {
            BigInteger.ZERO
        }

        BasicFee(amount = networkFee + accountActivationFee)
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

        return baseServerState * BigInteger.TWO
    }

    override suspend fun calculateDefaultFees(): Fee {
        return BasicFee(amount = 600.toBigInteger())
    }

    private companion object {
        private val MIN_PROTOCOL_FEE = 15.toBigInteger()
    }
}