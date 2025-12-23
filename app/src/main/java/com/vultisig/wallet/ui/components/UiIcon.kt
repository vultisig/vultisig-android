package com.vultisig.wallet.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun UiIcon(
    @DrawableRes drawableResId: Int,
    size: Dp,
    modifier: Modifier = Modifier,
    tint: Color = Theme.colors.neutrals.n100,
    contentDescription: String? = null,
    onClick: (() -> Unit)? = null,
) = Icon(
    painter = painterResource(id = drawableResId),
    contentDescription = contentDescription,
    tint = tint,
    modifier = modifier
        .size(size)
        .then(
            if (onClick != null)
                Modifier.clickOnce(onClick = onClick)
            else Modifier
        ),
)