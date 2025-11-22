import androidx.annotation.StringRes
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import coil.compose.rememberAsyncImagePainter
import com.vultisig.wallet.R
import com.vultisig.wallet.data.api.models.ResourceUsage
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.bottomsheet.VsModalBottomSheet
import com.vultisig.wallet.ui.components.buttons.AutoSizingText
import com.vultisig.wallet.ui.components.v2.containers.ContainerType
import com.vultisig.wallet.ui.components.v2.containers.V2Container
import com.vultisig.wallet.ui.screens.send.FadingHorizontalDivider
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.theme.Theme.v2
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.theme.v2.V2.colors

data class ResourceState(
    val available: Long, val total: Long,
    val title: String,
    val accentColor: Color,
    val showInfo: Boolean = false,
    val icon: Int,
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
                padEnd = 4.dp,
                containerBg = Color(0xFF1B2430)
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
                    icon = R.drawable.energy,
                    showInfo = true
                ),
                Modifier.weight(1f),
                containerBg = Color(0xFF1B2430)
            )
        }
    }
}

@Composable
fun ResourceCard(
    state: ResourceState, modifier: Modifier = Modifier, padEnd: Dp = 0.dp, containerBg: Color,

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
                    style = Theme.brockmann.body.s.medium,
                )
                if (state.showInfo) {
                    IconButton(
                        onClick = { /* info action */ },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.circleinfo),
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
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(containerBg),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(state.icon),
                        contentDescription = "${state.title} icon",
                        modifier = Modifier.size(16.dp),
                        contentScale = ContentScale.Fit
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column(modifier = Modifier.fillMaxWidth()) {
                    AutoSizingText(
                        text = "${state.available.toInt()}/${state.total.toInt()}",
                        style = Theme.brockmann.supplementary.caption,
                        color = colors.text.light,
                    )
                    Spacer(modifier = Modifier.height(7.dp))
                    AnimatedProgressBar(
                        value = if (state.total > 0) state.available.toFloat() / state.total.toFloat() else 0f,
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

@Composable
internal fun BandwidthEnergyBottomSheet(
    onDismissRequest: () -> Unit,
) {
    VsModalBottomSheet(onDismissRequest = onDismissRequest) {
    }
}

@Composable
fun BandwidthEnergyContent(
) {

    Column(
        modifier = Modifier
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row {
            Image(
                painter = painterResource(R.drawable.tron),
                contentDescription = "Tron Logo",
                modifier = Modifier.size(24.dp),
                contentScale = ContentScale.FillBounds
            )
            Text(
                text = "Tron",
                style = Theme.brockmann.supplementary.footnote,
                color = colors.neutrals.n50,
            )
        }
        UiSpacer(27.dp)
        Text(
            text = "Bandwidth & Energy ",
            style = Theme.brockmann.headings.title2,
            color = colors.variables.TextPrimary,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )


    }

}

@Composable
fun BandwidthEnergyItem(
    @StringRes title: Int,
    @StringRes description: Int,
    accentColor: Color,
    containerBg: Color,
    icon: Int,
) {
    val colors = colors
    var isExpanded by remember {
        mutableStateOf(false)
    }
    val rotation = animateFloatAsState(
        targetValue = if (isExpanded) 90f else 0f
    )
    V2Container(
        type = ContainerType.SECONDARY,
    ) {
        LazyColumn(
            contentPadding = PaddingValues(
                horizontal = 20.dp,
                vertical = 16.dp
            )
        ) {
            item {
                Column(
                    modifier = Modifier.animateContentSize()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                isExpanded = !isExpanded
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(containerBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(icon),
                                contentDescription = "${title} icon",
                                modifier = Modifier.size(16.dp),
                                contentScale = ContentScale.Fit
                            )
                        }
                        UiSpacer(8.dp)
                        Text(
                            text = stringResource(title),
                            color = accentColor,
                            style = Theme.brockmann.headings.subtitle,
                            modifier = Modifier.weight(1f)
                        )
                        UiSpacer(1f)
                        Icon(
                            modifier = Modifier.rotate(rotation.value),
                            painter = painterResource(id = R.drawable.small_caret_down),
                            contentDescription = null,
                            tint = colors.text.button.light,
                        )
                    }
                    if (isExpanded) {
                        FadingHorizontalDivider(Modifier.padding(vertical = 20.dp))
                        Text(
                            text = stringResource(description),
                            color = colors.variables.TextPrimary,
                            style = Theme.brockmann.body.s.regular,
                            lineHeight = 20.sp,
                        )
                    }

                }
            }
        }
    }
}

@Composable
@Preview
fun BandwidthEnergyItemPreview() {
    BandwidthEnergyItem(
        title = R.string.bandwidth,
        description = R.string.bandwidth,
        accentColor = colors.alerts.success,
        containerBg = Color(0xFF072C44),
        icon = R.drawable.bandwidth
    )
}

