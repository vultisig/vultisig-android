package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.api.CosmosApiFactory
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.EstimatedGasFee
import com.vultisig.wallet.data.models.GasFeeParams
import com.vultisig.wallet.data.models.TokenValue
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import javax.inject.Inject
import kotlinx.coroutines.delay

/**
 * Recomputes the Terra network fee shown on the transaction-done screen from the confirmed tx's
 * actual `gas_used`, mirroring the vultisig extension / iOS "gas used × min gas price" display.
 *
 * DISPLAY-ONLY. Terra (Cosmos) does NOT refund unused gas — the amount actually DEDUCTED is the
 * declared `fee.amount` (the static 7500 uluna), not this number. This intentionally shows less
 * than what left the balance, purely for cross-platform parity with the other clients' done
 * screens; it does not change the signed fee. The real fix (lowering the deducted fee) needs every
 * co-signer to honor a lowered gas_limit — see issue #5279. Mirrors [UpdateEvmActualFeeUseCase],
 * except EVM genuinely refunds unused gas so there the recomputed value IS what was burned.
 */
internal class UpdateTerraDisplayFeeUseCase
@Inject
constructor(
    private val cosmosApiFactory: CosmosApiFactory,
    private val gasFeeToEstimatedFee: GasFeeToEstimatedFeeUseCase,
) {

    /**
     * Returns the `gas_used × min-gas-price` fee estimate for [txHash], or null when not
     * applicable: a non-Terra chain, no landed tx within the retry window, or a tx missing
     * `gas_used`.
     */
    suspend operator fun invoke(txHash: String, chain: Chain, coin: Coin): EstimatedGasFee? {
        if (chain != Chain.Terra) return null

        val api = cosmosApiFactory.createCosmosApi(chain)
        var gasUsed: BigInteger? = null
        for (attempt in 1..MAX_RETRIES) {
            gasUsed =
                api.getTxStatus(txHash)?.txResponse?.gasUsed?.toBigIntegerOrNull()?.takeIf {
                    it > BigInteger.ZERO
                }
            if (gasUsed != null) break
            if (attempt < MAX_RETRIES) delay(RETRY_DELAY_MS)
        }
        val used = gasUsed ?: return null

        // Truncate (DOWN), not round up: this is a cosmetic display, and the extension shows
        // floor(gas_used × price) — e.g. 77779 × 0.025 = 1944.475 → 1944 uluna (0.001944 LUNA).
        val effectiveFee =
            (used.toBigDecimal() * TERRA_MIN_GAS_PRICE)
                .setScale(0, RoundingMode.DOWN)
                .toBigInteger()
        return gasFeeToEstimatedFee(
            GasFeeParams(
                gasLimit = BigInteger.ONE,
                gasFee =
                    TokenValue(value = effectiveFee, unit = coin.ticker, decimals = coin.decimal),
                selectedToken = coin,
            )
        )
    }

    private companion object {
        // Terra (LUNA, phoenix-1) minimum gas price, 0.025 uluna/gas — the same rate the extension
        // uses to render `gas_used × price`.
        val TERRA_MIN_GAS_PRICE = BigDecimal("0.025")
        const val MAX_RETRIES = 5
        const val RETRY_DELAY_MS = 2_000L
    }
}
