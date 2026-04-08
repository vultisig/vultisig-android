package com.vultisig.wallet.ui.screens.send

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.library.UiPlaceholderLoader
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun EstimatedNetworkFee(
    tokenGas: String,
    fiatGas: String,
    isLoading: Boolean = false,
    title: String = stringResource(R.string.send_form_est_network_fee),
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title,
            style = Theme.brockmann.supplementary.footnote,
            color = Theme.v2.colors.text.tertiary,
        )

        Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(1f)) {
            if (isLoading) {
                UiPlaceholderLoader(modifier = Modifier.height(20.dp).width(150.dp))

                UiSpacer(6.dp)

                UiPlaceholderLoader(modifier = Modifier.height(20.dp).width(150.dp))
            } else {
                Text(
                    text = tokenGas,
                    style = Theme.brockmann.body.s.medium,
                    color = Theme.v2.colors.text.primary,
                )

                Text(
                    text = fiatGas,
                    style = Theme.brockmann.body.s.medium,
                    color = Theme.v2.colors.text.tertiary,
                )
            }
        }
    }
}

@Composable
internal fun FadingHorizontalDivider(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    brush =
                        Brush.horizontalGradient(
                            colors =
                                listOf(
                                    Theme.v2.colors.backgrounds.secondary.copy(alpha = 0f),
                                    Theme.v2.colors.backgrounds.gradientMid,
                                    Theme.v2.colors.backgrounds.secondary.copy(alpha = 0f),
                                ),
                            startX = 0f,
                            endX = Float.POSITIVE_INFINITY,
                            tileMode = TileMode.Clamp,
                        )
                )
    )
}

@Composable
internal fun FoldableSection(
    expanded: Boolean = false,
    complete: Boolean = false,
    completeTitleContent: (@Composable RowScope.() -> Unit)? = null,
    expandedTitleActions: (@Composable RowScope.() -> Unit)? = null,
    onToggle: () -> Unit = {},
    title: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier =
            Modifier.border(
                width = 1.dp,
                color = Theme.v2.colors.border.normal,
                shape = RoundedCornerShape(12.dp),
            )
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable(onClick = onToggle).padding(all = 16.dp).fillMaxWidth(),
        ) {
            Text(
                text = title,
                color = Theme.v2.colors.text.primary,
                style = Theme.brockmann.body.s.medium,
            )

            if (expanded) {
                expandedTitleActions?.invoke(this)
            } else {
                if (complete) {
                    completeTitleContent?.invoke(this)

                    UiIcon(
                        drawableResId = R.drawable.ic_check,
                        size = 16.dp,
                        tint = Theme.v2.colors.alerts.success,
                    )

                    UiSpacer(1.dp)

                    UiIcon(
                        drawableResId = R.drawable.pencil,
                        size = 16.dp,
                        tint = Theme.v2.colors.text.primary,
                    )
                }
            }
        }

        AnimatedVisibility(visible = expanded) {
            Column {
                FadingHorizontalDivider(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                )
                content()
            }
        }
    }
}
