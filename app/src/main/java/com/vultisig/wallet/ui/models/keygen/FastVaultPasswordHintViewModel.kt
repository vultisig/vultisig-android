package com.vultisig.wallet.ui.models.keygen

import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.R
import com.vultisig.wallet.data.utils.TextFieldUtils.HINT_MAX_LENGTH
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.textAsFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

internal data class FastVaultPasswordHintState(
    val errorMessage: UiText? = null,
)

@HiltViewModel
internal class FastVaultPasswordHintViewModel @Inject constructor(
    private val navigator: Navigator<Destination>,
) : ViewModel() {

    val state = MutableStateFlow(FastVaultPasswordHintState())
    val textFieldState: TextFieldState = TextFieldState()

    init {
        viewModelScope.launch {
            textFieldState.textAsFlow().collect {
                validateHint(it)
            }
        }
    }

    private fun validateHint(hint: CharSequence) {
        val errorMessage =
            if (hint.length > HINT_MAX_LENGTH)
                UiText.StringResource(R.string.vault_password_hint_to_long)
            else null

        state.update {
            it.copy(errorMessage = errorMessage)
        }
    }

    fun back() {
        viewModelScope.launch {
            navigator.navigate(Destination.Back)
        }
    }
}