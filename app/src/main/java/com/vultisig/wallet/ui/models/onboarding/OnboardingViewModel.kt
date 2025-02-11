package com.vultisig.wallet.ui.models.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
internal class OnboardingViewModel @Inject constructor(
    private val navigator: Navigator<Destination>
) : ViewModel() {

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
                navigator.route(Route.Onboarding.VaultCreationSummary)
            }
        }
    }

    fun skip() = viewModelScope.launch {
        navigator.route(Route.Onboarding.VaultCreationSummary)
    }

    fun back() = viewModelScope.launch {
        navigator.navigate(Destination.Back)
    }
}

private val pages = listOf(
    OnboardingPage(),
    OnboardingPage(),
    OnboardingPage(),
    OnboardingPage(),
    OnboardingPage(),
    OnboardingPage(),
)