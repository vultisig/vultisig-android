package com.vultisig.wallet.ui.models.keygen

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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.coroutines.cancellation.CancellationException
import javax.inject.Inject

internal enum class ChainsSetupState {
    Scanning, ActiveChains, NoActiveChains, CustomizeChains
}

internal data class ChainItemUiModel(
    val chain: Chain,
    val derivationPath: DerivationPath,
    val isSelected: Boolean,
)

internal data class KeyImportChainsSetupUiModel(
    val screenState: ChainsSetupState = ChainsSetupState.Scanning,
    val activeChains: List<ChainItemUiModel> = emptyList(),
    val allChains: List<ChainItemUiModel> = emptyList(),
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


    init {
        startScanning()
    }

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
                            selectedCount = activeItems.size,
                        )
                    }
                } else {
                    _state.update {
                        it.copy(
                            screenState = ChainsSetupState.NoActiveChains,
                            allChains = allChainItems,
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
        _state.update { current ->
            val updatedChains = current.allChains.map {
                if (it.chain == chain) it.copy(isSelected = !it.isSelected) else it
            }
            current.copy(
                allChains = updatedChains,
                selectedCount = updatedChains.count { it.isSelected },
            )
        }
    }

    fun selectAll() {
        _state.update { current ->
            val updatedChains = current.allChains.map { it.copy(isSelected = true) }
            current.copy(
                allChains = updatedChains,
                selectedCount = updatedChains.size,
            )
        }
    }

    fun deselectAll() {
        _state.update { current ->
            val updatedChains = current.allChains.map { it.copy(isSelected = false) }
            current.copy(
                allChains = updatedChains,
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
            navigator.navigate(Destination.Back)
        }
    }
}
