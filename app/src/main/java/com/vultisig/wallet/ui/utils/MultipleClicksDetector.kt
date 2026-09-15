package com.vultisig.wallet.ui.utils

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

internal class MultipleClicksDetector(
    private val timeSource: TimeSource,
    private val requiredClicks: Int = DEFAULT_REQUIRED_CLICKS,
    private val timeout: Duration = DEFAULT_TIMEOUT_MS.milliseconds,
) {

    private val clickTimestamps = mutableListOf<TimeMark>()

    fun clickAndCheckIfDetected(): Boolean {
        clickTimestamps.removeAll { timestamp -> timestamp.elapsedNow() > timeout }

        clickTimestamps.add(timeSource.markNow())

        if (clickTimestamps.size >= requiredClicks) {
            clickTimestamps.clear()
            return true
        } else {
            return false
        }
    }

    companion object {
        private const val DEFAULT_TIMEOUT_MS = 500L
        private const val DEFAULT_REQUIRED_CLICKS = 3
    }
}
