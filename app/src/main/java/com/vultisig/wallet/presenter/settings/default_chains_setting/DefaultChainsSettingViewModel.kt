package com.vultisig.wallet.presenter.settings.default_chains_setting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.repositories.DefaultChainsRepository
import com.vultisig.wallet.presenter.settings.default_chains_setting.DefaultChainsSettingEvent.Initialize
import com.vultisig.wallet.presenter.settings.default_chains_setting.DefaultChainsSettingEvent.ChangeChaneState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class DefaultChainsSettingViewModel @Inject constructor(
    private val defaultChainsRepository: DefaultChainsRepository
) : ViewModel() {

    val state = MutableStateFlow(
        DefaultChainsUiModel(
            allDefaultChains = defaultChainsRepository.getAllDefaultChains().toUiModel()
        )
    )



    fun onEvent(event: DefaultChainsSettingEvent) {
        when (event) {
            Initialize -> initialize()
            is ChangeChaneState -> changeChaneState(event.chain, event.checked)
        }
    }

    private fun initialize() {
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
            val newSelection = if (isChecked) selectedDefaultChains + chain else selectedDefaultChains - chain
            defaultChainsRepository.setSelectedDefaultChains(newSelection.map { it.toDataModel() })
        }
    }


}