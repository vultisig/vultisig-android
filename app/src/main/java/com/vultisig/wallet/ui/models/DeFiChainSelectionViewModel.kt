package com.vultisig.wallet.ui.models

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.data.models.DefiChainUiModel
import com.vultisig.wallet.data.models.isDeFiSupported
import com.vultisig.wallet.data.repositories.DefaultDeFiChainsRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.ui.models.mappers.ChainToDefiChainUiMapper
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.navigation.back
import com.vultisig.wallet.ui.utils.textAsFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

internal data class DeFiChainSelectionUiModel(
    val defiChains: List<SelectableDefiChainUiModel> = emptyList(),
    val isLoading: Boolean = true,
)

internal data class SelectableDefiChainUiModel(
    val defiChain: DefiChainUiModel,
    val isSelected: Boolean,
)

@HiltViewModel
internal class DeFiChainSelectionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val vaultRepository: VaultRepository,
    private val defaultDeFiChainsRepository: DefaultDeFiChainsRepository,
    private val mapChainDefi: ChainToDefiChainUiMapper,
    private val navigator: Navigator<Destination>,
) : ViewModel() {

    private val args = savedStateHandle.toRoute<Route.AddDeFiChainAccount>()
    private val vaultId: String = args.vaultId

    private val _uiState = MutableStateFlow(DeFiChainSelectionUiModel())
    val uiState = _uiState.asStateFlow()
    private val allChains = MutableStateFlow<List<SelectableDefiChainUiModel>>(emptyList())

    val searchTextFieldState = TextFieldState()

    init {
        loadChains()
        observeSearchQuery()
    }

    private fun loadChains() {
        viewModelScope.launch {
            val vault = vaultRepository.get(vaultId)

            _uiState.update {
                it.copy(
                    isLoading = true
                )
            }


            if (vault == null) {
                _uiState.update {
                    it.copy(
                        defiChains = emptyList(),
                        isLoading = false
                    )
                }
                return@launch
            }

            val availableChains = vault.coins.map { it.chain }.distinct().filter { it.isDeFiSupported }

            val savedDeFiChains = defaultDeFiChainsRepository.getDefaultChains(vaultId).first()

            val chains = availableChains.map {
                 mapChainDefi(it).toSelectable(it in savedDeFiChains)
            }
            allChains.update {
                chains
            }
            _uiState.update {
                it.copy(
                    defiChains = chains,
                    isLoading = false
                )
            }
        }
    }

    private fun observeSearchQuery() {
        viewModelScope.launch {
            combine(
                allChains,
                searchTextFieldState.textAsFlow(),
            ) { allChains, query ->
                allChains.filter { chain ->
                    query.isBlank() || chain.defiChain.raw.contains(
                        other = query,
                        ignoreCase = true
                    )
                }
            }.collect { filtered ->
                _uiState.update { state ->
                    state.copy(defiChains = filtered)
                }
            }
        }
    }


    fun toggleChain(checked: Boolean,chain: SelectableDefiChainUiModel) {
        val chains = _uiState.value.defiChains.map {
            if (it.defiChain.chain == chain.defiChain.chain) {
                it.copy(isSelected = checked)
            } else {
                it
            }
        }

        _uiState.update { state ->
            state.copy(defiChains = chains)
        }
    }

    fun saveSelection() {
        viewModelScope.launch {
            val chains = _uiState.value.defiChains
                .filter { it.isSelected }
                .map { it.defiChain.chain }
                .toSet()
            defaultDeFiChainsRepository.setDefaultChains(vaultId, chains)
            navigator.back()
        }
    }

    fun navigateBack() {
        viewModelScope.launch {
            navigator.back()
        }
    }

    fun onSearch(text: String) {
        searchTextFieldState.setTextAndPlaceCursorAtEnd(text)
    }

    private fun DefiChainUiModel.toSelectable(isSelected: Boolean) = SelectableDefiChainUiModel(
        defiChain = this,
        isSelected = isSelected
    )
}