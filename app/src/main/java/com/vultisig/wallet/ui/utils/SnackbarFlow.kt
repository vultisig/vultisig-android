package com.vultisig.wallet.ui.utils

import com.vultisig.wallet.ui.components.v2.snackbar.SnackbarType
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

@Singleton
internal class SnackbarFlow @Inject constructor() {

    private val messageFlow = Channel<Pair<UiText, SnackbarType>>(capacity = Channel.BUFFERED)

    suspend fun showMessage(message: String, type: SnackbarType = SnackbarType.Success) {
        showMessage(message.asUiText(), type)
    }

    suspend fun showMessage(message: UiText, type: SnackbarType = SnackbarType.Success) {
        messageFlow.send(message to type)
    }

    suspend fun collectMessage(onMessageReceived: suspend (Pair<UiText, SnackbarType>) -> Unit) {
        messageFlow.receiveAsFlow().collect { (message, type) ->
            onMessageReceived(message to type)
        }
    }
}
