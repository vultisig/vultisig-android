package com.vultisig.wallet.ui.models.send

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.api.BlockChairApi
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

internal data class GasSettingsUiModel(
    val chainSpecific: BlockChainSpecific? = null,
    val selectedPriorityFee: PriorityFee = PriorityFee.FAST,
    val currentBaseFee: String = "",
    val defaultByteFee: BigInteger = BigInteger("0"),
    val totalFee: String = "",
    val totalFeeError: UiText? = null,
    val byteFeeError: UiText? = null,
    val gasLimitError: UiText? = null,
)

internal enum class PriorityFee {
    LOW, NORMAL, FAST,
}

@HiltViewModel
internal class GasSettingsViewModel @Inject constructor(
    private val evmApiFactory: EvmApiFactory,
    private val blockChairApi: BlockChairApi,
    private val convertWeiToGwei: ConvertWeiToGweiUseCase,
    private val convertGweiToWei: ConvertGweiToWeiUseCase,
) : ViewModel() {

    val state = MutableStateFlow(GasSettingsUiModel())

    val gasLimitState = TextFieldState()
    val byteFeeState = TextFieldState()

    private var priorityFeesMap = emptyMap<PriorityFee, BigInteger>()

    private fun collectEthData() = viewModelScope.launch {
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

    private fun updateByteFee(priorityFee: PriorityFee) {
        val fee = when (priorityFee) {
            PriorityFee.LOW -> state.value.defaultByteFee
                .multiply(BigInteger("2")).divide(BigInteger("3"))

            PriorityFee.NORMAL -> state.value.defaultByteFee

            PriorityFee.FAST -> state.value.defaultByteFee
                .multiply(BigInteger("5")).divide(BigInteger("2"))
        }
        byteFeeState.setTextAndPlaceCursorAtEnd(fee.toString())
    }

    fun loadData(chain: Chain, spec: BlockChainSpecificAndUtxo) {

        val specific = spec.blockChainSpecific

        state.update {
            it.copy(
                chainSpecific = specific,
            )
        }

            when(specific){
                is BlockChainSpecific.Ethereum -> {
                    collectEthData()
                    loadEthData(chain, spec)
                }
                is BlockChainSpecific.UTXO -> {
                    loadUTXOData(chain, spec)
                }
                else -> {}
            }
    }

    private fun loadEthData(chain: Chain, spec: BlockChainSpecificAndUtxo){
        val gasLimit = if (gasLimitState.text.isEmpty()) {
            (spec.blockChainSpecific as BlockChainSpecific.Ethereum).gasLimit
        } else {
            gasLimitState.text.toString().toBigInteger()
        }

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

    private fun loadUTXOData(chain: Chain, spec: BlockChainSpecificAndUtxo){

        viewModelScope.launch {
            if (byteFeeState.text.toBigInteger() > BigInteger.ZERO)
                return@launch
            try {
                val stats = blockChairApi.getBlockChairStats(chain)
                val byte = stats.multiply(BigInteger("5")).divide(BigInteger("2"))
                state.update {
                    it.copy(
                        defaultByteFee = stats,
                    )
                }
                byteFeeState.setTextAndPlaceCursorAtEnd(byte.toString())
            } catch (e: Exception) {
                Timber.e(e)
            }
        }
    }

    fun selectPriorityFee(priorityFee: PriorityFee) {
        state.update {
            it.copy(selectedPriorityFee = priorityFee)
        }
        updateByteFee(priorityFee)
    }

    fun save(): GasSettings {
        return when (state.value.chainSpecific) {
            is BlockChainSpecific.Ethereum -> GasSettings.Eth(
                priorityFee = priorityFeesMap[state.value.selectedPriorityFee]!!,
                gasLimit = gasLimitState.text.toString().toBigInteger(),
            )
            is BlockChainSpecific.UTXO -> GasSettings.UTXO(
                byteFee = byteFeeState.text.toString().toBigInteger(),
            )
            else -> throw IllegalStateException("Unsupported chain specific")
        }
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

    private fun CharSequence.toBigInteger() = try {
        BigInteger(toString())
    } catch (e: Exception) {
        BigInteger.ZERO
    }
}