package com.vultisig.wallet.ui.models.keygen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.repositories.onboarding.OnboardingRepository
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

internal data class StartUiModel(
    val hasBackButton: Boolean = false,
)

@HiltViewModel
internal class StartViewModel @Inject constructor(
    private val onBoardingRepository: OnboardingRepository,
    private val vaultRepository: VaultRepository,
    private val navigator: Navigator<Destination>
) : ViewModel() {

    val state = MutableStateFlow(StartUiModel())

    init {
        viewModelScope.launch {
            val hasVaults = vaultRepository.hasVaults()
            state.update { it.copy(hasBackButton = hasVaults) }
        }
    }

    fun back() {
        viewModelScope.launch {
            navigator.navigate(Destination.Back)
        }
    }

    fun navigateToCreateVault() {
        viewModelScope.launch {
            val isUserPassedOnboarding = onBoardingRepository.readOnboardingState().first()
            if (isUserPassedOnboarding) {
                navigator.route(Route.ChooseVaultType)
            } else {
                navigator.route(Route.Onboarding.VaultCreation)
            }
        }
    }

    fun navigateToScanQrCode() {
        viewModelScope.launch {
            navigator.route(Route.ScanQr())
        }
    }

    fun navigateToImportVault() {
        viewModelScope.launch {
            navigator.route(Route.ImportVault())
        }
    }

    fun navigateToImportSeedphrase() {
        viewModelScope.launch {
            navigator.route(Route.KeyImport.ImportSeedphrase)
        }
    }

}