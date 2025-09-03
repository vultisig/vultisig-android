package com.vultisig.wallet.ui.models.send

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.api.BlockChairApi
import com.vultisig.wallet.data.api.EvmApiFactory
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.repositories.BlockChainSpecificAndUtxo
import com.vultisig.wallet.data.usecases.ConvertGweiToWeiUseCase
import com.vultisig.wallet.data.usecases.ConvertWeiToGweiUseCase
import com.vultisig.wallet.ui.utils.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.math.BigInteger
import javax.inject.Inject

internal data class GasSettingsUiModel(
    val chainSpecific: BlockChainSpecific? = null,
    val byteFeeError: UiText? = null,
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
    val baseFeeState = TextFieldState()
    val priorityFeeState = TextFieldState()

    val byteFeeState = TextFieldState()

    fun loadData(chain: Chain, spec: BlockChainSpecificAndUtxo) {
        val specific = spec.blockChainSpecific

        state.update {
            it.copy(
                chainSpecific = specific,
            )
        }

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
        val gasLimit = if (gasLimitState.text.isEmpty()) {
            spec.gasLimit
        } else {
            gasLimitState.text.toString().toBigInteger()
        }

        gasLimitState.setTextAndPlaceCursorAtEnd(gasLimit.toString())

        viewModelScope.launch {
            val evmApi = evmApiFactory.createEvmApi(chain)
            try {
                val baseFeeWei = evmApi.getBaseFee()
                val baseFeeGwei = convertWeiToGwei(baseFeeWei)

                baseFeeState.setTextAndPlaceCursorAtEnd(baseFeeGwei.toPlainString())
                priorityFeeState.setTextAndPlaceCursorAtEnd(spec.priorityFeeWei.toString())
            } catch (e: Exception) {
                // todo handle
                Timber.e(e)
            }
        }
    }

    private fun loadUTXOData(chain: Chain) {
        viewModelScope.launch {
            if (byteFeeState.text.toBigInteger() > BigInteger.ZERO)
                return@launch
            try {
                val stats = blockChairApi.getBlockChairStats(chain)
                val byte = stats.multiply(BigInteger("5")).divide(BigInteger("2"))
                byteFeeState.setTextAndPlaceCursorAtEnd(byte.toString())
            } catch (e: Exception) {
                Timber.e(e)
            }
        }
    }

    fun save(): GasSettings {
        return when (state.value.chainSpecific) {
            is BlockChainSpecific.Ethereum -> GasSettings.Eth(
                baseFee = baseFeeState.text.toString().toBigInteger(),
                priorityFee = priorityFeeState.text.toString().toBigInteger(),
                gasLimit = gasLimitState.text.toString().toBigInteger(),
            )

            is BlockChainSpecific.UTXO -> GasSettings.UTXO(
                byteFee = byteFeeState.text.toString().toBigInteger(),
            )

            else -> throw IllegalStateException("Unsupported chain specific")
        }
    }

    private fun CharSequence.toBigInteger() = try {
        BigInteger(toString())
    } catch (e: Exception) {
        BigInteger.ZERO
    }
}