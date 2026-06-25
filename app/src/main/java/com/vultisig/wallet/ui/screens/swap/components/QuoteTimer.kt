package com.vultisig.wallet.ui.screens.swap.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.data.models.SwapQuote.Companion.expiredAfter
import com.vultisig.wallet.data.utils.timerFlow
import com.vultisig.wallet.ui.theme.Theme
import java.util.Locale
import kotlin.time.Duration
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Countdown pill showing the remaining lifetime of the current swap quote.
 *
 * @param expiredAt the instant at which the quote expires; the timer counts down to it.
 */
@Composable
internal fun QuoteTimer(expiredAt: Instant, modifier: Modifier = Modifier) {
    var timeLeft: String by remember { mutableStateOf("") }
    var progress: Float by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(expiredAt) {
        timerFlow().collect {
            val now = Clock.System.now()
            val left = expiredAt - now
            timeLeft = formatDurationAsMinutesSeconds(left)
            progress = quoteTimerProgress(left, expiredAfter)
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier =
            modifier
                // Match the 44dp toolbar buttons (back / advanced) so the header row reads as one
                // consistent height, per Figma (#4858).
                .height(44.dp)
                .background(
                    color = Theme.v2.colors.backgrounds.secondary,
                    shape = RoundedCornerShape(99.dp),
                )
                .padding(horizontal = 16.dp),
    ) {
        Text(
            text = timeLeft,
            style = Theme.brockmann.supplementary.caption,
            color = Theme.v2.colors.text.secondary,
        )

        CircularProgressIndicator(
            progress = { progress },
            trackColor = Theme.v2.colors.border.normal,
            color = Theme.v2.colors.primary.accent4,
            strokeCap = StrokeCap.Square,
            strokeWidth = 2.dp,
            gapSize = 0.dp,
            modifier = Modifier.size(16.dp),
        )
    }
}

private fun formatDurationAsMinutesSeconds(duration: Duration): String {
    val totalSeconds = duration.inWholeSeconds.coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}

/**
 * Fraction of the quote's lifetime still remaining, clamped to the 0f..1f range
 * [CircularProgressIndicator] requires. [remaining] goes negative once the quote expires before it
 * is replaced, so the raw ratio is coerced rather than passed straight to the indicator.
 */
internal fun quoteTimerProgress(remaining: Duration, lifetime: Duration): Float =
    (remaining / lifetime).toFloat().coerceIn(0f, 1f)
