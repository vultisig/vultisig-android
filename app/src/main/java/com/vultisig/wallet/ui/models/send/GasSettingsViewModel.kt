package com.vultisig.wallet.ui.models.send

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshotFlow
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
import kotlinx.coroutines.launch
import timber.log.Timber

internal data class GasSettingsUiModel(
    val chainSpecific: BlockChainSpecific? = null,
    val byteFeeError: UiText? = null,
    // True while loadEthData's fetch is in flight, and left true if it fails: Save must not be
    // pressable while baseFee/priorityFee are blanked, or a slow/failed fetch would let the
    // user save a zero fee (issue #5397).
    val isLoadingEthFee: Boolean = false,
    // Tier values to fill in; null while loading or after a failure, which disables the chips.
    val showsPriority: Boolean = false,
    val priorityTiers: Map<PriorityFee, BigInteger>? = null,
    val selectedPriority: PriorityFee? = null,
)

/** The priority tiers of the advanced gas settings, in display order. */
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

    private var tiersJob: Job? = null

    init {
        // A chip stays selected only while its field still holds the value Save would sign.
        viewModelScope.launch {
            snapshotFlow { priorityFeeState.text.toString() to byteFeeState.text.toString() }
                .collect { deselectIfEdited() }
        }
    }

    /**
     * Fills the sheet for [chain]. With [saved] settings made for this chain the fields show those;
     * otherwise they load as before.
     */
    fun loadData(chain: Chain, spec: BlockChainSpecificAndUtxo, saved: GasSettings?) {
        val specific = spec.blockChainSpecific
        tiersJob?.cancel()

        state.update {
            it.copy(
                chainSpecific = specific,
                showsPriority = chain.hasPriorityTiers,
                priorityTiers = null,
                selectedPriority = null,
                // A failed fee fetch on an earlier chain must not keep this sheet disabled.
                isLoadingEthFee = false,
            )
        }

        when (specific) {
            is BlockChainSpecific.Ethereum -> {
                if (saved is GasSettings.Eth) showSavedEth(saved) else loadEthData(chain, specific)
                if (chain.hasPriorityTiers) loadEthTiers(chain, isSaved = saved is GasSettings.Eth)
            }

            is BlockChainSpecific.UTXO -> {
                if (saved is GasSettings.UTXO) {
                    byteFeeState.setTextAndPlaceCursorAtEnd(saved.byteFee.toString())
                }
                loadUTXOData(chain, isSaved = saved is GasSettings.UTXO)
            }

            else -> Unit
        }
    }

    /**
     * Fills the field a tier sets with [priority]'s value; does nothing while tiers are missing.
     */
    fun selectPriority(priority: PriorityFee) {
        val value = state.value.priorityTiers?.get(priority) ?: return
        when (state.value.chainSpecific) {
            is BlockChainSpecific.Ethereum ->
                priorityFeeState.setTextAndPlaceCursorAtEnd(convertWeiToGwei(value).toPlainString())
            is BlockChainSpecific.UTXO -> byteFeeState.setTextAndPlaceCursorAtEnd(value.toString())
            else -> return
        }
        state.update { it.copy(selectedPriority = priority) }
    }

    private fun showSavedEth(saved: GasSettings.Eth) {
        gasLimitState.setTextAndPlaceCursorAtEnd(saved.gasLimit.toString())
        baseFeeState.setTextAndPlaceCursorAtEnd(convertWeiToGwei(saved.baseFee).toPlainString())
        priorityFeeState.setTextAndPlaceCursorAtEnd(
            convertWeiToGwei(saved.priorityFee).toPlainString()
        )
        state.update { it.copy(isLoadingEthFee = false) }
    }

    private fun loadEthTiers(chain: Chain, isSaved: Boolean) {
        tiersJob =
            viewModelScope.safeLaunch(
                onError = { e ->
                    Timber.w(e, "Fee history unavailable for %s", chain)
                    showTiers(null, isSaved)
                }
            ) {
                val history = evmApiFactory.createEvmApi(chain).getFeeHistory()
                showTiers(evmPriorityFeeTiers(chain, history), isSaved)
            }
    }

    // A reopened sheet shows a chip only when the saved value is still exactly that tier's.
    private fun showTiers(tiers: Map<PriorityFee, BigInteger>?, isSaved: Boolean) {
        val current = currentTierFieldValue()
        val matching =
            if (isSaved && tiers != null && current != null) {
                PriorityFee.entries.firstOrNull { tiers[it] == current }
            } else null
        state.update { it.copy(priorityTiers = tiers, selectedPriority = matching) }
    }

    private fun deselectIfEdited() {
        val selected = state.value.selectedPriority ?: return
        val tierValue = state.value.priorityTiers?.get(selected)
        if (tierValue != currentTierFieldValue()) {
            state.update { it.copy(selectedPriority = null) }
        }
    }

    /** The value in the field a tier fills (priority fee in wei, or byte fee), as Save reads it. */
    private fun currentTierFieldValue(): BigInteger? =
        when (state.value.chainSpecific) {
            is BlockChainSpecific.Ethereum ->
                priorityFeeState.text.toString().toBigDecimalOrNull()?.let {
                    convertGweiToWei(it).toBigInteger()
                }
            is BlockChainSpecific.UTXO -> byteFeeState.text.toString().toBigIntegerOrNull()
            else -> null
        }

    private fun loadEthData(chain: Chain, spec: BlockChainSpecific.Ethereum) {
        gasLimitState.setTextAndPlaceCursorAtEnd(spec.gasLimit.toString())
        // This ViewModel is Hilt-scoped to the Send screen, not to one dialog open, so these
        // fields survive a close-without-saving. Blank them synchronously (before the network
        // fetch below) so a Save tapped for a newly opened chain, before that fetch resolves,
        // can never sign a fee carried over from a previous chain's session.
        baseFeeState.setTextAndPlaceCursorAtEnd("")
        priorityFeeState.setTextAndPlaceCursorAtEnd("")
        state.update { it.copy(isLoadingEthFee = true) }

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
                state.update { it.copy(isLoadingEthFee = false) }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Timber.e(e)
                // isLoadingEthFee stays true: the fields are still blank, so Save must stay
                // disabled rather than let this failure save a zero fee.
            }
        }
    }

    private fun loadUTXOData(chain: Chain, isSaved: Boolean) {
        val offersTiers = chain.hasPriorityTiers
        tiersJob = viewModelScope.safeLaunch {
            val hasValue = byteFeeState.text.toBigInteger() > BigInteger.ZERO
            if (hasValue && !offersTiers) return@safeLaunch
            val byteFee = utxoFeeService.getDefaultGasFee(chain)
            if (!hasValue) byteFeeState.setTextAndPlaceCursorAtEnd(byteFee.toString())
            if (offersTiers) showTiers(utxoByteFeeTiers(byteFee), isSaved)
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
