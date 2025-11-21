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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp

data class ResourceState(
    val title: String,
    val used: String,
    val usedValue: Float, // 0f..1f
    val iconRes: Int,
    val accent: Color,
    val iconBg: Color,
    val showInfo: Boolean = false
)

@Composable
fun ResourceTwoCardsRow(
    left: ResourceState,
    right: ResourceState,
    modifier: Modifier = Modifier,
    containerBg: Color = Color(0xFF071220),
    borderColor: Color = Color(0xFF102235)
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(96.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(
                1.dp,
                borderColor,
                RoundedCornerShape(12.dp)
            ),
        color = containerBg
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ResourceCard(
                left,
                Modifier.weight(1f),
                padEnd = 4.dp
            )
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(borderColor)
            )
            ResourceCard(
                right,
                Modifier.weight(1f),
                padStart = 4.dp
            )
        }
    }
}

@Composable
fun ResourceCard(
    state: ResourceState, modifier: Modifier = Modifier, padStart: Dp = 0.dp, padEnd: Dp = 0.dp
) {
    Row(
        modifier = modifier
            .padding(
                start = padStart,
                end = padEnd,
                top = 12.dp,
                bottom = 12.dp
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
                    color = state.accent,
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
                        .background(state.iconBg),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(state.iconRes),
                        contentDescription = "${state.title} icon",
                        modifier = Modifier.size(18.dp),
                        contentScale = ContentScale.Fit
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = state.used,
                        color = Color(0xFFB8DDE6),
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    AnimatedProgressBar(
                        value = state.usedValue,
                        accent = state.accent
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
        left = ResourceState(
            title = "Bandwidth",
            used = "1.46 / 1.46kb",
            usedValue = 0.98f,
            iconRes = android.R.drawable.presence_online, // replace with R.drawable.bandwidth
            accent = Color(0xFF00E5B8),
            iconBg = Color(0xFF00373A)
        ),
        right = ResourceState(
            title = "Energy",
            used = "1 / 2",
            usedValue = 0.5f,
            iconRes = android.R.drawable.star_on, // replace with R.drawable.energy
            accent = Color(0xFFFFC257),
            iconBg = Color(0xFF2A1F10),
            showInfo = true
        ),
        modifier = Modifier.padding(16.dp)
    )
}