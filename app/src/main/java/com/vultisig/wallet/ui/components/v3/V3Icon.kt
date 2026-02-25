package com.vultisig.wallet.ui.components.v3

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.v2.modifiers.shinedBottom
import com.vultisig.wallet.ui.theme.Theme


@Composable
fun V3Icon(
    @DrawableRes logo: Int,
){
    Box(
        modifier = Modifier
            .size(33.dp)
            .clip(
                shape = CircleShape
            )
            .background(
                color = Theme.v2.colors.backgrounds.background
            )
            .shinedBottom()
            .border(
                width = 2.dp,
                color = Theme.v2.colors.neutrals.n50.copy(alpha = 0.1f),
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(logo),
            contentDescription = "icon",
            modifier = Modifier
                .size(size = 18.dp)
        )
    }
}

@Composable
fun V3Icon(
    backgroundColor: Color = Color.Transparent,
    @DrawableRes logo: Int,
    borderWidth: Dp = 1.dp,
    borderColor: Color = Theme.v2.colors.border.light,
    shinedBottom: Color? = Theme.v2.colors.neutrals.n50,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(
                shape = CircleShape
            )
            .background(
                color = backgroundColor
            )
            .then(
                if (shinedBottom == null) Modifier
                else Modifier.shinedBottom(
                    color = shinedBottom
                )
            )
            .border(
                width = borderWidth,
                color = borderColor,
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(logo),
            modifier = Modifier
                .size(18.dp),
            contentDescription = null,
        )
    }
}




@Preview
@Composable
private fun V3IconPreview(){
        V3Icon(
            logo = R.drawable.icon_shield_solid
        )
}