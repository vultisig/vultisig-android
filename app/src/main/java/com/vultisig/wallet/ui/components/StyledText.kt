package com.vultisig.wallet.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import com.vultisig.wallet.ui.theme.Theme

@Composable
fun StyledText(
    parts: List<StyledTextPart>,
    fontSize: TextUnit,
    fontFamily: androidx.compose.ui.text.font.FontFamily?,
    fontWeight: FontWeight?,
    textAlign: TextAlign = TextAlign.Center,
    defaultColor: Color = Theme.v2.colors.text.primary
) {
    Text(
        text = buildAnnotatedString {
            parts.forEach { part ->
                withStyle(
                    style = SpanStyle(
                        color = part.color ?: defaultColor,
                        fontSize = fontSize,
                        fontFamily = fontFamily,
                        fontWeight = fontWeight
                    )
                ) {
                    append(part.text)
                }
            }
        },
        color = defaultColor,
        textAlign = textAlign
    )
}

data class StyledTextPart(
    val text: String,
    val color: Color? = null
)