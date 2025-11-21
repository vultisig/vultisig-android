import androidx.annotation.StringRes
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import com.vultisig.wallet.R
import com.vultisig.wallet.data.api.models.ResourceUsage
import com.vultisig.wallet.ui.components.buttons.AutoSizingText
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.theme.Theme.v2
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.theme.v2.V2.colors

data class ResourceState(
    val available: Long, val total: Long,
    val title :  String,
    val accentColor: Color,
    val  showInfo: Boolean = false,
    val icon : Int,

    // 0f..1f
)

@Composable
fun ResourceTwoCardsRow(
    resourceUsage: ResourceUsage,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(96.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(
                1.dp,
                colors.variables.BordersLight,
                RoundedCornerShape(12.dp)
            ),
        color = colors.variables.BackgroundsSurface1
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ResourceCard(
                ResourceState(
                    available = resourceUsage.availableBandwidth,
                    total = resourceUsage.totalBandwidth,
                    title = stringResource(R.string.bandwidth),
                    accentColor = colors.alerts.success,
                    icon = R.drawable.bandwidth
                ),
                Modifier.weight(1f),
                padEnd = 4.dp
            )
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(colors.variables.BordersLight)
            )
            ResourceCard(
                ResourceState(
                    available = resourceUsage.availableEnergy,
                    total = resourceUsage.totalEnergy,
                    title = stringResource(R.string.energy),
                    accentColor = colors.alerts.warning,
                    icon = R.drawable.energy
                ),
                Modifier.weight(1f),
            )
        }
    }
}

@Composable
fun ResourceCard(
    state: ResourceState, modifier: Modifier = Modifier, padEnd: Dp = 0.dp
) {
    Row(
        modifier = modifier.padding(
                16.dp
            ),
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.fillMaxHeight()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = state.title,
                    color = state.accentColor,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (state.showInfo) {
                    IconButton(
                        onClick = { /* info action */ },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = "info",
                            tint = Color(0xFF9FB1C9)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
//                        .background(state.iconBg)
                    ,
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(state.icon),
                        contentDescription = "${state.title} icon",
                        modifier = Modifier.size(18.dp),
                        contentScale = ContentScale.Fit
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.fillMaxWidth()) {
                    AutoSizingText(
                        text = "${state.available.toInt()}/${state.total.toInt()}",
                        style = Theme.brockmann.supplementary.caption,
                        color = colors.text.light,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    AnimatedProgressBar(
                        value = if (state.total > 0) state.available.toFloat()/state.total.toFloat() else 0f,
                        accent = state.accentColor
                    )
                }
            }
        }
    }
}

@Composable
fun AnimatedProgressBar(value: Float, accent: Color, height: Dp = 8.dp) {
    val animated by animateFloatAsState(
        targetValue = value.coerceIn(
            0f,
            1f
        ),
        animationSpec = tween(
            durationMillis = 700,
            easing = LinearOutSlowInEasing
        )
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF123041))
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(animated)
                .clip(RoundedCornerShape(8.dp))
                .background(accent)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewResourceTwoCards() {
    ResourceTwoCardsRow(
        resourceUsage = ResourceUsage(
            availableBandwidth = 1500,
            totalBandwidth = 3000,
            availableEnergy = 800,
            totalEnergy = 2000
        ),
        modifier = Modifier.padding(16.dp)
    )

}