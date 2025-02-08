package com.vultisig.wallet.ui.models.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.repositories.onboarding.OnboardingRepository
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.NavigationOptions
import com.vultisig.wallet.ui.navigation.Navigator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class OnboardingSummaryViewModel @Inject constructor(
    private val onboardingRepository: OnboardingRepository,
    private val navigator: Navigator<Destination>
) : ViewModel() {

    val checkState = MutableStateFlow(false)

    fun toggleCheck(checked: Boolean) = viewModelScope.launch {
        checkState.value = checked
    }

    fun createVault() = viewModelScope.launch {
        if (!checkState.value) return@launch
        onboardingRepository.saveOnboardingState(true)
        navigator.navigate(
            dst = Destination.SelectVaultType,
            opts = NavigationOptions(
                popUpTo = Destination.AddVault.route,
            )
        )
    }
}