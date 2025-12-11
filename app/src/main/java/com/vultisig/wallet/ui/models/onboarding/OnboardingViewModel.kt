package com.vultisig.wallet.ui.models.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    private val navigator: Navigator<Destination>,
) : ViewModel() {

    private val pages = 5

    val state = MutableStateFlow(
        OnboardingUiModel(
            pageIndex = 0,
            pageTotal = pages
        )
    )

    fun next() {
        viewModelScope.launch {
            val nextPageIndex = (state.value.pageIndex + 1).takeIf { it < pages }
            if (nextPageIndex != null) {
                state.update {
                    it.copy(
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
