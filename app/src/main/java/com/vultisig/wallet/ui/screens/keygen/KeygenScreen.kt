package com.vultisig.wallet.ui.screens.keygen

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.TssAction
import com.vultisig.wallet.ui.components.KeepScreenOn
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.errors.ErrorView
import com.vultisig.wallet.ui.components.loader.VsHorizontalProgressIndicator
import com.vultisig.wallet.ui.components.loader.VsSigningProgressIndicator
import com.vultisig.wallet.ui.components.rive.RiveAnimation
import com.vultisig.wallet.ui.components.topbar.VsTopAppBar
import com.vultisig.wallet.ui.models.keygen.KeygenStepUiModel
import com.vultisig.wallet.ui.models.keygen.KeygenUiModel
import com.vultisig.wallet.ui.models.keygen.KeygenViewModel
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.asString

@Composable
internal fun KeygenScreen(
    model: KeygenViewModel = hiltViewModel(),
) {
    KeepScreenOn()

    val state by model.state.collectAsState()

    if (state.isSuccess) {
        Success()
    } else {
        when (state.action) {
            TssAction.KEYGEN, TssAction.ReShare , TssAction.KeyImport -> {
                KeygenScreen(
                    state = state,
                    onTryAgainClick = model::tryAgain,
                )
            }

            TssAction.Migrate -> {
                VsSigningProgressIndicator(
                    text = stringResource(R.string.keygen_screen_upgrading_vault),
                )
            }
        }
    }
}

@Composable
private fun KeygenScreen(
    state: KeygenUiModel,
    onTryAgainClick: () -> Unit,
) {
    Scaffold(
        containerColor = Theme.v2.colors.backgrounds.primary,
        topBar = {
            VsTopAppBar(
                title = stringResource(R.string.keygen_top_bar_title),
            )
        },
        content = { contentPadding ->
            Column(
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .padding(contentPadding)
                    .fillMaxSize(),
            ) {
                val error = state.error
                if (error == null) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(
                                all = 40.dp,
                            ),
                    ) {
                        Text(
                            text = stringResource(R.string.keygen_while_you_wait_title),
                            style = Theme.brockmann.headings.subtitle,
                            color = Theme.v2.colors.text.extraLight,
                            textAlign = TextAlign.Center,
                        )

                        UiSpacer(12.dp)

                        val benefits = remember { benefits() }

                        val transition = rememberInfiniteTransition(label = "")

                        val currentIndex by transition.animateFloat(
                            initialValue = 0f,
                            targetValue = (benefits.size).toFloat(),
                            animationSpec = infiniteRepeatable(
                                animation = tween(benefits.size * 3333, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart,
                            ),
                            label = ""
                        )

                        val currentBenefit =
                            benefits[currentIndex.toInt().coerceIn(0..benefits.lastIndex)]

                        val annotatedString = buildAnnotatedString {
                            withStyle(
                                style = SpanStyle(
                                    brush = Theme.v2.colors.gradients.primary,
                                ),
                            ) {
                                append(stringResource(currentBenefit.emphasized))
                            }
                            appendLine()
                            append(stringResource(currentBenefit.template))
                        }

                        AnimatedContent(
                            targetState = annotatedString, label = "",
                            transitionSpec = {
                                (fadeIn(animationSpec = tween(550, delayMillis = 250)))
                                    .togetherWith(fadeOut(animationSpec = tween(250)))
                            }
                        ) { text ->
                            Text(
                                text = text,
                                style = Theme.brockmann.headings.title2,
                                color = Theme.v2.colors.text.primary,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }

                    val shape = RoundedCornerShape(24.dp)

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(all = 36.dp),
                    ) {
                        LazyColumn(
                            userScrollEnabled = false,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentHeight()
                                .background(
                                    color = Theme.v2.colors.backgrounds.secondary,
                                    shape = shape,
                                )
                                .border(
                                    width = 1.dp,
                                    color = Theme.v2.colors.border.light,
                                    shape = shape,
                                )
                                .padding(
                                    vertical = 28.dp,
                                    horizontal = 36.dp,
                                )
                                .defaultMinSize(minHeight = 64.dp)
                        ) {
                            items(state.steps.takeLast(2)) { step ->
                                LoadingStageItem(
                                    text = step.title.asString(),
                                    isLoading = step.isLoading,
                                    modifier = Modifier.animateItem()
                                )
                            }
                        }

                        VsHorizontalProgressIndicator(
                            progress = state.progress,
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .padding(
                                    horizontal = 24.dp
                                ),
                        )
                    }
                } else {
                    ErrorView(
                        title = error.title.asString(),
                        description = error.description.asString(),
                        onTryAgainClick = onTryAgainClick,
                        modifier = Modifier
                            .fillMaxWidth()
                    )
                }
            }
        }
    )
}

private data class BenefitsDescription(
    @StringRes val template: Int,
    @StringRes val emphasized: Int,
)

private fun benefits() = listOf(
    BenefitsDescription(R.string.keygen_benefit_1_template, R.string.keygen_benefit_1_emphasized),
    BenefitsDescription(R.string.keygen_benefit_2_template, R.string.keygen_benefit_2_emphasized),
    BenefitsDescription(R.string.keygen_benefit_3_template, R.string.keygen_benefit_3_emphasized),
    BenefitsDescription(R.string.keygen_benefit_4_template, R.string.keygen_benefit_4_emphasized),
    BenefitsDescription(R.string.keygen_benefit_5_template, R.string.keygen_benefit_5_emphasized),
)

@Composable
private fun LoadingStageItem(
    text: String,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .padding(
                vertical = 2.dp,
            )
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                strokeWidth = 2.dp,
                color = Theme.v2.colors.alerts.success,
                modifier = Modifier
                    .size(20.dp)
            )
        } else {
            UiIcon(
                drawableResId = R.drawable.check,
                size = 20.dp,
                tint = Theme.v2.colors.alerts.success,
            )
        }

        UiSpacer(8.dp)

        Text(
            text = text,
            style = Theme.brockmann.body.m.medium,
            color = if (isLoading) Theme.v2.colors.text.primary
            else Theme.v2.colors.text.extraLight,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun Success() {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(Theme.v2.colors.backgrounds.primary),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            RiveAnimation(
                animation = R.raw.riv_vault_created,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )

            var isSuccessVisible by remember { mutableStateOf(false) }

            LaunchedEffect(Unit) {
                isSuccessVisible = true
            }


            AnimatedVisibility(
                visible = isSuccessVisible,
                enter = fadeIn(tween(SUCCESS_ENTER_DURATION_MS)) +
                        slideInVertically(
                            tween(SUCCESS_ENTER_DURATION_MS),
                        ) +
                        scaleIn(tween(SUCCESS_ENTER_DURATION_MS)),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val successText = buildAnnotatedString {
                        append(stringResource(R.string.keygen_vault_created_success_part_1))
                        appendLine(" ")
                        withStyle(
                            SpanStyle(brush = Theme.v2.colors.gradients.primary)
                        ) {
                            append(stringResource(R.string.vault_created_success_part_2))
                        }
                    }

                    Text(
                        text = successText,
                        style = Theme.brockmann.headings.title1,
                        color = Theme.v2.colors.text.primary,
                        textAlign = TextAlign.Center,
                    )

                    UiSpacer(12.dp)

                    RiveAnimation(
                        animation = R.raw.riv_connecting_with_server,
                        modifier = Modifier
                            .size(24.dp),
                    )
                }
            }

            UiSpacer(60.dp)

        }
    }
}

private const val SUCCESS_ENTER_DURATION_MS = 375

@Preview
@Composable
private fun KeygenScreenPreview() {
    KeygenScreen(
        state = KeygenUiModel(
            steps = listOf(
                KeygenStepUiModel(
                    UiText.StringResource(R.string.keygen_step_preparing_vault),
                    isLoading = true,
                ),
                KeygenStepUiModel(
                    UiText.StringResource(R.string.keygen_step_generating_ecdsa),
                    isLoading = false,
                ),
            )
        ),
        onTryAgainClick = {},
    )
}