package com.vultisig.wallet.ui.screens.keygen

import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.core.Alignment.*
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.MultiColorButton
import com.vultisig.wallet.ui.components.UiBarContainer
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.animatePlacementInScope
import com.vultisig.wallet.ui.components.rive.RiveAnimation
import com.vultisig.wallet.ui.models.keygen.SelectVaultTypeUiModel
import com.vultisig.wallet.ui.models.keygen.SelectVaultTypeViewModel
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.asString

@Composable
internal fun SelectVaultTypeScreen(
    navController: NavController,
    model: SelectVaultTypeViewModel = hiltViewModel(),
) {
    val state by model.state.collectAsState()

    SelectVaultTypeScreen(
        navController = navController,
        state = state,
        onTabClick = model::selectTab,
        onStartClick = model::start,
    )
}

@Composable
private fun SelectVaultTypeScreen(
    navController: NavController,
    state: SelectVaultTypeUiModel,
    onTabClick: (index: Int) -> Unit,
    onStartClick: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val helpLink = stringResource(R.string.link_docs_create_vault)


    UiBarContainer(
        navController = navController,
        title = stringResource(R.string.setup_title),
        endIcon = R.drawable.question,
        onEndIconClick = {
            uriHandler.openUri(helpLink)
        },
    ) {
        Column(
            horizontalAlignment = CenterHorizontally,
        ) {
            val isSecureTypeSelected = state.selectedTypeIndex == 0
            val trigger = state.triggerAnimation

            RiveAnimation(
                animation = R.raw.choose_vault,
                modifier = Modifier
                    .padding(24.dp)
                    .weight(1f),
                stateMachineName = "State Machine 1",
                autoPlay = false,
                alignment = TOP_CENTER,
                onInit = { rive: RiveAnimationView ->
                    if (trigger)
                        rive.setBooleanState("State Machine 1", "Switch", isSecureTypeSelected)
                }
            )
            val fadeAnimation by animateFloatAsState(
                targetValue = if (!trigger) 0.0f else 1f,
                animationSpec = tween(durationMillis = 800, easing = FastOutLinearInEasing),
                label = "fade animation"
            )
            val scaleAnimation by animateFloatAsState(
                targetValue = if (!trigger) 0.5f else 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow,
                ),
                label = "scale Animation"
            )

            Column(
                Modifier
                    .padding(horizontal = 16.dp)
                    .graphicsLayer {
                        this.transformOrigin = TransformOrigin(0.5f, 1f)
                        this.alpha = fadeAnimation
                        this.scaleX = scaleAnimation
                        this.scaleY = scaleAnimation
                    }


            ) {
                LookaheadScope {
                    Box(
                        Modifier
                            .height(intrinsicSize = IntrinsicSize.Min)
                            .clip(CircleShape)
                            .background(Theme.colors.oxfordBlue200)
                            .padding(8.dp)
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(),
                            contentAlignment = if (isSecureTypeSelected) Alignment.TopStart else Alignment.TopEnd
                        ) {
                            Box(
                                Modifier
                                    .animatePlacementInScope(this@LookaheadScope)
                                    .clip(CircleShape)
                                    .background(Theme.colors.oxfordBlue600Main)
                                    .fillMaxHeight()
                                    .fillMaxWidth(0.5f)
                            )
                        }
                        Row(
                            Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            TextAndIcon(
                                text = stringResource(R.string.select_vault_type_secure),
                                icon = painterResource(R.drawable.ic_shield),
                                tint = if (isSecureTypeSelected) Theme.colors.turquoise600Main else Theme.colors.neutral0,
                                contentDescription = stringResource(R.string.select_vault_type_secure),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        onTabClick(0)
                                    }
                                    .padding(16.dp)
                                    .wrapContentWidth(CenterHorizontally)
                            )

                            TextAndIcon(
                                text = stringResource(R.string.select_vault_type_fast),
                                icon = painterResource(R.drawable.thunder),
                                tint = if (isSecureTypeSelected) Theme.colors.neutral0 else Theme.colors.error,
                                contentDescription = stringResource(R.string.select_vault_type_fast),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        onTabClick(1)
                                    }
                                    .padding(16.dp)
                                    .wrapContentWidth(CenterHorizontally)
                            )
                        }
                    }
                }

                UiSpacer(16.dp)
                val borderColor = Theme.colors.oxfordBlue200
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(15))
                        .border(
                            width = 1.dp,
                            color = borderColor,
                            shape = RoundedCornerShape(15)
                        )
                        .background(
                            color = Theme.colors.oxfordBlue600Main
                        ),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = state.vaultType.title.asString(),
                        color = if (isSecureTypeSelected)
                            Theme.colors.turquoise600Main
                        else Theme.colors.error,
                        style = Theme.montserrat.subtitle1,
                        modifier = Modifier
                            .background(Theme.colors.oxfordBlue800)
                            .fillMaxWidth()
                            .drawBehind {
                                drawLine(
                                    color = borderColor,
                                    start = Offset(0f, size.height),
                                    end = Offset(size.width, size.height),
                                    strokeWidth = 5f,
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
                                )
                            }
                            .padding(24.dp),
                        textAlign = TextAlign.Center
                    )

                    TextAndIcon(
                        text = state.vaultType.desc1.asString(),
                        icon = painterResource(R.drawable.check),
                        tint = Theme.colors.turquoise600Main,
                        modifier = Modifier
                            .padding(horizontal = 20.dp)
                    )
                    TextAndIcon(
                        text = state.vaultType.desc2.asString(),
                        icon = painterResource(R.drawable.check),
                        tint = Theme.colors.turquoise600Main,
                        modifier = Modifier
                            .padding(horizontal = 20.dp)
                    )
                    TextAndIcon(
                        text = state.vaultType.desc3.asString(),
                        icon = painterResource(if (isSecureTypeSelected) R.drawable.check else R.drawable.x),
                        tint = if (isSecureTypeSelected) Theme.colors.turquoise600Main else Theme.colors.error,
                        modifier = Modifier
                            .padding(horizontal = 20.dp)
                    )

                    UiSpacer(8.dp)
                }
            }

            UiSpacer(16.dp)
            MultiColorButton(
                text = stringResource(id = R.string.select_vault_type_start),
                backgroundColor = Theme.colors.turquoise600Main,
                textColor = Theme.colors.oxfordBlue600Main,
                minHeight = 44.dp,
                textStyle = Theme.montserrat.subtitle1,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 16.dp,
                    ),
                onClick = onStartClick,
            )

            UiSpacer(32.dp)
        }
    }
}

@Composable
private fun TextAndIcon(
    modifier: Modifier = Modifier,
    text: String,
    icon: Painter,
    tint: Color,
    contentDescription: String? = null
) {
    Row(
        modifier = modifier
    ) {
        Icon(
            painter = icon,
            contentDescription = contentDescription,
            tint = tint
        )
        UiSpacer(8.dp)
        Text(
            text = text,
            color = Theme.colors.neutral0,
            style = Theme.montserrat.subtitle1,
        )
    }
}

@Preview
@Composable
private fun SelectVaultTypeScreenPreview() {
    SelectVaultTypeScreen(
        navController = rememberNavController(),
        state = SelectVaultTypeUiModel(selectedTypeIndex = 0),
        onTabClick = {},
        onStartClick = {},
    )
}

