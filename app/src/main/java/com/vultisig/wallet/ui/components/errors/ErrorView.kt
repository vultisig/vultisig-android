package com.vultisig.wallet.ui.components.errors

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.AppVersionText
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonVariant
import com.vultisig.wallet.ui.components.v2.buttons.DesignType
import com.vultisig.wallet.ui.components.v2.buttons.VsCircleButton
import com.vultisig.wallet.ui.components.v2.buttons.VsCircleButtonSize
import com.vultisig.wallet.ui.components.v2.buttons.VsCircleButtonType
import com.vultisig.wallet.ui.theme.Theme

internal data class ErrorViewButtonUiModel(val text: String, val onClick: () -> Unit)

@Composable
internal fun ErrorView(
    modifier: Modifier = Modifier,
    title: String,
    description: String? = null,
    errorState: ErrorState = ErrorState.WARNING,
    buttonUiModel: ErrorViewButtonUiModel?,
    secondaryButtonUiModel: ErrorViewButtonUiModel? = null,
    onBack: (() -> Unit)? = null,
) {
    Box(modifier = modifier.fillMaxSize().background(Theme.v2.colors.backgrounds.primary)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            UiSpacer(weight = 1f)

            ErrorWaves(title = title, description = description, errorState = errorState)

            buttonUiModel?.let {
                UiSpacer(30.dp)
                VsButton(
                    variant = VsButtonVariant.Secondary,
                    label = it.text,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = it.onClick,
                )
            }

            secondaryButtonUiModel?.let {
                UiSpacer(if (buttonUiModel != null) 12.dp else 30.dp)
                VsButton(
                    variant = VsButtonVariant.CTA,
                    label = it.text,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = it.onClick,
                )
            }

            UiSpacer(weight = 1f)

            AppVersionText()

            UiSpacer(size = 50.dp)
        }

        if (onBack != null) {
            VsCircleButton(
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
                onClick = onBack,
                size = VsCircleButtonSize.Small,
                type = VsCircleButtonType.Secondary,
                designType = DesignType.Shined,
                icon = R.drawable.ic_caret_left,
            )
        }
    }
}

@Composable
internal fun ErrorWaves(
    modifier: Modifier = Modifier,
    title: String,
    description: String? = null,
    errorState: ErrorState = ErrorState.WARNING,
) {

    val waveCircleColor = Theme.v2.colors.border.light
    Column(
        modifier = modifier.padding(bottom = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            imageVector =
                ImageVector.vectorResource(
                    id =
                        when (errorState) {
                            ErrorState.CRITICAL -> R.drawable.error_critical
                            ErrorState.WARNING -> R.drawable.error_warning
                        }
                ),
            contentDescription = null,
            modifier =
                Modifier.size(24.dp).drawBehind {
                    drawCircle(
                        color = waveCircleColor,
                        radius = 37.dp.toPx(),
                        style = Stroke(width = 0.69f.dp.toPx()),
                    )
                    drawCircle(
                        color = waveCircleColor,
                        radius = 68.dp.toPx(),
                        style = Stroke(width = 0.55.dp.toPx()),
                    )
                    drawCircle(
                        color = waveCircleColor,
                        radius = 131.dp.toPx(),
                        style = Stroke(width = 0.69f.dp.toPx()),
                    )
                },
        )

        UiSpacer(24.dp)

        Text(
            text = title,
            style = Theme.brockmann.headings.title2,
            color =
                when (errorState) {
                    ErrorState.CRITICAL -> Theme.v2.colors.alerts.error
                    ErrorState.WARNING -> Theme.v2.colors.alerts.warning
                },
            textAlign = TextAlign.Center,
        )

        description?.let {
            UiSpacer(12.dp)
            Text(
                text = description,
                style = Theme.brockmann.body.s.medium,
                color = Theme.v2.colors.text.tertiary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

enum class ErrorState {
    CRITICAL,
    WARNING,
}

@Preview
@Composable
fun WarningErrorViewPreview() {
    ErrorView(
        title = "Something went wrong",
        description = "Please try again later",
        errorState = ErrorState.WARNING,
        buttonUiModel =
            ErrorViewButtonUiModel(text = stringResource(R.string.try_again), onClick = {}),
    )
}

@Preview
@Composable
fun WarningErrorViewPreview2() {
    ErrorView(
        title = "Something went wrong",
        errorState = ErrorState.CRITICAL,
        buttonUiModel =
            ErrorViewButtonUiModel(text = stringResource(R.string.try_again), onClick = {}),
    )
}

@Preview
@Composable
fun WarningErrorViewPreview3() {
    ErrorView(
        title = "Something went wrong",
        errorState = ErrorState.CRITICAL,
        buttonUiModel = null,
    )
}
