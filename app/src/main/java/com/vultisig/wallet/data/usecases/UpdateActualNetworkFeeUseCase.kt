package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.EstimatedGasFee
import com.vultisig.wallet.data.models.GasFeeParams
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.nativeToken
import java.math.BigInteger
import javax.inject.Inject
import kotlinx.coroutines.delay

/**
 * Values the network fee a confirmed transaction actually paid, for the done screen to show in place
 * of the pre-sign estimate.
 *
 * Extracted from `KeysignViewModel` so the retry + fee math can be unit-tested in isolation. Reads
 * the paid fee through [fetchPaidNetworkFee], retrying while the indexer catches up, and maps it
 * through [gasFeeToEstimatedFee].
 */
internal class UpdateActualNetworkFeeUseCase
@Inject
constructor(
    private val fetchPaidNetworkFee: FetchPaidNetworkFeeUseCase,
    private val gasFeeToEstimatedFee: GasFeeToEstimatedFeeUseCase,
) {

    /**
     * Returns the paid fee for [txHash] on [chain], or null when not applicable: a chain whose paid
     * fee isn't read back, or no fee within the retry window.
     *
     * The fee is always paid in the chain's native coin, so it is valued in it even when the
     * transaction moved a token — valuing wei at an ERC-20's decimals would inflate the fiat.
     *
     * @param txHash On-chain hash of the confirmed transaction.
     * @param chain Chain the transaction was broadcast to.
     */
    suspend operator fun invoke(txHash: String, chain: Chain): EstimatedGasFee? {
        if (!fetchPaidNetworkFee.supports(chain)) return null

        var paidFee: BigInteger? = null
        for (attempt in 1..MAX_FEE_RETRIES) {
            paidFee = fetchPaidNetworkFee(chain, txHash)
            if (paidFee != null) break
            if (attempt < MAX_FEE_RETRIES) delay(FEE_RETRY_DELAY_MS)
        }
        if (paidFee == null) return null
        val nativeToken = chain.nativeToken
        return gasFeeToEstimatedFee(
            GasFeeParams(
                gasLimit = BigInteger.ONE,
                gasFee =
                    TokenValue(
                        value = paidFee,
                        unit = nativeToken.ticker,
                        decimals = nativeToken.decimal,
                    ),
                selectedToken = nativeToken,
            )
        )
    }

    private companion object {
        const val MAX_FEE_RETRIES = 5
        const val FEE_RETRY_DELAY_MS = 2_000L
    }
}
