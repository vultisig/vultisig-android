package com.vultisig.wallet.ui.models.send

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.api.EvmApiFactory
import com.vultisig.wallet.data.blockchain.utxo.UtxoFeeService
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.supportsLegacyGas
import com.vultisig.wallet.data.repositories.BlockChainSpecificAndUtxo
import com.vultisig.wallet.data.usecases.ConvertGweiToWeiUseCase
import com.vultisig.wallet.data.usecases.ConvertWeiToGweiUseCase
import com.vultisig.wallet.ui.utils.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigInteger
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

internal data class GasSettingsUiModel(
    val chainSpecific: BlockChainSpecific? = null,
    val byteFeeError: UiText? = null,
)

internal enum class PriorityFee {
    LOW,
    NORMAL,
    FAST,
}

@HiltViewModel
internal class GasSettingsViewModel
@Inject
constructor(
    private val evmApiFactory: EvmApiFactory,
    private val utxoFeeService: UtxoFeeService,
    private val convertWeiToGwei: ConvertWeiToGweiUseCase,
    private val convertGweiToWei: ConvertGweiToWeiUseCase,
) : ViewModel() {

    val state = MutableStateFlow(GasSettingsUiModel())

    val gasLimitState = TextFieldState()
    val baseFeeState = TextFieldState()
    val priorityFeeState = TextFieldState()

    val byteFeeState = TextFieldState()

    fun loadData(chain: Chain, spec: BlockChainSpecificAndUtxo) {
        val specific = spec.blockChainSpecific

        state.update { it.copy(chainSpecific = specific) }

        when (specific) {
            is BlockChainSpecific.Ethereum -> {
                loadEthData(chain, specific)
            }

            is BlockChainSpecific.UTXO -> {
                loadUTXOData(chain)
            }

            else -> Unit
        }
    }

    private fun loadEthData(chain: Chain, spec: BlockChainSpecific.Ethereum) {
        gasLimitState.setTextAndPlaceCursorAtEnd(spec.gasLimit.toString())
        // This ViewModel is Hilt-scoped to the Send screen, not to one dialog open, so these
        // fields survive a close-without-saving. Blank them synchronously (before the network
        // fetch below) so a Save tapped for a newly opened chain, before that fetch resolves,
        // can never sign a fee carried over from a previous chain's session.
        baseFeeState.setTextAndPlaceCursorAtEnd("")
        priorityFeeState.setTextAndPlaceCursorAtEnd("")

        viewModelScope.launch {
            val evmApi = evmApiFactory.createEvmApi(chain)
            try {
                if (chain.supportsLegacyGas) {
                    // No EIP-1559 base fee exists on a legacy-gas chain (BSC's is pinned near
                    // zero by BEP-226), so the single price the user edits here is the real
                    // eth_gasPrice, carried in the same baseFee field; priority fee stays zero
                    // and hidden so applyGasSettings' baseFee + priorityFee sum still lands on
                    // exactly that price (issue #5397).
                    val gasPriceGwei = convertWeiToGwei(evmApi.getGasPrice())
                    baseFeeState.setTextAndPlaceCursorAtEnd(gasPriceGwei.toPlainString())
                    priorityFeeState.setTextAndPlaceCursorAtEnd("0")
                } else {
                    val baseFeeGwei = convertWeiToGwei(evmApi.getBaseFee())
                    baseFeeState.setTextAndPlaceCursorAtEnd(baseFeeGwei.toPlainString())
                    priorityFeeState.setTextAndPlaceCursorAtEnd(
                        convertWeiToGwei(spec.priorityFeeWei).toPlainString()
                    )
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Timber.e(e)
            }
        }
    }

    private fun loadUTXOData(chain: Chain) {
        viewModelScope.launch {
            if (byteFeeState.text.toBigInteger() > BigInteger.ZERO) return@launch
            try {
                val byteFee = utxoFeeService.getDefaultGasFee(chain)
                byteFeeState.setTextAndPlaceCursorAtEnd(byteFee.toString())
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Timber.e(e)
            }
        }
    }

    fun save(): GasSettings {
        return when (state.value.chainSpecific) {
            is BlockChainSpecific.Ethereum -> {
                // Neither field's format is restricted to non-negative (the keyboard still
                // accepts a pasted "-"), so clamp here: this is what keeps maxFeePerGasWei
                // (baseFee + priorityFee) from ever landing below priorityFee itself.
                val baseFeeWei =
                    convertGweiToWei(baseFeeState.text.toString().toBigDecimalOrZero())
                        .toBigInteger()
                        .coerceAtLeast(BigInteger.ZERO)
                val priorityFeeWei =
                    convertGweiToWei(priorityFeeState.text.toString().toBigDecimalOrZero())
                        .toBigInteger()
                        .coerceAtLeast(BigInteger.ZERO)
                GasSettings.Eth(
                    baseFee = baseFeeWei,
                    priorityFee = priorityFeeWei,
                    gasLimit = gasLimitState.text.toString().toBigInteger(),
                )
            }

            is BlockChainSpecific.UTXO ->
                GasSettings.UTXO(byteFee = byteFeeState.text.toString().toBigInteger())

            else -> throw IllegalStateException("Unsupported chain specific")
        }
    }

    private fun CharSequence.toBigInteger() =
        try {
            BigInteger(toString())
        } catch (e: NumberFormatException) {
            BigInteger.ZERO
        }

    private fun String.toBigDecimalOrZero() =
        try {
            toBigDecimal()
        } catch (e: NumberFormatException) {
            java.math.BigDecimal.ZERO
        }
}
