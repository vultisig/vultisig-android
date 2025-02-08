package com.vultisig.wallet.ui.models.keygen

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.utils.textAsFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject


internal data class CharHandler(
    val textFieldState: TextFieldState,
    var enteredChar: Char?,
)

internal enum class FocusManagerEvent {
    MOVE_NEXT, MOVE_PREVIOUS, CLEAR_FOCUS,
}

internal enum class VerifyPinState {
    Idle, Loading, SUCCESS, ERROR
}

internal data class VaultBackupState(
    val verifyPinState: VerifyPinState = VerifyPinState.Idle,
    val sentEmailTo: String,
)

@HiltViewModel
internal class VaultBackupViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
) : ViewModel() {

    val email = savedStateHandle.toRoute<Route.VaultBackup>().email
    val state = MutableStateFlow(VaultBackupState(sentEmailTo = email))

    val charHandlers = List(PIN_DIGIT_COUNT) {
        CharHandler(
            textFieldState = TextFieldState(),
            enteredChar = null,
        )
    }

    val textFocusHandler = MutableSharedFlow<FocusManagerEvent?>()

    init {
        handlePinInput()
    }

    private fun handlePinInput() {
        viewModelScope.launch {
            charHandlers.forEach { charHandler ->
                launch {
                    charHandler.textFieldState.textAsFlow().collect { enteredText ->
                        if (enteredText.length == 1) {
                            charHandler.enteredChar = enteredText.first()
                            val charList = charHandlers.map { it.enteredChar }
                            if (charList.all { it != null }) {
                                verify()
                            } else {
                                textFocusHandler.emit(FocusManagerEvent.MOVE_NEXT)
                            }
                        }
                        if (enteredText.length > 1) {
                            charHandler.textFieldState.setTextAndPlaceCursorAtEnd(
                                enteredText.last().toString()
                            )
                        }
                    }
                }
            }
        }
    }

    fun paste(textInClipboard: String) {
        viewModelScope.launch {
            charHandlers
                .zip(textInClipboard.toList().take(charHandlers.size)) { ch, t ->
                    ch.textFieldState.setTextAndPlaceCursorAtEnd(t.toString())
                }
            textFocusHandler.emit(FocusManagerEvent.MOVE_NEXT)
        }
    }

    private fun verify() {
        viewModelScope.launch {
            val pinChars = charHandlers.map { it.enteredChar }
            if (checkPinHasError(pinChars)) {
                state.update {
                    it.copy(verifyPinState = VerifyPinState.ERROR)
                }
                return@launch
            }
            val pin = pinChars.joinToString(separator = "")

            state.update {
                it.copy(verifyPinState = VerifyPinState.Loading)
            }
            delay(3000)
            val s = if (pin == "1233")
                VerifyPinState.ERROR else VerifyPinState.SUCCESS
            state.update {
                it.copy(verifyPinState = s)
            }
        }
    }

    private fun checkPinHasError(chars: List<Char?>) =
        chars.any { it?.isDigit() == false } || chars.size != PIN_DIGIT_COUNT

    fun onBackSpacePressed(index: Int) {
        viewModelScope.launch {
            val charHandler = charHandlers[index]
            if (charHandler.enteredChar == null) {
                textFocusHandler.emit(FocusManagerEvent.MOVE_PREVIOUS)
            } else {
                charHandler.enteredChar = null
            }
        }
    }

    fun back() {
        viewModelScope.launch {
            navigator.navigate(Destination.Back)
        }
    }

    fun changeEmail() {

    }

    fun restartKeygen() {

    }

    companion object {
        private const val PIN_DIGIT_COUNT = 4
    }
}
