package com.vultisig.wallet.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
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

private val brockmannFontFamily = FontFamily(
    Font(R.font.brockmann_bold, weight = FontWeight.Bold),
    Font(R.font.brockmann_semibold, weight = FontWeight.SemiBold),
    Font(R.font.brockmann_regular, weight = FontWeight.Normal),
    Font(R.font.brockmann_medium, weight = FontWeight.Medium),
)

@Immutable
internal data class VsTypography(
    val headings: Headings,
    val body: Body,
    val supplementary: Supplementary,
    val button: Button,
) {

    @Immutable
    data class Headings(
        val headline: TextStyle,
        val largeTitle: TextStyle,
        val title1: TextStyle,
        val title2: TextStyle,
        val title3: TextStyle,
        val subtitle: TextStyle,
    )

    @Immutable
    data class Body(
        val l: BodyStyles,
        val m: BodyStyles,
        val s: BodyStyles,
    )

    @Immutable
    data class BodyStyles(
        val medium: TextStyle,
        val regular: TextStyle,
    )

    @Immutable
    data class Supplementary(
        val caption: TextStyle,
        val footnote: TextStyle,
    )

    @Immutable
    data class Button(
        val large: TextStyle,
        val semibold: TextStyle,
        val medium: TextStyle,
    )

    companion object {

        private val lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Proportional,
            trim = LineHeightStyle.Trim.None,
        )

        fun createFrom(fontFamily: FontFamily): VsTypography =
            VsTypography(
                headings = Headings(
                    headline = TextStyle(
                        fontWeight = FontWeight.Medium,
                        fontSize = 48.sp,
                        lineHeight = 56.sp,
                        letterSpacing = (-1).sp,
                        fontFamily = fontFamily,
                        lineHeightStyle = lineHeightStyle,
                    ),
                    largeTitle = TextStyle(
                        fontWeight = FontWeight.Medium,
                        fontSize = 34.sp,
                        lineHeight = 37.sp,
                        letterSpacing = (-1).sp,
                        fontFamily = fontFamily,
                        lineHeightStyle = lineHeightStyle,
                    ),
                    title1 = TextStyle(
                        fontWeight = FontWeight.Medium,
                        fontSize = 28.sp,
                        lineHeight = 34.sp,
                        letterSpacing = (-0.64).sp,
                        fontFamily = fontFamily,
                        lineHeightStyle = lineHeightStyle,
                    ),
                    title2 = TextStyle(
                        fontWeight = FontWeight.Medium,
                        fontSize = 22.sp,
                        lineHeight = 24.sp,
                        letterSpacing = (-0.36).sp,
                        fontFamily = fontFamily,
                        lineHeightStyle = lineHeightStyle,
                    ),
                    title3 = TextStyle(
                        fontWeight = FontWeight.Medium,
                        fontSize = 17.sp,
                        lineHeight = 20.sp,
                        letterSpacing = (-0.3).sp,
                        fontFamily = fontFamily,
                        lineHeightStyle = lineHeightStyle,
                    ),
                    subtitle = TextStyle(
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                        lineHeight = 17.sp,
                        letterSpacing = (-0.18).sp,
                        fontFamily = fontFamily,
                        lineHeightStyle = lineHeightStyle,
                    ),
                ),
                body = Body(
                    l = BodyStyles(
                        medium = TextStyle(
                            fontWeight = FontWeight.Medium,
                            fontSize = 18.sp,
                            lineHeight = 28.sp,
                            letterSpacing = (-0.09).sp,
                            fontFamily = fontFamily,
                            lineHeightStyle = lineHeightStyle,
                        ),
                        regular = TextStyle(
                            fontWeight = FontWeight.Normal,
                            fontSize = 18.sp,
                            lineHeight = 28.sp,
                            letterSpacing = (-0.09).sp,
                            fontFamily = fontFamily,
                            lineHeightStyle = lineHeightStyle,
                        ),
                    ),
                    m = BodyStyles(
                        medium = TextStyle(
                            fontWeight = FontWeight.Medium,
                            fontSize = 16.sp,
                            lineHeight = 24.sp,
                            fontFamily = fontFamily,
                            lineHeightStyle = lineHeightStyle,
                        ),
                        regular = TextStyle(
                            fontWeight = FontWeight.Normal,
                            fontSize = 16.sp,
                            lineHeight = 24.sp,
                            fontFamily = fontFamily,
                            lineHeightStyle = lineHeightStyle,
                        ),
                    ),
                    s = BodyStyles(
                        medium = TextStyle(
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            fontFamily = fontFamily,
                            lineHeightStyle = lineHeightStyle,
                        ),
                        regular = TextStyle(
                            fontWeight = FontWeight.Normal,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            fontFamily = fontFamily,
                            lineHeightStyle = lineHeightStyle,
                        ),
                    )
                ),
                supplementary = Supplementary(
                    caption = TextStyle(
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        letterSpacing = 0.12.sp,
                        fontFamily = fontFamily,
                        lineHeightStyle = lineHeightStyle,
                    ),
                    footnote = TextStyle(
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        letterSpacing = 0.06.sp,
                        fontFamily = fontFamily,
                        lineHeightStyle = lineHeightStyle,
                    ),
                ),
                button = Button(
                    large = TextStyle(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        lineHeight = 20.sp,
                        fontFamily = fontFamily,
                        lineHeightStyle = lineHeightStyle,
                    ),
                    semibold = TextStyle(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        lineHeight = 20.sp,
                        fontFamily = fontFamily,
                        lineHeightStyle = lineHeightStyle,
                    ),
                    medium = TextStyle(
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        fontFamily = fontFamily,
                        lineHeightStyle = lineHeightStyle,
                    ),
                ),
            )
    }
}

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
                    lineHeight = 30.sp,
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

internal val brockmannTypography = VsTypography.createFrom(brockmannFontFamily)