package com.vultisig.wallet.ui.models.keygen

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.repositories.ChainImportSetting
import com.vultisig.wallet.data.repositories.DerivationPath
import com.vultisig.wallet.data.repositories.KeyImportRepository
import com.vultisig.wallet.data.usecases.ScanChainBalancesUseCase
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.navigation.back
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

internal enum class ChainsSetupState {
    Scanning, ActiveChains, NoActiveChains, CustomizeChains
}

@Immutable
internal data class ChainItemUiModel(
    val chain: Chain,
    val derivationPath: DerivationPath,
    val isSelected: Boolean,
)

@Immutable
internal data class KeyImportChainsSetupUiModel(
    val screenState: ChainsSetupState = ChainsSetupState.Scanning,
    val activeChains: List<ChainItemUiModel> = emptyList(),
    val allChains: List<ChainItemUiModel> = emptyList(),
    val filteredChains: List<ChainItemUiModel> = emptyList(),
    val selectedCount: Int = 0,
)

@HiltViewModel
internal class KeyImportChainsSetupViewModel @Inject constructor(
    private val navigator: Navigator<Destination>,
    private val keyImportRepository: KeyImportRepository,
    private val scanChainBalances: ScanChainBalancesUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(KeyImportChainsSetupUiModel())
    val state: StateFlow<KeyImportChainsSetupUiModel> = _state.asStateFlow()

    val searchTextFieldState = TextFieldState()

    init {
        startScanning()
        collectSearchQuery()
    }

    private fun collectSearchQuery() {
        viewModelScope.launch {
            snapshotFlow { searchTextFieldState.text }
                .collect { query ->
                    filterChains(query.toString())
                }
        }
    }

    private fun filterChains(query: String) {
        _state.update { current ->
            current.copy(filteredChains = applyFilter(current.allChains, query))
        }
    }

    private fun applyFilter(
        chains: List<ChainItemUiModel>,
        query: String = searchTextFieldState.text.toString(),
    ): List<ChainItemUiModel> =
        if (query.isBlank()) chains
        else chains.filter { it.chain.raw.contains(query, ignoreCase = true) }

    private fun startScanning() {
        viewModelScope.launch {
            try {
                val mnemonic = keyImportRepository.get()?.mnemonic
                    ?: error("No mnemonic found")

                val results = withContext(Dispatchers.IO) {
                    scanChainBalances(mnemonic)
                }
                val activeResults = results.filter { it.hasBalance }

                // Build the full chain list for CustomizeChains screen, pre-selecting
                // chains that have balance. Use the active result's derivation path
                // (e.g. Phantom for Solana) if one was found with balance.
                val allChainItems = Chain.keyImportSupportedChains
                    .map { chain ->
                        val hasBalance = activeResults.any { it.chain == chain }
                        ChainItemUiModel(
                            chain = chain,
                            derivationPath = results
                                .firstOrNull { it.chain == chain && it.hasBalance }
                                ?.derivationPath ?: DerivationPath.Default,
                            isSelected = hasBalance,
                        )
                    }

                if (activeResults.isNotEmpty()) {
                    val activeItems = activeResults.map { result ->
                        ChainItemUiModel(
                            chain = result.chain,
                            derivationPath = result.derivationPath,
                            isSelected = true,
                        )
                    }
                    _state.update {
                        it.copy(
                            screenState = ChainsSetupState.ActiveChains,
                            activeChains = activeItems,
                            allChains = allChainItems,
                            filteredChains = allChainItems,
                            selectedCount = activeItems.size,
                        )
                    }
                } else {
                    _state.update {
                        it.copy(
                            screenState = ChainsSetupState.NoActiveChains,
                            allChains = allChainItems,
                            filteredChains = allChainItems,
                            selectedCount = 0,
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to scan chain balances")
                // Fall back to manual selection
                val allChainItems = Chain.keyImportSupportedChains
                    .map { chain ->
                        ChainItemUiModel(
                            chain = chain,
                            derivationPath = DerivationPath.Default,
                            isSelected = false,
                        )
                    }
                _state.update {
                    it.copy(
                        screenState = ChainsSetupState.NoActiveChains,
                        allChains = allChainItems,
                        filteredChains = allChainItems,
                    )
                }
            }
        }
    }

    fun selectManually() {
        _state.update {
            it.copy(screenState = ChainsSetupState.CustomizeChains)
        }
    }

    fun customize() = selectManually()

    fun toggleChain(chain: Chain) {
        val updatedChains = _state.value.allChains.map {
            if (it.chain == chain) it.copy(isSelected = !it.isSelected) else it
        }
        _state.update {
            it.copy(
                allChains = updatedChains,
                filteredChains = applyFilter(updatedChains),
                selectedCount = updatedChains.count { c -> c.isSelected },
            )
        }
    }

    fun selectAll() {
        val updatedChains = _state.value.allChains.map { it.copy(isSelected = true) }
        _state.update {
            it.copy(
                allChains = updatedChains,
                filteredChains = applyFilter(updatedChains),
                selectedCount = updatedChains.size,
            )
        }
    }

    fun deselectAll() {
        val updatedChains = _state.value.allChains.map { it.copy(isSelected = false) }
        _state.update {
            it.copy(
                allChains = updatedChains,
                filteredChains = applyFilter(updatedChains),
                selectedCount = 0,
            )
        }
    }

    fun continueWithSelection() {
        val currentState = state.value
        // ActiveChains screen shows only chains with balance; CustomizeChains
        // shows all supported chains. Pick the list matching the current screen.
        val selectedChains = when (currentState.screenState) {
            ChainsSetupState.ActiveChains ->
                currentState.activeChains.filter { it.isSelected }

            ChainsSetupState.CustomizeChains ->
                currentState.allChains.filter { it.isSelected }

            else -> return
        }

        if (selectedChains.isEmpty()) return

        val settings = selectedChains.map {
            ChainImportSetting(
                chain = it.chain,
                derivationPath = it.derivationPath,
            )
        }

        keyImportRepository.setChainSettings(settings)

        viewModelScope.launch {
            navigator.route(Route.KeyImport.DeviceCount)
        }
    }

    fun back() {
        viewModelScope.launch {
            navigator.back()
        }
    }
}
