package com.vultisig.wallet.ui.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Launches [onSessionStart] after a 1-second back-off in [scope] on the IO dispatcher, then
 * immediately calls [onDiscovery] on the caller's thread.
 */
internal fun launchMediatorSession(
    scope: CoroutineScope,
    onSessionStart: suspend () -> Unit,
    onDiscovery: () -> Unit,
) {
    scope.launch(Dispatchers.IO) {
        delay(1000)
        onSessionStart()
    }
    onDiscovery()
}
