package com.vultisig.wallet.ui.components
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.ui.components.library.UiCirclesLoader
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun MultiColorButton(
    text: String = "",
    startIcon: Int? = null,
    trailingIcon: Int? = null,
    iconColor: Color? = null,
    backgroundColor: Color? = null,
    isLoading: Boolean = false,
    disabled: Boolean = false,
    iconSize: Dp? = null,
    borderSize: Dp? = null,
    minWidth: Dp? = null,
    minHeight: Dp? = null,
    textStyle: TextStyle? = null,
    textColor: Color? = null,
    modifier: Modifier,
    onClick: () -> Unit,
    content : (@Composable ()->Unit)? = null,
) {
    var innerModifier = modifier
    val appColor = Theme.colors
    if (borderSize != null)
        innerModifier = innerModifier.then(
            Modifier.border(
                width = borderSize,
                brush = Brush.horizontalGradient(
                    listOf(
                        appColor.turquoise600Main,
                        appColor.persianBlue600Main
                    )
                ),
                shape = CircleShape
            )
        )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = innerModifier
            .clip(shape = RoundedCornerShape(60.dp))
            .background(
                color = if (disabled) appColor.neutral600
                else backgroundColor ?: appColor.turquoise600Main
            )
            .defaultMinSize(
                minWidth = minWidth ?: 170.dp,
                minHeight = minHeight ?: 44.dp
            )
            .clickOnce(
                onClick = onClick,
                enabled = !disabled && !isLoading,
            )
    ) {
        if (startIcon != null)
            Icon(
                painter = painterResource(startIcon),
                contentDescription = null,
                tint = if (disabled == true && trailingIcon == null) appColor.neutral600 else if (disabled == true) appColor.neutral800 else iconColor
                    ?: appColor.turquoise600Main,
                modifier = Modifier.size(iconSize ?: 15.dp)
            )
        else UiSpacer(iconSize ?: 25.dp)
        if (isLoading) {
            UiCirclesLoader(
                color1 = textColor ?: appColor.oxfordBlue800
            )
        } else {
            content?.invoke() ?: Text(
                text = text,
                color = if (disabled == true) appColor.neutral800 else textColor
                    ?: appColor.oxfordBlue800,
                style = textStyle ?: Theme.montserrat.subtitle1
            )
        }

        if (trailingIcon != null)
            Icon(
                painter = painterResource(trailingIcon),
                contentDescription = null,
                tint = if (disabled == true) appColor.neutral800 else iconColor
                    ?: appColor.turquoise600Main,
                modifier = Modifier.size(iconSize ?: 25.dp)
            )
        else UiSpacer(iconSize ?: 25.dp)
    }
}