package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.api.EvmApiFactory
import com.vultisig.wallet.data.api.models.EvmTxStatusJson
import com.vultisig.wallet.data.api.models.paidFeeWei
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.EstimatedGasFee
import com.vultisig.wallet.data.models.GasFeeParams
import com.vultisig.wallet.data.models.TokenStandard
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.nativeToken
import java.math.BigInteger
import javax.inject.Inject
import kotlinx.coroutines.delay

/**
 * Recomputes the actual EVM network fee burned by a confirmed transaction.
 *
 * Extracted from `KeysignViewModel` so the receipt polling + fee math can be unit-tested in
 * isolation. Polls the transaction receipt and, once available, derives the burned fee (`gasUsed ×
 * effectiveGasPrice`) and maps it through [gasFeeToEstimatedFee]. Only [invoke]'s own transaction
 * is read: an approval broadcast before it is a separate transaction with its own fee.
 */
internal class UpdateEvmActualFeeUseCase
@Inject
constructor(
    private val evmApiFactory: EvmApiFactory,
    private val gasFeeToEstimatedFee: GasFeeToEstimatedFeeUseCase,
) {

    /**
     * Returns the actual-fee estimate for [txHash] on [chain], or null when not applicable: a
     * non-EVM chain, no receipt within the retry window, or a receipt missing the fee fields.
     *
     * Gas is always paid in the chain's native coin, so the fee is valued in it even when the
     * transaction moved an ERC-20 — valuing wei at the token's decimals would inflate the fiat.
     *
     * @param txHash On-chain hash of the confirmed transaction.
     * @param chain Chain the transaction was broadcast to.
     */
    suspend operator fun invoke(txHash: String, chain: Chain): EstimatedGasFee? {
        if (chain.standard != TokenStandard.EVM) return null

        val evmApi = evmApiFactory.createEvmApi(chain)
        var receipt: EvmTxStatusJson? = null
        for (attempt in 1..MAX_EVM_RECEIPT_RETRIES) {
            receipt = evmApi.getTxStatus(txHash)?.result
            if (receipt != null) break // stop retrying whether or not fee fields are populated
            if (attempt < MAX_EVM_RECEIPT_RETRIES) delay(EVM_RECEIPT_RETRY_DELAY_MS)
        }
        val paidFeeWei = receipt?.paidFeeWei() ?: return null
        val nativeToken = chain.nativeToken
        return gasFeeToEstimatedFee(
            GasFeeParams(
                gasLimit = BigInteger.ONE,
                gasFee =
                    TokenValue(
                        value = paidFeeWei,
                        unit = nativeToken.ticker,
                        decimals = nativeToken.decimal,
                    ),
                selectedToken = nativeToken,
            )
        )
    }

    private companion object {
        const val MAX_EVM_RECEIPT_RETRIES = 5
        const val EVM_RECEIPT_RETRY_DELAY_MS = 2_000L
    }
}
