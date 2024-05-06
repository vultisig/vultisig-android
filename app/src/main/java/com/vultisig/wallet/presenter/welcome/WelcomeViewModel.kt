package com.vultisig.wallet.presenter.welcome

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.on_board.OnBoardRepository
import com.vultisig.wallet.presenter.common.UiEvent
import com.vultisig.wallet.presenter.common.UiEvent.NavigateTo
import com.vultisig.wallet.presenter.common.UiEvent.PopBackStack
import com.vultisig.wallet.presenter.common.UiEvent.ScrollToNextPage
import com.vultisig.wallet.presenter.navigation.Screen
import com.vultisig.wallet.presenter.welcome.WelcomeEvent.BoardCompleted
import com.vultisig.wallet.presenter.welcome.WelcomeEvent.InitPages
import com.vultisig.wallet.presenter.welcome.WelcomeEvent.NextPages
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WelcomeViewModel @Inject constructor(
    private val repository: OnBoardRepository
) : ViewModel() {

    var state by mutableStateOf(WelcomeState())
        private set
    private var _channel = Channel<UiEvent>()
    var channel = _channel.receiveAsFlow()

    init {
        onEvent(InitPages)
    }

    fun onEvent(event: WelcomeEvent) {
        when (event) {
            InitPages -> getBoardPages()
            NextPages -> scrollToNextPage()
            BoardCompleted -> saveOnBoardingState()
        }
    }
    private fun getBoardPages() {
        state = state.copy(pages = repository.onBoardPages())
    }

    private fun saveOnBoardingState() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveOnBoardingState(completed = true)
            _channel.send(NavigateTo(Screen.Home))
            _channel.send(PopBackStack)
        }
    }
    private fun scrollToNextPage() {
        viewModelScope.launch(Dispatchers.IO) {
            _channel.send(ScrollToNextPage(Screen.Home))
        }
    }

}