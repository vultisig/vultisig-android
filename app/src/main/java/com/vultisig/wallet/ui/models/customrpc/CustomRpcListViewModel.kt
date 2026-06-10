package com.vultisig.wallet.ui.models.customrpc

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class CustomRpcRowUiModel(
    val chainId: String,
    val chainName: String,
    @DrawableRes val logo: Int,
    val customUrl: String?,
) {
    val isCustom: Boolean
        get() = customUrl != null
}

@Immutable
internal data class CustomRpcListUiState(val rows: List<CustomRpcRowUiModel> = emptyList())

@HiltViewModel
internal class CustomRpcListViewModel
@Inject
constructor(
    private val navigator: Navigator<Destination>,
    private val customRpcRepository: CustomRpcRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val vaultId = savedStateHandle.toRoute<Route.CustomRpcList>().vaultId

    private val _state = MutableStateFlow(CustomRpcListUiState())
    val state: StateFlow<CustomRpcListUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            customRpcRepository.overrides.collect { overrides ->
                val rows =
                    CustomRpcSupportedChains.all.map { chain ->
                        CustomRpcRowUiModel(
                            chainId = chain.id,
                            chainName = chain.raw,
                            logo = chain.logo,
                            customUrl = overrides[chain],
                        )
                    }
                _state.update { it.copy(rows = rows) }
            }
        }
    }

    fun onRowClick(chainId: String) {
        // The Silver-tier gate lives at the settings entry point (#4787); reaching the list already
        // implies eligibility, so a row tap goes straight to the editor.
        viewModelScope.launch { navigator.route(Route.CustomRpcDetail(vaultId, chainId)) }
    }

    fun back() {
        viewModelScope.launch { navigator.back() }
    }
}
