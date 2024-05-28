package com.vultisig.wallet.presenter.welcome

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.repositories.OnBoardRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.presenter.common.UiEvent
import com.vultisig.wallet.presenter.common.UiEvent.NavigateTo
import com.vultisig.wallet.presenter.common.UiEvent.ScrollToNextPage
import com.vultisig.wallet.ui.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class WelcomeViewModel @Inject constructor(
    private val repository: OnBoardRepository,
    private val vaultsRepository: VaultRepository,
) : ViewModel() {

    var state by mutableStateOf(WelcomeState())
        private set
    private var _channel = Channel<UiEvent>()
    var channel = _channel.receiveAsFlow()

    init {
        getBoardPages()
    }

    fun scrollToNextPage() {
        viewModelScope.launch(Dispatchers.IO) {
            val dest = if (vaultsRepository.hasVaults())
                Screen.Home
            else Screen.CreateNewVault

            _channel.send(ScrollToNextPage(dest))
        }
    }

    fun skip() {
        saveOnBoardingState()
    }

    private fun getBoardPages() {
        state = state.copy(pages = repository.onBoardPages())
    }

    private fun saveOnBoardingState() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveOnBoardingState(completed = true)

            val dest = if (vaultsRepository.hasVaults())
                Screen.Home
            else Screen.CreateNewVault

            _channel.send(NavigateTo(dest))
        }
    }

}