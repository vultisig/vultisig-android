package com.vultisig.wallet.ui.components.errors

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.vultisig.wallet.ui.components.UiIcon
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
    rawError: String? = null,
    onBack: (() -> Unit)? = null,
) {
    var showRawError by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize().background(Theme.v2.colors.backgrounds.primary)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            UiSpacer(weight = 1f)

            ErrorWaves(title = title, description = description, errorState = errorState)

            if (rawError != null) {
                UiSpacer(37.dp)
                ShowExactErrorRow(onClick = { showRawError = true })
            }

            UiSpacer(weight = 1f)

            secondaryButtonUiModel?.let {
                VsButton(
                    variant = VsButtonVariant.Secondary,
                    label = it.text,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = it.onClick,
                )
                UiSpacer(12.dp)
            }

            buttonUiModel?.let {
                VsButton(
                    variant = VsButtonVariant.CTA,
                    label = it.text,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = it.onClick,
                )
            }

            UiSpacer(size = 24.dp)
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

    if (showRawError && rawError != null) {
        ErrorMessageBottomSheet(rawError = rawError, onDismissRequest = { showRawError = false })
    }
}

@Composable
private fun ShowExactErrorRow(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(
                    color = Theme.v2.colors.backgrounds.surface1,
                    shape = RoundedCornerShape(16.dp),
                )
                .border(
                    width = 1.dp,
                    color = Theme.v2.colors.border.light,
                    shape = RoundedCornerShape(16.dp),
                )
                .clickable(onClick = onClick)
                .padding(20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.error_message_show_exact),
            style = Theme.brockmann.body.s.medium,
            color = Theme.v2.colors.text.secondary,
        )
        UiIcon(
            drawableResId = R.drawable.ic_chevron_down_small,
            size = 24.dp,
            tint = Theme.v2.colors.text.secondary,
        )
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
        modifier = modifier,
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
            style = Theme.brockmann.headings.title3,
            color = Theme.v2.colors.text.primary,
            textAlign = TextAlign.Center,
        )

        description?.let {
            UiSpacer(8.dp)
            Text(
                text = description,
                style = Theme.brockmann.supplementary.footnote,
                color = Theme.v2.colors.text.secondary,
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
private fun CriticalErrorViewPreview() {
    ErrorView(
        title = "Transaction failed",
        description =
            "One of your devices didn't respond in time. Check your connection and try again.",
        errorState = ErrorState.CRITICAL,
        rawError = "javax.crypto.AEADBadTagException: error:1e000065:Cipher functions",
        buttonUiModel = ErrorViewButtonUiModel(text = "Try Again", onClick = {}),
        onBack = {},
    )
}

@Preview
@Composable
private fun WarningErrorViewPreview() {
    ErrorView(
        title = "Insufficient funds",
        description = "Insufficient funds to execute the swap. Please fund the wallet.",
        errorState = ErrorState.WARNING,
        buttonUiModel = ErrorViewButtonUiModel(text = "Try Again", onClick = {}),
    )
}
