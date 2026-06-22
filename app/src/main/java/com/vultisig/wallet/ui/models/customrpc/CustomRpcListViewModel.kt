package com.vultisig.wallet.ui.models.customrpc

import androidx.annotation.DrawableRes
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.data.models.CustomRpcSupportedChains
import com.vultisig.wallet.data.models.logo
import com.vultisig.wallet.data.repositories.CustomRpcRepository
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.navigation.back
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class CustomRpcChainUiModel(
    val chainId: String,
    val chainName: String,
    @DrawableRes val logo: Int,
    val isCustom: Boolean,
)

@Immutable
internal data class CustomRpcListUiState(val chains: List<CustomRpcChainUiModel> = emptyList())

@HiltViewModel
internal class CustomRpcListViewModel
@Inject
constructor(
    private val navigator: Navigator<Destination>,
    private val customRpcRepository: CustomRpcRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val vaultId = savedStateHandle.toRoute<Route.CustomRpcList>().vaultId

    val searchTextFieldState = TextFieldState()

    private val _state = MutableStateFlow(CustomRpcListUiState())
    val state: StateFlow<CustomRpcListUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                    customRpcRepository.overrides,
                    snapshotFlow { searchTextFieldState.text.toString() },
                ) { overrides, query ->
                    val trimmed = query.trim()
                    CustomRpcSupportedChains.all
                        .filter { chain ->
                            trimmed.isEmpty() || chain.raw.contains(trimmed, ignoreCase = true)
                        }
                        .map { chain ->
                            CustomRpcChainUiModel(
                                chainId = chain.id,
                                chainName = chain.raw,
                                logo = chain.logo,
                                isCustom = overrides[chain] != null,
                            )
                        }
                }
                .collect { chains -> _state.update { it.copy(chains = chains) } }
        }
    }

    fun onChainClick(chainId: String) {
        // The Silver-tier gate lives at the vault Advanced Settings entry point (#4997); reaching
        // the picker already implies eligibility, so a tile tap goes straight to the editor.
        viewModelScope.launch { navigator.route(Route.CustomRpcDetail(vaultId, chainId)) }
    }

    fun back() {
        viewModelScope.launch { navigator.back() }
    }
}
