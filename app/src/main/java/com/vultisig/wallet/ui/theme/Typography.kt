package com.vultisig.wallet.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.vultisig.wallet.R

private val menloFontFamily = FontFamily(
    Font(R.font.menlo_bold, weight = FontWeight.Bold),
    Font(R.font.menlo_bold, weight = FontWeight.SemiBold),
    Font(R.font.menlo_regular, weight = FontWeight.Normal),
    Font(R.font.menlo_regular, weight = FontWeight.Medium)
)

private val montserratFontFamily = FontFamily(
    Font(R.font.montserrat_bold, weight = FontWeight.Bold),
    Font(R.font.montserrat_bold, weight = FontWeight.SemiBold),
    Font(R.font.montserrat_regular, weight = FontWeight.Normal),
    Font(R.font.montserrat_regular, weight = FontWeight.Medium),
)

@Immutable
internal data class VultisigTypography(
    val heading1: TextStyle,
    val heading2: TextStyle,
    val heading3: TextStyle,
    val heading4: TextStyle,
    val heading5: TextStyle,
    val subtitle1: TextStyle,
    val subtitle2: TextStyle,
    val subtitle3: TextStyle,
    val body1: TextStyle,
    val body2: TextStyle,
    val body3: TextStyle,
    val caption: TextStyle,
    val overline: TextStyle,
    val overline2: TextStyle,
) {
    // TODO: aliases for old typography use, should be removed once not used
    @Deprecated("Use heading3 instead", ReplaceWith("heading3"))
    val headlineLarge: TextStyle
        get() = heading3
    @Deprecated("Use heading5 instead", ReplaceWith("heading5"))
    val headlineSmall: TextStyle
        get() = heading5
    @Deprecated("Use subtitle3 instead", ReplaceWith("subtitle3"))
    val titleSmall: TextStyle
        get() = subtitle3
    @Deprecated("Use subtitle1 instead", ReplaceWith("subtitle1"))
    val titleLarge: TextStyle
        get() = subtitle1
    @Deprecated("Use subtitle2 instead", ReplaceWith("subtitle2"))
    val titleMedium: TextStyle
        get() = subtitle2
    @Deprecated("Use body1 instead", ReplaceWith("body1"))
    val bodyLarge: TextStyle
        get() = body1
    @Deprecated("Use body2 instead", ReplaceWith("body2"))
    val bodyMedium: TextStyle
        get() = body2
    @Deprecated("Use overline instead", ReplaceWith("overline"))
    val labelMedium: TextStyle
        get() = overline

    companion object {
        fun createFrom(fontFamily: FontFamily): VultisigTypography =
            VultisigTypography(
                heading1 = TextStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = 50.sp,
                    fontFamily = fontFamily,
                ),
                heading2 = TextStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = 40.sp,
                    fontFamily = fontFamily,
                ),
                heading3 = TextStyle(
                    fontWeight = FontWeight.Normal,
                    fontSize = 40.sp,
                    fontFamily = fontFamily,
                ),
                heading4 = TextStyle(
                    fontWeight = FontWeight.Normal,
                    fontSize = 28.sp,
                    fontFamily = fontFamily,
                ),
                heading5 = TextStyle(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 20.sp,
                    fontFamily = fontFamily,
                ),
                subtitle1 = TextStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    fontFamily = fontFamily,
                ),
                subtitle2 = TextStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    fontFamily = fontFamily,
                ),
                subtitle3 = TextStyle(
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    fontFamily = fontFamily,
                ),
                body1 = TextStyle(
                    fontWeight = FontWeight.Normal,
                    fontSize = 12.sp,
                    fontFamily = fontFamily,
                ),
                body2 = TextStyle(
                    fontWeight = FontWeight.Normal,
                    fontSize = 14.sp,
                    fontFamily = fontFamily,
                ),
                body3 = TextStyle(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    fontFamily = fontFamily,
                ),
                caption = TextStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    fontFamily = fontFamily,
                ),
                overline = TextStyle(
                    fontWeight = FontWeight.Normal,
                    fontSize = 12.sp,
                    fontFamily = fontFamily,
                ),
                overline2 = TextStyle(
                    fontWeight = FontWeight.Normal,
                    fontSize = 12.sp,
                    fontFamily = fontFamily,
                ),
            )
    }
}

internal val menloTypography = VultisigTypography.createFrom(menloFontFamily)

internal val montserratTypography = VultisigTypography.createFrom(montserratFontFamily)