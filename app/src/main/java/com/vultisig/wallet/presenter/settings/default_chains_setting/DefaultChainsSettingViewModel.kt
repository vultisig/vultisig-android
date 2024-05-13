package com.vultisig.wallet.presenter.settings.default_chains_setting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.repositories.TokenRepository
import com.vultisig.wallet.presenter.settings.default_chains_setting.DefaultChainsSettingEvent.Initialize
import com.vultisig.wallet.presenter.settings.default_chains_setting.DefaultChainsSettingEvent.UpdateItem
import com.vultisig.wallet.ui.models.ChainSelectionUiModel
import com.vultisig.wallet.ui.models.ChainUiModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class DefaultChainsSettingViewModel @Inject constructor(
    private val tokenRepository: TokenRepository,
) : ViewModel() {

    val state = MutableStateFlow(ChainSelectionUiModel())

    fun onEvent(event: DefaultChainsSettingEvent) {
        when (event) {
            Initialize -> loadChains()
            is UpdateItem -> onChainClick(event.chain, event.checked)
        }
    }

    private fun onChainClick(chain: ChainUiModel, checked: Boolean) {
        state.update {
            it.copy(
                chains = it.chains.map { item ->
                    item.copy(
                        isEnabled =
                        if (item == chain) checked else item.isEnabled
                    )
                }
            )
        }
    }

    private fun loadChains() {
        viewModelScope.launch {
            tokenRepository.nativeTokens.collect { chains ->
                state.update {
                    it.copy(chains = chains.map { nativeToken ->
                        ChainUiModel(
                            isEnabled = false,
                            coin = nativeToken,
                        )
                    })
                }
            }
        }
    }
}