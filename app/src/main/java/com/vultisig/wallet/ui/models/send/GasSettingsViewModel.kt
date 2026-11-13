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
import com.vultisig.wallet.data.utils.safeLaunch
import com.vultisig.wallet.ui.utils.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigInteger
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

internal data class GasSettingsUiModel(
    val chainSpecific: BlockChainSpecific? = null,
    val byteFeeError: UiText? = null,
    // True while loadEthData's fetch is in flight, and left true if it fails: Save must not be
    // pressable while baseFee/priorityFee are blanked, or a slow/failed fetch would let the
    // user save a zero fee (issue #5397).
    val isLoadingEthFee: Boolean = false,
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

    private var loadJob: Job? = null

    fun loadData(chain: Chain, spec: BlockChainSpecificAndUtxo) {
        val specific = spec.blockChainSpecific

        // A reload for another chain must not be overwritten by the previous chain's late fetch.
        loadJob?.cancel()

        state.update { it.copy(chainSpecific = specific) }

        when (specific) {
            is BlockChainSpecific.Ethereum -> {
                loadJob = loadEthData(chain, specific)
            }

            is BlockChainSpecific.UTXO -> {
                loadJob = loadUTXOData(chain)
            }

            else -> Unit
        }
    }

    private fun loadEthData(chain: Chain, spec: BlockChainSpecific.Ethereum): Job {
        gasLimitState.setTextAndPlaceCursorAtEnd(spec.gasLimit.toString())
        // This ViewModel is Hilt-scoped to the Send screen, not to one dialog open, so these
        // fields survive a close-without-saving. Blank them synchronously (before the network
        // fetch below) so a Save tapped for a newly opened chain, before that fetch resolves,
        // can never sign a fee carried over from a previous chain's session.
        baseFeeState.setTextAndPlaceCursorAtEnd("")
        priorityFeeState.setTextAndPlaceCursorAtEnd("")
        state.update { it.copy(isLoadingEthFee = true) }

        // isLoadingEthFee stays true on failure (safeLaunch logs it): the fields are still blank, so
        // Save must stay disabled rather than let this failure save a zero fee.
        return viewModelScope.safeLaunch {
            val evmApi = evmApiFactory.createEvmApi(chain)
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
            state.update { it.copy(isLoadingEthFee = false) }
        }
    }

    private fun loadUTXOData(chain: Chain): Job =
        viewModelScope.safeLaunch {
            if (byteFeeState.text.toBigInteger() > BigInteger.ZERO) return@safeLaunch
            val byteFee = utxoFeeService.getDefaultGasFee(chain)
            byteFeeState.setTextAndPlaceCursorAtEnd(byteFee.toString())
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
