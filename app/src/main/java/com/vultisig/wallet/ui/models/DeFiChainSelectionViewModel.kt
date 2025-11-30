package com.vultisig.wallet.ui.models

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.isDeFiSupported
import com.vultisig.wallet.data.repositories.DefaultDeFiChainsRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.navigation.back
import com.vultisig.wallet.ui.utils.textAsFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

internal data class DeFiChainSelectionUiModel(
    val allChains: List<Chain> = emptyList(),
    val chains: List<Chain> = emptyList(),
    val selectedChains: Set<Chain> = emptySet(),
    val isLoading: Boolean = true,
)

@HiltViewModel
internal class DeFiChainSelectionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val vaultRepository: VaultRepository,
    private val defaultDeFiChainsRepository: DefaultDeFiChainsRepository,
    private val navigator: Navigator<Destination>,
) : ViewModel() {

    private val args = savedStateHandle.toRoute<Route.AddDeFiChainAccount>()
    private val vaultId: String = args.vaultId

    private val _uiState = MutableStateFlow(DeFiChainSelectionUiModel())
    val uiState = _uiState.asStateFlow()
    
    val searchTextFieldState = TextFieldState()

    init {
        loadChains()
        observeSearchQuery()
    }

    private fun loadChains() {
        viewModelScope.launch {
            val vault = vaultRepository.get(vaultId)

            if (vault == null) {
                _uiState.update {
                    it.copy(allChains = emptyList(), chains = emptyList(), selectedChains = emptySet(), isLoading = false)
                }
                return@launch
            }

            val availableChains = vault.coins.map { it.chain }.distinct().filter { it.isDeFiSupported }
            val savedDeFiChains = defaultDeFiChainsRepository.getDefaultChains(vaultId).first()
            
            _uiState.update {
                it.copy(
                    allChains = availableChains,
                    chains = availableChains,
                    selectedChains = savedDeFiChains.intersect(availableChains.toSet()),
                    isLoading = false
                )
            }
        }
    }

    private fun observeSearchQuery() {
        viewModelScope.launch {
            combine(
                _uiState.map { it.allChains }.distinctUntilChanged(),
                searchTextFieldState.textAsFlow().map { it.toString() }.distinctUntilChanged(),
            ) { allChains, queryStr ->
                if (queryStr.isBlank()) {
                    allChains
                } else {
                    allChains.filter { chain ->
                        chain.raw.contains(queryStr, ignoreCase = true)
                    }
                }
            }.collect { filtered ->
                _uiState.update { state ->
                    state.copy(chains = filtered)
                }
            }
        }
    }


    fun toggleChain(chain: Chain) {
        _uiState.update { state ->
            val newSelection = if (chain in state.selectedChains) {
                state.selectedChains - chain
            } else {
                state.selectedChains + chain
            }
            state.copy(selectedChains = newSelection)
        }
    }

    fun saveSelection() {
        viewModelScope.launch {
            defaultDeFiChainsRepository.setDefaultChains(vaultId, _uiState.value.selectedChains)
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
}