package com.vultisig.wallet.ui.utils

import com.vultisig.wallet.ui.components.v2.snackbar.SnackbarType
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

@Singleton
internal class SnackbarFlow @Inject constructor() {

    private val messageFlow = Channel<Pair<String, SnackbarType>>()

    suspend fun showMessage(message: String, type: SnackbarType = SnackbarType.Success) {
        messageFlow.send(message to type)
    }

    suspend fun collectMessage(onMessageReceived: suspend (Pair<String, SnackbarType>) -> Unit) {
        messageFlow.receiveAsFlow().collect { if (it.first.isNotBlank()) onMessageReceived(it) }
    }
}
