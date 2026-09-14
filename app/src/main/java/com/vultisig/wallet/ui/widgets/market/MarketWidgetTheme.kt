package com.vultisig.wallet.ui.widgets.market

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.glance.text.FontWeight
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.vultisig.wallet.ui.theme.v2.V2

/**
 * Visual tokens for the market widgets, mirroring the Signal Flow spec. Glance renders through
 * RemoteViews, so these are plain [ColorProvider]s off the v2 palette rather than the composable
 * theme, and the brand fonts are replaced by the device default at medium weight (a font resource
 * can't be handed to a RemoteViews text span, and Glance has no hook for one).
 */
internal object MarketWidgetTheme {
    val background: Color = V2.colors.backgrounds.primary
    val primaryText = V2.colors.text.primary.provider()
    val secondaryText = V2.colors.text.secondary.provider()
    val tertiaryText = V2.colors.text.tertiary.provider()
    val separator = V2.colors.border.light.provider()

    val positiveColor: Color = V2.colors.alerts.success
    val negativeColor: Color = V2.colors.alerts.error
    val neutralColor: Color = V2.colors.text.tertiary
    val primaryTextColor: Color = V2.colors.text.primary

    fun label(size: TextUnit, color: ColorProvider = primaryText) =
        TextStyle(color = color, fontSize = size, fontWeight = FontWeight.Medium)

    fun changeColor(change: Double?): Color =
        when {
            change == null -> neutralColor
            change >= 0 -> positiveColor
            else -> negativeColor
        }

    private fun Color.provider(): ColorProvider = ColorProvider(this)
}
