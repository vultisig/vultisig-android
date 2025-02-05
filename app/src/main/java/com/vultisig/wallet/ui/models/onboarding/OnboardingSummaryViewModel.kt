package com.vultisig.wallet.ui.models.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.repositories.OnboardingRepository
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class OnboardingSummaryViewModel @Inject constructor(
    private val onboardingRepository: OnboardingRepository,
    private val navigator: Navigator<Destination>
) : ViewModel() {

    fun createVault() = viewModelScope.launch {
        onboardingRepository.saveOnboardingState(true)
        navigator.navigate(Destination.SelectVaultType)
    }
}