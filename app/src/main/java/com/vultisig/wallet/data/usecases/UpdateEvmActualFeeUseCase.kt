package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.api.EvmApiFactory
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.EstimatedGasFee
import com.vultisig.wallet.data.models.GasFeeParams
import com.vultisig.wallet.data.models.TokenStandard
import com.vultisig.wallet.data.models.TokenValue
import java.math.BigInteger
import javax.inject.Inject
import kotlinx.coroutines.delay

/**
 * Recomputes the actual EVM network fee burned by a confirmed transaction.
 *
 * Extracted from `KeysignViewModel` so the receipt polling + fee math can be unit-tested in
 * isolation. Polls the transaction receipt and, once available, derives the burned fee (`gasUsed ×
 * effectiveGasPrice`) and maps it through [gasFeeToEstimatedFee].
 */
class UpdateEvmActualFeeUseCase
@Inject
constructor(
    private val evmApiFactory: EvmApiFactory,
    private val gasFeeToEstimatedFee: GasFeeToEstimatedFeeUseCase,
) {

    /**
     * Returns the actual-fee estimate for [txHash] on [chain], or null when not applicable: a
     * non-EVM chain, no receipt within the retry window, or a receipt missing the fee fields.
     *
     * @param txHash On-chain hash of the confirmed transaction.
     * @param chain Chain the transaction was broadcast to.
     * @param coin Signing coin, used for the fee token's ticker/decimals.
     */
    suspend operator fun invoke(txHash: String, chain: Chain, coin: Coin): EstimatedGasFee? {
        if (chain.standard != TokenStandard.EVM) return null

        val evmApi = evmApiFactory.createEvmApi(chain)
        var gasUsedHex: String? = null
        var effectiveGasPriceHex: String? = null
        for (attempt in 1..MAX_EVM_RECEIPT_RETRIES) {
            val result = evmApi.getTxStatus(txHash)?.result
            if (result != null) {
                gasUsedHex = result.gasUsed
                effectiveGasPriceHex = result.effectiveGasPrice
                break // receipt received — stop retrying whether or not fee fields are populated
            }
            if (attempt < MAX_EVM_RECEIPT_RETRIES) delay(EVM_RECEIPT_RETRY_DELAY_MS)
        }
        val gasUsed = BigInteger((gasUsedHex ?: return null).removePrefix("0x"), 16)
        val effectiveGasPrice =
            BigInteger((effectiveGasPriceHex ?: return null).removePrefix("0x"), 16)
        val actualFeeWei = gasUsed.multiply(effectiveGasPrice)
        return gasFeeToEstimatedFee(
            GasFeeParams(
                gasLimit = BigInteger.ONE,
                gasFee =
                    TokenValue(value = actualFeeWei, unit = coin.ticker, decimals = coin.decimal),
                selectedToken = coin,
            )
        )
    }

    private companion object {
        const val MAX_EVM_RECEIPT_RETRIES = 5
        const val EVM_RECEIPT_RETRY_DELAY_MS = 2_000L
    }
}
