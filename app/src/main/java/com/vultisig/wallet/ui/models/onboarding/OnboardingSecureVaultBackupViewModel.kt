package com.vultisig.wallet.ui.models.onboarding

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.data.repositories.onboarding.OnboardingSecureBackupRepository
import com.vultisig.wallet.data.repositories.onboarding.OnboardingSecureBackupState
import com.vultisig.wallet.ui.models.onboarding.components.OnboardingPage
import com.vultisig.wallet.ui.models.onboarding.components.OnboardingUiModel
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.NavigationOptions
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class OnboardingSecureVaultBackupViewModel @Inject constructor(
    private val onboardingSecureBackupRepository: OnboardingSecureBackupRepository,
    private val navigator: Navigator<Destination>,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val vaultId = savedStateHandle.toRoute<Route.Onboarding.SecureVaultBackup>().vaultId

    val state = MutableStateFlow(
        OnboardingUiModel(
            currentPage = pages.first(),
            pageIndex = 0,
            pageTotal = pages.size
        )
    )

    fun next() {
        viewModelScope.launch {
            val nextAnimation = pages.getOrNull(state.value.pageIndex + 1)
            if (nextAnimation != null) {
                state.update {
                    it.copy(
                        currentPage = nextAnimation,
                        pageIndex = it.pageIndex + 1
                    )
                }
            } else {
                onboardingSecureBackupRepository.saveOnboardingState(OnboardingSecureBackupState.COMPLETED_MAIN)
                navigator.navigate(
                    dst = Destination.BackupSuggestion(
                        vaultId = vaultId
                    ),
                    opts = NavigationOptions(
                        popUpTo = Destination.Home().route,
                    )
                )
            }
        }
    }

    fun back() {}
}

private val pages = listOf(
    OnboardingPage(),
    OnboardingPage(),
)