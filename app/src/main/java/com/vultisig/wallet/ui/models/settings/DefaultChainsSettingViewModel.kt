package com.vultisig.wallet.ui.models.settings

import androidx.annotation.DrawableRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.crypto.ticker
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.logo
import com.vultisig.wallet.data.repositories.DefaultChainsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject


internal data class DefaultChainsUiModel(
    val chains: List<DefaultChain> = emptyList(),
    val selectedDefaultChains: List<DefaultChain> = emptyList(),
)

internal data class DefaultChain(
    val title: String,
    val subtitle: String,
    @DrawableRes val logo: Int,
)

@HiltViewModel
internal class DefaultChainsSettingViewModel @Inject constructor(
    private val defaultChainsRepository: DefaultChainsRepository,
) : ViewModel() {

    val state = MutableStateFlow(
        DefaultChainsUiModel(
            chains = Chain.entries.toUiModel()
                .sortedBy { it.title }
        )
    )

    fun initialize() {
        viewModelScope.launch {
            defaultChainsRepository.selectedDefaultChains.collect { chains ->
                state.update {
                    it.copy(selectedDefaultChains = chains.toUiModel())
                }
            }
        }
    }


    fun changeChaneState(chain: DefaultChain, isChecked: Boolean) {
        viewModelScope.launch {
            val selectedDefaultChains = state.value.selectedDefaultChains
            val newSelection =
                if (isChecked) selectedDefaultChains + chain else selectedDefaultChains - chain
            defaultChainsRepository.setSelectedDefaultChains(newSelection.map { it.toDataModel() })
        }
    }


    private fun DefaultChain.toDataModel() = Chain.entries.first { it.raw == title }

    private fun Chain.toUiModel() = DefaultChain(
        title = raw,
        subtitle = ticker(),
        logo = logo
    )

    private fun List<Chain>.toUiModel() = map { it.toUiModel() }


}

