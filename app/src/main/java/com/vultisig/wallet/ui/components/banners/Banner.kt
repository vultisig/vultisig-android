package com.vultisig.wallet.ui.components.banners

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.theme.Theme

internal enum class BannerVariant {
    Warning,
    Info,
    Error,
    Success
}

@Composable
internal fun Banner(
    text: String,
    modifier: Modifier = Modifier,
    variant: BannerVariant = BannerVariant.Info,
    onCloseClick: () -> Unit,
) = Banner(
    text = text,
    modifier = modifier,
    variant = variant,
    actions = {
        Icon(
            painter = painterResource(R.drawable.x),
            contentDescription = null,
            tint = Theme.colors.text.primary,
            modifier = Modifier
                .size(24.dp)
                .background(
                    color = Theme.colors.backgrounds.tertiary_2,
                    shape = CircleShape,
                )
                .padding(8.dp)
                .clickable(onClick = onCloseClick)
        )
    },
)

@Composable
internal fun Banner(
    text: String,
    modifier: Modifier = Modifier,
    variant: BannerVariant = BannerVariant.Info,
    @SuppressLint("ComposableLambdaParameterNaming")
    actions: (@Composable () -> Unit)? = null,
) {
    val shape = RoundedCornerShape(12.dp)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .background(
                color = when (variant) {
                    BannerVariant.Warning -> Theme.colors.backgrounds.alert
                    BannerVariant.Info -> Theme.colors.backgrounds.secondary
                    BannerVariant.Error -> Theme.colors.backgrounds.error
                    BannerVariant.Success -> Theme.colors.backgrounds.success
                },
                shape = shape,
            )
            .border(
                width = 1.dp,
                color = when (variant) {
                    BannerVariant.Warning -> Color(0x40FFC25C)
                    BannerVariant.Info -> Theme.colors.border.light
                    BannerVariant.Error -> Color(0x40FF5C5C)
                    BannerVariant.Success -> Color(0x4013C89D)
                },
                shape = shape,
            )
            .padding(
                all = 16.dp,
            )
    ) {
        val contentColor = when (variant) {
            BannerVariant.Warning -> Theme.colors.alerts.warning
            BannerVariant.Info -> Theme.colors.text.light
            BannerVariant.Error -> Theme.colors.alerts.error
            BannerVariant.Success -> Theme.colors.alerts.success
        }

        UiIcon(
            drawableResId = R.drawable.ic_info,
            size = 16.dp,
            tint = contentColor,
        )

        Text(
            text = text,
            color = contentColor,
            style = Theme.brockmann.supplementary.footnote,
            modifier = Modifier.weight(1f)
        )

        actions?.invoke()
    }
}

@Preview
@Composable
private fun BannerPreview() {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Banner(
            text = "This is a warning",
            variant = BannerVariant.Warning
        )
        Banner(
            text = "This is info",
            variant = BannerVariant.Info
        )
        Banner(
            text = "This is an error",
            variant = BannerVariant.Error
        )
        Banner(
            text = "This is a success",
            variant = BannerVariant.Success
        )
    }
}