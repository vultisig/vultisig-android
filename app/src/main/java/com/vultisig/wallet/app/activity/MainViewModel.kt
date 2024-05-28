package com.vultisig.wallet.app.activity

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.repositories.OnBoardRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject


@HiltViewModel
internal class MainViewModel @Inject constructor(
    private val repository: OnBoardRepository,
    private val navigator: Navigator<Destination>,

    private val vaultRepository: VaultRepository,
) : ViewModel() {

    private val _isLoading: MutableState<Boolean> = mutableStateOf(true)
    val isLoading: State<Boolean> = _isLoading

    private val _startDestination: MutableState<String> = mutableStateOf(Screen.Home.route)
    val startDestination: State<String> = _startDestination

    val destination = navigator.destination

    init {
        viewModelScope.launch {
            if (vaultRepository.hasVaults()) {
                _startDestination.value = Screen.Home.route
                _isLoading.value = false
            } else {
                val isUserPassedOnboarding = repository.readOnBoardingState()
                    .first()

                if (isUserPassedOnboarding) {
                    _startDestination.value = Screen.CreateNewVault.route
                } else {
                    _startDestination.value = Screen.Welcome.route
                }

                _isLoading.value = false
            }
        }
    }

}
