package com.vultisig.wallet.ui.utils

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.vultiGradient
import com.vultisig.wallet.ui.theme.Theme

@Composable
fun DeviceInfoItem(
    info: String,
    backgroundColor: Color = Theme.colors.oxfordBlue600Main,
) {
    val textColor = Theme.colors.neutral0

    Text(
        text = info,
        color = textColor,
        style = Theme.menlo.overline2,
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = backgroundColor,
                shape = RoundedCornerShape(10.dp),
            )
            .padding(
                vertical = 24.dp,
                horizontal = 20.dp,
            )
    )
}


@Preview
@Composable
fun LabelViewPreview() {
    DeviceInfoItem(
        "iPad Pro 6th generation (This Device)",
        Theme.colors.trasnparentTurquoise,
    )
}

@Composable
fun Hint(
    text: String,
) {
    val brushGradient = Brush.vultiGradient()

    Row(
        modifier = Modifier
            .border(
                width = 1.dp,
                brush = brushGradient,
                shape = RoundedCornerShape(10.dp)
            )
            .fillMaxWidth()
            .padding(all = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(id = android.R.drawable.ic_menu_info_details),
            contentDescription = null,
            modifier = Modifier
                .graphicsLayer(alpha = 0.99f)
                .drawWithCache {
                    onDrawWithContent {
                        drawContent()
                        drawRect(
                            brushGradient,
                            blendMode = BlendMode.SrcAtop
                        )
                    }
                }
                .size(20.dp)
        )

        UiSpacer(size = 10.dp)

        Text(
            style = Theme.menlo.body1,
            text = text,
            color = Theme.colors.neutral0
        )
    }
}
