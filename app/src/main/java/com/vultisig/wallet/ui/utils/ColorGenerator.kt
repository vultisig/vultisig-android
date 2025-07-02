package com.vultisig.wallet.ui.utils

import androidx.compose.ui.graphics.Color
import androidx.core.graphics.ColorUtils.HSLToColor
import kotlin.math.abs

internal object ColorGenerator {

    fun generate(
        value: Any?,
        saturation: Float = 0.75f,
        lightness: Float = 0.15f,
    ): Color {
        val hash = value.hashCode()
        val hueRanges = listOf(0..30, 60..360)
        val totalRange = hueRanges.sumOf { it.count() }
        val normalizedHash = abs(hash % totalRange)
        val hue = if (normalizedHash < hueRanges[0].count())
            hueRanges[0].first + normalizedHash
        else hueRanges[1].first + (normalizedHash - hueRanges[0].count())
        val hslColor = HSLToColor(floatArrayOf(hue.toFloat(), saturation, lightness))
        return Color(hslColor)
    }

}
