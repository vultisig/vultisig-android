package com.vultisig.wallet.ui.screens.v3.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.v3.HorizontalAnimatedPager
import com.vultisig.wallet.ui.components.v3.V3Icon
import com.vultisig.wallet.ui.components.v3.V3Scaffold
import com.vultisig.wallet.ui.models.v3.onboarding.ChooseDeviceCountUiEvent
import com.vultisig.wallet.ui.models.v3.onboarding.ChooseDeviceCountUiState
import com.vultisig.wallet.ui.models.v3.onboarding.ChooseDeviceCountViewModel
import com.vultisig.wallet.ui.models.v3.onboarding.DeviceCountTip
import com.vultisig.wallet.ui.screens.v3.onboarding.components.DeviceCountSelector
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.asString

@Composable
internal fun ChooseDeviceCountScreen(
    viewModel: ChooseDeviceCountViewModel = hiltViewModel()
) {

    val uiState by viewModel.uiState.collectAsState()

    ChooseDeviceCountScreen(
        uiState = uiState,
        onEvent = viewModel::handleEvent
    )
}

@Composable
private fun ChooseDeviceCountScreen(
    uiState: ChooseDeviceCountUiState,
    onEvent: (ChooseDeviceCountUiEvent) -> Unit,
) {
    V3Scaffold(
        applyGradientBackground = true,
    ) {
        Column {
            Header()

            DeviceCountSelector(
                count = uiState.deviceCount,
                onIncrease = {
                    onEvent(ChooseDeviceCountUiEvent.IncreaseCount)
                },
                onDecrease = {
                    onEvent(ChooseDeviceCountUiEvent.DecreaseCount)
                },
            )

            UiSpacer(
                size = 32.dp
            )

            DeviceCountDescription(
                selectedIndex = uiState.deviceCount - 1, tips = uiState.tips
            )

            UiSpacer(
                weight = 1f
            )

            Tip()

            UiSpacer(
                size = 16.dp
            )

            VsButton(
                label = "Get Started", modifier = Modifier.fillMaxWidth(), onClick = {
                    onEvent(ChooseDeviceCountUiEvent.Next)
                }
            )
        }
    }
}

@Composable
private fun Header() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(250.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(R.drawable.logo),
            contentDescription = null,
            modifier = Modifier.width(60.dp),
            contentScale = ContentScale.FillWidth
        )
        UiSpacer(
            size = 24.dp
        )
        Text(
            text = "How many devices\n do you have?",
            style = Theme.brockmann.headings.title2,
            color = Theme.v2.colors.neutrals.n50,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun Tip() {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        UiIcon(
            drawableResId = R.drawable.tip, size = 16.dp,
            tint = Theme.v2.colors.alerts.info
        )
        UiSpacer(
            size = 8.dp
        )
        Text(
            text = stringResource(R.string.welcome_tip) ,
            style = Theme.brockmann.supplementary.caption,
            color = Theme.v2.colors.text.secondary,
        )
    }
}

@Composable
private fun DeviceCountDescription(
    selectedIndex: Int,
    tips: List<DeviceCountTip>
) {
    val shape = RoundedCornerShape(
        topStart = 24.dp,
        topEnd = 24.dp,
        bottomStart = 20.dp,
        bottomEnd = 20.dp
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(
                shape = shape
            )
            .background(
                color = Theme.v2.colors.backgrounds.background
            )
            .border(
                width = 1.dp, shape = shape, color = Theme.v2.colors.border.light
            )
            .padding(
                horizontal = 20.dp,
                vertical = 24.dp,
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        HorizontalAnimatedPager(
            index = selectedIndex
        ) {
            tips.forEach { (logo, _, _) ->
                item {
                    V3Icon(logo)
                }
            }
        }
        UiSpacer(
            size = 12.dp
        )


        Column(
            modifier = Modifier.animateContentSize()
        ) {
            UiSpacer(
                size = 8.dp
            )
            AnimatedContent(targetState = tips[selectedIndex].title.asString()) {
                Text(
                    text = it,
                    color = Theme.v2.colors.neutrals.n50,
                    style = Theme.brockmann.headings.subtitle,
                )
            }

            UiSpacer(
                size = 8.dp
            )

            AnimatedContent(tips[selectedIndex].subTitle.asString()) { subTitleText ->
                val highlight = tips[selectedIndex].subTitleHighlight?.asString()
                val annotatedText = buildAnnotatedString {
                    val start = if (highlight != null) subTitleText.indexOf(highlight) else -1
                    if (start >= 0) {
                        append(subTitleText.substring(0, start))
                        withStyle(SpanStyle(color = Theme.v2.colors.neutrals.n50, fontWeight = FontWeight.Bold)) {
                            append(highlight)
                        }
                        append(subTitleText.substring(start + highlight!!.length))
                    } else {
                        append(subTitleText)
                    }
                }
                Text(
                    text = annotatedText,
                    color = Theme.v2.colors.text.tertiary,
                    style = Theme.brockmann.body.s.medium,
                )
            }

        }
    }
}


@Preview
@Composable
private fun ChooseDeviceCountScreenPreview() {
    ChooseDeviceCountScreen(
        uiState = ChooseDeviceCountUiState(),
        onEvent = {}
    )
}