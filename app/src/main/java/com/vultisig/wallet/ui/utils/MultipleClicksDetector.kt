package com.vultisig.wallet.ui.utils

import com.vultisig.wallet.data.utils.minus
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

internal class MultipleClicksDetector(
    private val requiredClicks: Int = DEFAULT_REQUIRED_CLICKS,
    private val timeout: Duration = DEFAULT_TIMEOUT_MS.milliseconds,
) {

    private val clickTimestamps = mutableListOf<Instant>()

    fun clickAndCheckIfDetected(): Boolean {
        val currentTime = Instant.now()

        clickTimestamps.removeAll { timestamp -> currentTime - timestamp > timeout }

        clickTimestamps.add(currentTime)

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
