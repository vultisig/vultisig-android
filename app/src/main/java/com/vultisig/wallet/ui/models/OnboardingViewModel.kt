package com.vultisig.wallet.ui.models

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.OnBoardPage
import com.vultisig.wallet.data.repositories.OnBoardRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.ui.models.OnboardingAnimations.*
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject


data class OnboardingState(
    val currentAnimation: String? = null,
    val animationNumber: Int = 0,
)

@HiltViewModel
internal class OnboardingViewModel @Inject constructor(
    private val repository: OnBoardRepository,
    private val vaultsRepository: VaultRepository,
    private val navigator: Navigator<Destination>
) : ViewModel() {

    val uiState = MutableStateFlow(OnboardingState())

    fun next() {
        viewModelScope.launch {
            val nextAnimation = animations.getOrNull(uiState.value.animationNumber + 1)
            if (nextAnimation != null) {
                uiState.update {
                    it.copy(
                        currentAnimation = nextAnimation.animation,
                        animationNumber = it.animationNumber + 1
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
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveOnBoardingState(completed = true)

            val dest = if (vaultsRepository.hasVaults())
                Destination.Home()
            else Destination.AddVault

            navigator.navigate(dest)
        }
    }
}

sealed class OnboardingAnimations(val animation: String) {
    data object Screen1 : OnboardingAnimations("Screen 1")
    data object Screen2 : OnboardingAnimations("Screen 2")
    data object Screen3 : OnboardingAnimations("Screen 3")
    data object Screen4 : OnboardingAnimations("Screen 4")
    data object Screen5 : OnboardingAnimations("Screen 5")
    data object Screen6 : OnboardingAnimations("Screen 6")
}

private val animations = listOf(
    Screen1,
    Screen2,
    Screen3,
    Screen4,
    Screen5,
    Screen6
)