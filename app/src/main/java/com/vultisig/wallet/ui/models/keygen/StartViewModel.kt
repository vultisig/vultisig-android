package com.vultisig.wallet.ui.models.keygen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.repositories.onboarding.OnboardingRepository
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class StartViewModel @Inject constructor(
    private val onBoardingRepository: OnboardingRepository,
    private val navigator: Navigator<Destination>
) : ViewModel(){

    fun navigateToCreateVault(){
        viewModelScope.launch {
            val isUserPassedOnboarding = onBoardingRepository.readOnboardingState().first()
            if (isUserPassedOnboarding) {
                navigator.route(Route.ChooseVaultType)
            } else {
                navigator.route(Route.Onboarding.VaultCreation)
            }
        }
    }

    fun navigateToScanQrCode(){
        viewModelScope.launch {
            navigator.navigate(Destination.JoinThroughQr(null))
        }
    }

    fun navigateToImportVault(){
        viewModelScope.launch {
            navigator.route(Route.ImportVault())
        }
    }

}