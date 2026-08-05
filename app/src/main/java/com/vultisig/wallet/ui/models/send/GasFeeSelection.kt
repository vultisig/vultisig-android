package com.vultisig.wallet.ui.models.send

import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.TokenStandard
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.ui.utils.UiText
import java.math.BigInteger

/**
 * The Advanced Gas Settings entry that actually applies to [chain].
 *
 * `saveGasSettings` never clears what it stored, so an `Eth` entry set on an EVM chain outlives a
 * switch to a Cosmos, UTXO, Solana, Substrate, Sui or Ripple one. Reading it there would size both
 * the gas-limit multiplier and the displayed fee off a wei-denominated value that chain never
 * charges — [selectGasFeeForFeeEstimation] only special-cases Cardano and UTXO, so every other
 * non-EVM chain would take the `evmGasSettings != null` branch. Resolve the settings through here
 * instead of casting at the call site.
 */
internal fun GasSettings?.evmSettingsFor(chain: Chain): GasSettings.Eth? =
    (this as? GasSettings.Eth)?.takeIf { chain.standard == TokenStandard.EVM }

/**
 * Picks the gas fee value passed to GasFeeToEstimatedFeeUseCase for a given chain.
 *
 * Cardano is special-cased: it uses a fixed fee from UtxoFeeService and never goes through the
 * Bitcoin UTXO planner, so [planFee] is the sentinel `1`. Without this branch the generic UTXO path
 * would overwrite the real fee with `1` and the UI would show ~0 ADA.
 *
 * [evmGasSettings] is assumed to already be chain-appropriate — see [evmSettingsFor].
 */
internal fun selectGasFeeForFeeEstimation(
    chain: Chain,
    gasFee: TokenValue,
    planFee: Long?,
    evmGasSettings: GasSettings.Eth?,
): TokenValue =
    when {
        chain == Chain.Cardano -> gasFee
        chain.standard == TokenStandard.UTXO -> {
            val plan =
                planFee
                    ?: throw InvalidTransactionDataException(
                        UiText.StringResource(R.string.send_error_invalid_plan_fee)
                    )
            if (plan > 0) gasFee.copy(value = BigInteger.valueOf(plan)) else gasFee
        }
        evmGasSettings != null -> gasFee.copy(value = evmGasSettings.maxFeePerGasWei)
        else -> gasFee
    }
