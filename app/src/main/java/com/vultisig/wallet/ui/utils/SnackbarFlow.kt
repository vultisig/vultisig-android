package com.vultisig.wallet.ui.utils

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class SnackbarFlow @Inject constructor() {

    private val messageFlow = Channel<String?>()

    suspend fun showMessage(message: String) {
        messageFlow.send(message)
    }

    suspend fun collectMessage(onMessageReceived: suspend (String) -> Unit) {
        messageFlow.receiveAsFlow().collect {
            if (!it.isNullOrBlank())
                onMessageReceived(it)
        }
    }
}