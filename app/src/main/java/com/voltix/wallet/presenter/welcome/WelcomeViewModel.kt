package com.voltix.wallet.presenter.welcome

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voltix.wallet.on_board.use_cases.BoardPages
import com.voltix.wallet.on_board.use_cases.SaveOnBoard
import com.voltix.wallet.presenter.common.UiEvent
import com.voltix.wallet.presenter.common.UiEvent.*
import com.voltix.wallet.presenter.navigation.Screen
import com.voltix.wallet.presenter.welcome.WelcomeEvent.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WelcomeViewModel @Inject constructor(
    private val saveOnBoard: SaveOnBoard,
    private val boardPage: BoardPages
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
            NextPages -> getNextPages()
            BoardCompleted -> saveOnBoardingState()
        }
    }
    private fun getNextPages() {
        state = state.copy(pages = boardPage())
    }
    private fun getBoardPages() {
        state = state.copy(pages = boardPage())
    }

    private fun saveOnBoardingState() {
        viewModelScope.launch(Dispatchers.IO) {
            saveOnBoard(completed = true)
            _channel.send(NavigateTo(Screen.Home))
            _channel.send(PopBackStack)
        }
    }

}