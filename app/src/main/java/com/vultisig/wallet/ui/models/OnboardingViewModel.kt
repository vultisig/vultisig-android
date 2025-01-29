package com.vultisig.wallet.ui.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.repositories.OnBoardRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.ui.models.OnboardingPages.Screen1
import com.vultisig.wallet.ui.models.OnboardingPages.Screen2
import com.vultisig.wallet.ui.models.OnboardingPages.Screen3
import com.vultisig.wallet.ui.models.OnboardingPages.Screen4
import com.vultisig.wallet.ui.models.OnboardingPages.Screen5
import com.vultisig.wallet.ui.models.OnboardingPages.Screen6
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject


internal data class OnboardingUiModel(
    val currentPage: OnboardingPages = Screen1,
    val pageIndex: Int = 0,
    val pageTotal: Int = pages.size
)

@HiltViewModel
internal class OnboardingViewModel @Inject constructor(
    private val repository: OnBoardRepository,
    private val vaultsRepository: VaultRepository,
    private val navigator: Navigator<Destination>
) : ViewModel() {

    val uiState = MutableStateFlow(OnboardingUiModel())

    fun next() {
        viewModelScope.launch {
            val nextAnimation = pages.getOrNull(uiState.value.pageIndex + 1)
            if (nextAnimation != null) {
                uiState.update {
                    it.copy(
                        currentPage = nextAnimation,
                        pageIndex = it.pageIndex + 1
                    )
                }
            } else {
                saveOnBoardingState()
            }
        }
    }

    fun skip() {
        saveOnBoardingState()
    }

    private fun saveOnBoardingState() {
        viewModelScope.launch {
            repository.saveOnBoardingState(completed = true)

            val dest = if (vaultsRepository.hasVaults())
                Destination.Home()
            else Destination.AddVault

            navigator.navigate(dest)
        }
    }
}

internal sealed class OnboardingPages(val animationName: String) {
    data object Screen1 : OnboardingPages("Screen 1")
    data object Screen2 : OnboardingPages("Screen 2")
    data object Screen3 : OnboardingPages("Screen 3")
    data object Screen4 : OnboardingPages("Screen 4")
    data object Screen5 : OnboardingPages("Screen 5")
    data object Screen6 : OnboardingPages("Screen 6")
}

private val pages = listOf(
    Screen1,
    Screen2,
    Screen3,
    Screen4,
    Screen5,
    Screen6,
)