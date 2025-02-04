package com.vultisig.wallet.ui.models.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.ui.models.onboarding.OnboardingPages.Screen1
import com.vultisig.wallet.ui.models.onboarding.OnboardingPages.Screen2
import com.vultisig.wallet.ui.models.onboarding.OnboardingPages.Screen3
import com.vultisig.wallet.ui.models.onboarding.OnboardingPages.Screen4
import com.vultisig.wallet.ui.models.onboarding.OnboardingPages.Screen5
import com.vultisig.wallet.ui.models.onboarding.OnboardingPages.Screen6
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

const val ONBOARDING_STATE_MACHINE_NAME = "State Machine 1"

internal data class OnboardingUiModel(
    val currentPage: OnboardingPages = Screen1,
    val pageIndex: Int = 0,
    val pageTotal: Int = pages.size
)

@HiltViewModel
internal class OnboardingViewModel @Inject constructor(
    private val navigator: Navigator<Destination>
) : ViewModel() {

    val uiState = MutableStateFlow(OnboardingUiModel())

    fun next() = viewModelScope.launch {
        val nextAnimation = pages.getOrNull(uiState.value.pageIndex + 1)
        if (nextAnimation != null) {
            uiState.update {
                it.copy(
                    currentPage = nextAnimation,
                    pageIndex = it.pageIndex + 1
                )
            }
        } else {
            navigator.navigate(Destination.OnboardingSummary)
        }
    }


    fun skip() = viewModelScope.launch {
        navigator.navigate(Destination.OnboardingSummary)
    }

    fun back() = viewModelScope.launch {
        navigator.navigate(Destination.Back)
    }
}

internal sealed class OnboardingPages(val triggerName: String = "Next") {
    data object Screen1 : OnboardingPages()
    data object Screen2 : OnboardingPages()
    data object Screen3 : OnboardingPages()
    data object Screen4 : OnboardingPages()
    data object Screen5 : OnboardingPages()
    data object Screen6 : OnboardingPages()
}

private val pages = listOf(
    Screen1,
    Screen2,
    Screen3,
    Screen4,
    Screen5,
    Screen6,
)