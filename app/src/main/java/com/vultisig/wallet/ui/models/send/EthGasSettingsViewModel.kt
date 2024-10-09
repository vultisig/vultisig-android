package com.vultisig.wallet.ui.models.send

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.api.EvmApi
import com.vultisig.wallet.data.api.EvmApiFactory
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.repositories.BlockChainSpecificAndUtxo
import com.vultisig.wallet.data.usecases.ConvertGweiToWeiUseCase
import com.vultisig.wallet.data.usecases.ConvertWeiToGweiUseCase
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.textAsFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.math.BigDecimal
import java.math.BigInteger
import javax.inject.Inject

internal data class EthGasSettingsUiModel(
    val selectedPriorityFee: PriorityFee = PriorityFee.FAST,
    val currentBaseFee: String = "",
    val totalFee: String = "",
    val totalFeeError: UiText? = null,
    val gasLimitError: UiText? = null,
)

internal enum class PriorityFee {
    LOW, NORMAL, FAST,
}

@HiltViewModel
internal class EthGasSettingsViewModel @Inject constructor(
    private val evmApiFactory: EvmApiFactory,
    private val convertWeiToGwei: ConvertWeiToGweiUseCase,
    private val convertGweiToWei: ConvertGweiToWeiUseCase,
) : ViewModel() {

    val state = MutableStateFlow(EthGasSettingsUiModel())

    val gasLimitState = TextFieldState()

    private var priorityFeesMap = emptyMap<PriorityFee, BigInteger>()

    init {
        viewModelScope.launch {
            combine(
                state
                    .mapNotNull { it.currentBaseFee.toBigDecimalOrNull() },
                gasLimitState.textAsFlow()
                    .mapNotNull { it.toString().toBigDecimalOrNull() },
                state.map { it.selectedPriorityFee }
            ) { baseFeeWei, gasLimit, priorityFee ->
                val normalizedBaseFee = convertGweiToWei(baseFeeWei)
                    .multiply(BigDecimal(1.5))

                val fee = priorityFeesMap[priorityFee]
                if (fee != null) {
                    val totalFee = gasLimit * (normalizedBaseFee +
                            fee.toBigDecimal())

                    val totalFeeGwei = convertWeiToGwei(totalFee.toBigInteger())

                    state.update {
                        it.copy(totalFee = totalFeeGwei.toPlainString())
                    }
                }
            }.collect()
        }
    }

    fun loadData(chain: Chain, spec: BlockChainSpecificAndUtxo) {
        val specific = spec.blockChainSpecific as BlockChainSpecific.Ethereum

        val gasLimit = specific.gasLimit

        gasLimitState.setTextAndPlaceCursorAtEnd(gasLimit.toString())

        viewModelScope.launch {
            val evmApi = evmApiFactory.createEvmApi(chain)
            try {
                priorityFeesMap = evmApi.getFeeMap()

                val baseFeeWei = evmApi.getBaseFee()
                val baseFeeGwei = convertWeiToGwei(baseFeeWei)

                state.update {
                    it.copy(
                        currentBaseFee = baseFeeGwei.toPlainString(),
                    )
                }
            } catch (e: Exception) {
                // todo handle
                Timber.e(e)
            }
        }
    }

    fun selectPriorityFee(priorityFee: PriorityFee) {
        state.update {
            it.copy(selectedPriorityFee = priorityFee)
        }
    }

    fun save(): EthGasSettings {
        return EthGasSettings(
            priorityFee = priorityFeesMap[state.value.selectedPriorityFee]!!,
            gasLimit = gasLimitState.text.toString().toBigInteger(),
        )
    }

    private suspend fun EvmApi.getFeeMap(): Map<PriorityFee, BigInteger> {
        val feeHistory = getFeeHistory()

        if (feeHistory.isEmpty()) {
            val maxFee = getMaxPriorityFeePerGas()
            return mapOf(
                PriorityFee.LOW to maxFee,
                PriorityFee.NORMAL to maxFee,
                PriorityFee.FAST to maxFee,
            )
        } else {
            return mapOf(
                PriorityFee.LOW to feeHistory[0],
                PriorityFee.NORMAL to feeHistory[feeHistory.size / 2],
                PriorityFee.FAST to feeHistory[feeHistory.size - 1],
            )
        }
    }

}