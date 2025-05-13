package com.vultisig.wallet.data.utils

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlin.time.Duration.Companion.seconds


fun timerFlow(): Flow<Long> = flow {
    var seconds = 0L
    while (currentCoroutineContext().isActive) {
        emit(seconds++)
        delay(1.seconds)
    }
}