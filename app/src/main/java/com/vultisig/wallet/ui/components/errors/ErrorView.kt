package com.vultisig.wallet.ui.components.errors

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun ErrorView(
    modifier: Modifier = Modifier,
    title: String,
    description: String? = null,
    errorState: ErrorState = ErrorState.WARNING,
    buttonText: String = stringResource(R.string.try_again),
    onButtonClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Theme.colors.backgrounds.primary)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.Companion.CenterHorizontally,
        verticalArrangement = Arrangement.Center,

        ) {
        val waveCircleColor = Theme.colors.border.light

        UiSpacer(
            weight = 1f
        )

        Image(
            imageVector = ImageVector.vectorResource(id = when(errorState){
                ErrorState.CRITICAL -> R.drawable.error_critical
                ErrorState.WARNING -> R.drawable.error_warning
            }),
            contentDescription = "Warning",
            modifier = Modifier
                .size(24.dp)
                .drawBehind {
                    drawCircle(
                        color = waveCircleColor,
                        radius = 37.dp.toPx(),
                        style = Stroke(width = 0.69f.dp.toPx())
                    )
                    drawCircle(
                        color = waveCircleColor,
                        radius = 68.dp.toPx(),
                        style = Stroke(width = 0.55.dp.toPx())
                    )
                    drawCircle(
                        color = waveCircleColor,
                        radius = 131.dp.toPx(),
                        style = Stroke(width = 0.69f.dp.toPx())
                    )
                }
        )

        UiSpacer(24.dp)

        Text(
            text = title,
            style = Theme.brockmann.headings.title2,
            color = when(errorState){
                ErrorState.CRITICAL -> Theme.colors.alerts.error
                ErrorState.WARNING -> Theme.colors.alerts.warning
            },
            textAlign = TextAlign.Center
        )


        description?.let {
            UiSpacer(12.dp)
            Text(
                text = description,
                style = Theme.brockmann.body.s.medium,
                color = Theme.colors.text.extraLight,
                textAlign = TextAlign.Center
            )
        }

        UiSpacer(30.dp)
        VsButton(
            variant = VsButtonVariant.Secondary,
            label = buttonText,
            modifier = Modifier.fillMaxWidth(),
            onClick = onButtonClick
        )

        UiSpacer(
            weight = 1f
        )

        AppVersionText()

        UiSpacer(
            size = 50.dp
        )
    }


}

enum class ErrorState {
    CRITICAL,
    WARNING
}

@Preview
@Composable
fun WarningErrorViewPreview() {
    ErrorView(
        title = "Something went wrong",
        description = "Please try again later",
        errorState = ErrorState.WARNING,
        onButtonClick = {},
    )
}

@Preview
@Composable
fun WarningErrorViewPreview2() {
    ErrorView(
        title = "Something went wrong",
        errorState = ErrorState.CRITICAL,
        onButtonClick = {},
    )
}