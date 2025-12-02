package com.vultisig.wallet.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun GradientInfoCard(text: String, gradient: Brush = Brush.vultiGradient()) {
    Card(
        modifier = Modifier
            .padding(bottom = 16.dp)
            .border(
                width = 1.dp,
                brush = gradient,
                shape = RoundedCornerShape(12.dp)
            ),
        colors = CardDefaults.cardColors(
            containerColor = Theme.v2.colors.backgrounds.transparent,
        )
    ) {
        Row(
            horizontalArrangement = Arrangement.Absolute.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UiIcon(
                modifier = Modifier
                    .padding(
                        start = 16.dp,
                        top = 16.dp,
                        bottom = 16.dp
                    )
                    .graphicsLayer(alpha = 0.99f)
                    .drawWithCache {
                        onDrawWithContent {
                            drawContent()
                            drawRect(gradient, blendMode = BlendMode.SrcAtop)
                        }
                    },
                drawableResId = R.drawable.ic_info,
                size = 20.dp,
            )
            Text(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                text = text,
                style = Theme.menlo.body2.copy(
                    fontSize = 12.sp,
                ).copy(textAlign = TextAlign.Center),
                color = Theme.v2.colors.neutrals.n100,
            )
        }
    }
}