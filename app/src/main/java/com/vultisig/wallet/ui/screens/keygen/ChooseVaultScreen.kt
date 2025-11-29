package com.vultisig.wallet.ui.screens.keygen

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.core.Alignment.TOP_CENTER
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.animatePlacementInScope
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.rive.RiveAnimation
import com.vultisig.wallet.ui.components.topbar.VsTopAppBar
import com.vultisig.wallet.ui.models.keygen.ChooseVaultViewModel
import com.vultisig.wallet.ui.models.keygen.SelectVaultTypeUiModel
import com.vultisig.wallet.ui.models.keygen.VaultType
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.asString
import com.vultisig.wallet.ui.utils.VsUriHandler
import kotlinx.coroutines.launch

@Composable
internal fun ChooseVaultScreen(
    model: ChooseVaultViewModel = hiltViewModel(),
) {
    val state by model.state.collectAsState()
    val uriHandler = VsUriHandler()
    val helpLink = stringResource(R.string.link_docs_create_vault)
    ChooseVaultScreen(
        state = state,
        onTabClick = model::selectTab,
        onStartClick = model::start,
        onBackClick = model::navigateToBack,
        onHelpClick = {
            uriHandler.openUri(helpLink)
        }
    )
}

@Composable
private fun ChooseVaultScreen(
    state: SelectVaultTypeUiModel,
    onTabClick: (type: VaultType) -> Unit,
    onStartClick: () -> Unit,
    onBackClick: () -> Unit,
    onHelpClick: () -> Unit,
) {
    Scaffold(
        containerColor = Theme.v2.colors.backgrounds.primary
    ) {
        Column(
            modifier = Modifier.padding(it),
            horizontalAlignment = CenterHorizontally,
        ) {
            val isSecureTypeSelected = state.vaultType is VaultType.Secure

            val fadeAnimation = remember {
                Animatable(0f)
            }
            val scaleAnimation = remember {
                Animatable(0.5f)
            }

            LaunchedEffect(Unit) {
                launch {
                    startFadeAnimation(fadeAnimation)
                }
                startScaleAnimation(scaleAnimation)
            }


            VsTopAppBar(
                title = stringResource(R.string.select_vault_type_choose_setup),
                iconRight = R.drawable.question,
                onBackClick = onBackClick,
                onIconRightClick = onHelpClick,
            )
            RiveAnimation(
                animation = R.raw.riv_choose_vault,
                modifier = Modifier
                    .padding(24.dp)
                    .weight(1f),
                alignment = TOP_CENTER,
                onInit = { rive: RiveAnimationView ->
                    state.animate(rive)
                }
            )

            Column(
                Modifier
                    .padding(horizontal = 16.dp)
                    .graphicsLayer {
                        this.transformOrigin = TransformOrigin.Center.copy(pivotFractionY = 1f)
                        this.alpha = fadeAnimation.value
                        this.scaleX = scaleAnimation.value
                        this.scaleY = scaleAnimation.value
                    }

            ) {
                LookaheadScope {
                    Box(
                        modifier = Modifier
                            .height(intrinsicSize = IntrinsicSize.Min)
                            .clip(CircleShape)
                            .background(Theme.v2.colors.backgrounds.tertiary_2)
                            .padding(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(),
                            contentAlignment = if (isSecureTypeSelected)
                                Alignment.TopEnd else Alignment.TopStart
                        ) {
                            Box(
                                modifier = Modifier
                                    .animatePlacementInScope(this@LookaheadScope)
                                    .clip(CircleShape)
                                    .background(Theme.v2.colors.backgrounds.primary)
                                    .fillMaxHeight()
                                    .fillMaxWidth(0.5f)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(CircleShape)
                                    .clickable {
                                        onTabClick(VaultType.Fast)
                                    }
                                    .padding(16.dp)
                                    .wrapContentWidth(CenterHorizontally)
                                    .testTag("ChooseVaultScreen.selectFastVault")
                            ) {
                                val brushGradient = Theme.v2.colors.gradients.primary
                                val iconModifier = if (!isSecureTypeSelected) {
                                    Modifier
                                        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                                        .drawWithCache {
                                            onDrawWithContent {
                                                drawContent()
                                                drawRect(
                                                    brushGradient,
                                                    blendMode = BlendMode.SrcAtop
                                                )
                                            }
                                        }
                                } else {
                                    Modifier
                                }
                                Icon(
                                    modifier = iconModifier,
                                    painter = painterResource(R.drawable.thunder),
                                    contentDescription = stringResource(R.string.select_vault_type_fast),
                                    tint = Theme.v2.colors.text.primary,
                                )
                                UiSpacer(8.dp)
                                Text(
                                    text = stringResource(R.string.select_vault_type_fast),
                                    color = Theme.v2.colors.text.primary,
                                    style = Theme.brockmann.body.s.medium,
                                )
                            }

                            TextAndIcon(
                                text = stringResource(R.string.select_vault_type_secure),
                                icon = painterResource(R.drawable.ic_shield),
                                tint = if (isSecureTypeSelected)
                                    Theme.v2.colors.alerts.success
                                else Theme.v2.colors.text.primary,
                                contentDescription = stringResource(R.string.select_vault_type_secure),
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(CircleShape)
                                    .clickable {
                                        onTabClick(VaultType.Secure)
                                    }
                                    .padding(16.dp)
                                    .wrapContentWidth(CenterHorizontally)
                            )
                        }
                    }
                }

                UiSpacer(16.dp)
                val borderColor = Theme.v2.colors.border.normal
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(15))
                        .border(
                            width = 1.dp,
                            color = borderColor,
                            shape = RoundedCornerShape(15)
                        )
                        .background(Theme.v2.colors.backgrounds.tertiary_2),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = buildAnnotatedString {
                            withStyle(style = SpanStyle(brush = Theme.v2.colors.gradients.primary)) {
                                append(state.vaultType.title.asString())
                            }
                        },
                        color = Theme.v2.colors.alerts.success,
                        style = Theme.brockmann.headings.subtitle,
                        modifier = Modifier
                            .background(Theme.v2.colors.backgrounds.tertiary_2)
                            .fillMaxWidth()
                            .background(
                                color = Theme.v2.colors.backgrounds.primary
                            )
                            .drawBehind {
                                drawLine(
                                    color = borderColor,
                                    start = Offset(0f, size.height),
                                    end = Offset(size.width, size.height),
                                    strokeWidth = 5f,
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
                                )
                            }
                            .padding(16.dp),
                        textAlign = TextAlign.Center
                    )

                    TextAndIcon(
                        text = state.vaultType.desc1.asString(),
                        icon = painterResource(R.drawable.check),
                        tint = Theme.v2.colors.alerts.success,
                        modifier = Modifier
                            .padding(horizontal = 20.dp)
                    )
                    TextAndIcon(
                        text = state.vaultType.desc2.asString(),
                        icon = painterResource(R.drawable.check),
                        tint = Theme.v2.colors.alerts.success,
                        modifier = Modifier
                            .padding(horizontal = 20.dp)
                    )
                    TextAndIcon(
                        text = state.vaultType.desc3.asString(),
                        icon = painterResource(R.drawable.check),
                        tint = Theme.v2.colors.alerts.success,
                        modifier = Modifier
                            .padding(horizontal = 20.dp)
                    )

                    UiSpacer(8.dp)
                }
            }

            UiSpacer(24.dp)
            VsButton(
                onClick = onStartClick,
                label = stringResource(id = R.string.select_vault_type_next),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .testTag("ChooseVaultScreen.continue")
            )
            UiSpacer(32.dp)
        }
    }
}

private suspend fun startScaleAnimation(
    scaleAnimation: Animatable<Float, AnimationVector1D>
) {
    scaleAnimation.animateTo(
        targetValue = 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        )
    )
}


private suspend fun startFadeAnimation(
    fadeAnimation: Animatable<Float, AnimationVector1D>
) {
    fadeAnimation.animateTo(
        targetValue = 1f,
        animationSpec = tween(
            durationMillis = 500,
            easing = FastOutLinearInEasing
        )
    )
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
            color = Theme.v2.colors.text.primary,
            style = Theme.brockmann.body.s.medium,
        )
    }
}

@Preview
@Composable
private fun SelectVaultTypeScreenPreview() {
    ChooseVaultScreen(
        state = SelectVaultTypeUiModel(vaultType = VaultType.Secure),
        onTabClick = {},
        onStartClick = {},
        onBackClick = {},
        onHelpClick = {},
    )
}

