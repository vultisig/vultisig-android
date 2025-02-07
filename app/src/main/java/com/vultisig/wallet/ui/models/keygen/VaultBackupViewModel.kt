package com.vultisig.wallet.ui.models.keygen

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
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
    private val navigator: Navigator<Destination>,
) : ViewModel() {

    val email = "test@email.com" //change value from savedStateHandler
    val state = MutableStateFlow(VaultBackupState(sentEmailTo = email))


    val charHandlers = List(4) {
        CharHandler(
            textFieldState = TextFieldState(),
            enteredChar = null,
        )
    }

    val textFocusCommander = MutableSharedFlow<FocusManagerEvent?>()

    init {
        viewModelScope.launch {
            charHandlers.forEach { charHandler ->
                launch {
                    charHandler.textFieldState.textAsFlow().collect { enteredText ->
                        if (enteredText.length == 1) {
                            charHandler.enteredChar = enteredText.first()
                            val charList = charHandlers.map { it.enteredChar }
                            if (charList.all { it != null }) {
                                verify()
                            } else textFocusCommander.emit(FocusManagerEvent.MOVE_NEXT)
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
        charHandlers
            .zip(textInClipboard.toList().take(charHandlers.size)) { ch, t ->
                ch.textFieldState.setTextAndPlaceCursorAtEnd(t.toString())
            }
        viewModelScope.launch {
            textFocusCommander.emit(FocusManagerEvent.MOVE_NEXT)
        }
    }

    private fun verify() {
        viewModelScope.launch {
            val chars = charHandlers.map { it.enteredChar }
            if (verifyChars(chars))
                return@launch
            val pin = chars.joinToString(separator = "")

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

    private fun verifyChars(chars: List<Char?>): Boolean {
        if (chars.any { it?.isDigit() == false }) {
            state.update {
                it.copy(verifyPinState = VerifyPinState.ERROR)
            }
            return true
        }
        return false
    }

    fun onBackSpacePressed(index: Int) {
        viewModelScope.launch {
            val charHandler = charHandlers[index]
            if (charHandler.enteredChar == null) textFocusCommander.emit(FocusManagerEvent.MOVE_PREVIOUS)
            else {
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

    fun onRestartKeygenClick() {

    }
}
