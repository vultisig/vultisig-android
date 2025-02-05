package com.vultisig.wallet.ui.models.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.repositories.onboarding.OnboardingSecureBackupRepository
import com.vultisig.wallet.data.repositories.onboarding.OnboardingSecureBackupState
import com.vultisig.wallet.ui.models.onboarding.components.OnboardingPage
import com.vultisig.wallet.ui.models.onboarding.components.OnboardingUiModel
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class OnboardingSecurityVaultBackupViewModel @Inject constructor(
    private val onboardingSecureBackupRepository: OnboardingSecureBackupRepository,
    private val navigator: Navigator<Destination>
) : ViewModel() {

    val state = MutableStateFlow(
        OnboardingUiModel(
            currentPage = pages.first(),
            pageTotal = pages.size
        )
    )

    fun next() {
        viewModelScope.launch {
            val nextAnimation = pages.getOrNull(state.value.currentPage.index + 1)
            if (nextAnimation != null) {
                state.update {
                    it.copy(
                        currentPage = nextAnimation,
                    )
                }
            } else {
                onboardingSecureBackupRepository.saveOnboardingState(OnboardingSecureBackupState.CompletedMain)
                navigator.route(Route.VaultInfo.Name(Route.VaultInfo.VaultType.Secure))//TODO:
            }
        }
    }

    fun back() {}
}

private val pages = listOf(
    OnboardingPage(0),
    OnboardingPage(1),
)