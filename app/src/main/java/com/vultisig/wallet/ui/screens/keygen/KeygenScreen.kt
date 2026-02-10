package com.vultisig.wallet.ui.screens.keygen

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.rive.Fit
import app.rive.ViewModelSource
import app.rive.rememberViewModelInstance
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.TssAction
import com.vultisig.wallet.ui.components.KeepScreenOn
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.errors.ErrorView
import com.vultisig.wallet.ui.components.loader.VsSigningProgressIndicator
import com.vultisig.wallet.ui.components.rive.RiveAnimation
import com.vultisig.wallet.ui.components.rive.rememberRiveResourceFile
import com.vultisig.wallet.ui.components.v2.scaffold.V2Scaffold
import com.vultisig.wallet.ui.models.keygen.KeygenState
import com.vultisig.wallet.ui.models.keygen.KeygenStepUiModel
import com.vultisig.wallet.ui.models.keygen.KeygenUiModel
import com.vultisig.wallet.ui.models.keygen.KeygenViewModel
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.asString
import com.vultisig.wallet.ui.utils.performHaptic

@Composable
internal fun KeygenScreen(
    model: KeygenViewModel = hiltViewModel(),
) {
    KeepScreenOn()

    val state by model.state.collectAsState()
    val view = LocalView.current

    LaunchedEffect(state.keygenState) {
        when(state.keygenState) {
            KeygenState.KeygenECDSA,
            KeygenState.KeygenEdDSA,
            KeygenState.Success,-> {
                view.performHaptic()
            }
            else -> Unit
        }
    }

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
    V2Scaffold(
        applyDefaultPaddings = false,
        applyScaffoldPaddings = false,
        topBar = {},
        content = {
            Column(
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxSize(),
            ) {
                val error = state.error
                if (error == null) {

                    val riveFile = rememberRiveResourceFile(resId = R.raw.riv_keygen).value ?: return@Column
                    val vmi = rememberViewModelInstance(
                        file = riveFile,
                        source = ViewModelSource.Named("ViewModel").defaultInstance()
                    )

                    LaunchedEffect(Unit) {
                        vmi.setBoolean("Connected", true)
                    }

                    val animatedValue by animateFloatAsState(
                        targetValue = state.progress.times(100),
                        animationSpec = tween(durationMillis = 300),
                        label = "riv_progress_animation"
                    )

                    LaunchedEffect(animatedValue) {
                        vmi.setNumber("progessPercentage", animatedValue)
                    }

                    RiveAnimation(
                        file = riveFile,
                        viewModelInstance = vmi,
                        modifier = Modifier.fillMaxSize(),
                        fit = Fit.Cover()
                    )
                } else {
                    ErrorView(
                        title = error.title.asString(),
                        description = error.description.asString(),
                        onButtonClick = onTryAgainClick,
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
            else Theme.v2.colors.text.tertiary,
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