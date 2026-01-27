package com.vultisig.wallet.ui.components.containers

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun VsContainer(
    modifier: Modifier = Modifier,
    type: VsContainerType = VsContainerType.PRIMARY,
    borderType: VsContainerBorderType = VsContainerBorderType.Borderless,
    vsContainerCornerType: VsContainerCornerType = VsContainerCornerType.RoundedVsContainerCornerShape(),
    content: @Composable () -> Unit,
) {
    val containerColor = when (type) {
        VsContainerType.PRIMARY -> Theme.colors.backgrounds.primary
        VsContainerType.SECONDARY -> Theme.colors.backgrounds.secondary
        VsContainerType.TERTIARY -> Theme.colors.backgrounds.tertiary_2
    }

    val borderColor = when (borderType) {
        VsContainerBorderType.Borderless -> Color.Transparent
        is VsContainerBorderType.Bordered -> borderType.color
    }

    val shape = when (vsContainerCornerType) {
        VsContainerCornerType.Circular -> CircleShape
        is VsContainerCornerType.RoundedVsContainerCornerShape -> RoundedCornerShape(
            size = vsContainerCornerType.size,
        )
    }

    val containerColorAnimated by animateColorAsState(containerColor)
    val borderColorAnimated by animateColorAsState(borderColor)


    Card(
        modifier = modifier,
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = containerColorAnimated,
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp
        ),
        border = BorderStroke(
            width = 1.dp,
            color = borderColorAnimated,
        )
    ) {
        content()
    }
}

@Preview
@Composable
private fun PreviewVsContainer() {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        VsContainer(
            type = VsContainerType.PRIMARY,
            borderType = VsContainerBorderType.Bordered()
        ) {
            Text(
                text = "This is primary bordered container",
                color = Theme.colors.text.primary,
                style = Theme.brockmann.body.s.medium,
                modifier = Modifier.padding(16.dp)
            )
        }
        VsContainer(
            type = VsContainerType.PRIMARY,
            borderType = VsContainerBorderType.Borderless
        ) {
            Text(
                text = "This is primary borderless container",
                color = Theme.colors.text.primary,
                style = Theme.brockmann.body.s.medium,
                modifier = Modifier.padding(16.dp)
            )
        }

        VsContainer(
            type = VsContainerType.PRIMARY,
            borderType = VsContainerBorderType.Bordered(Theme.colors.alerts.info)
        ) {
            Text(
                text = "This is primary custom border color container",
                color = Theme.colors.text.primary,
                style = Theme.brockmann.body.s.medium,
                modifier = Modifier.padding(16.dp)
            )
        }
        VsContainer(
            type = VsContainerType.SECONDARY,
            borderType = VsContainerBorderType.Bordered()
        ) {
            Text(
                text = "This is secondary bordered container",
                color = Theme.colors.text.primary,
                style = Theme.brockmann.body.s.medium,
                modifier = Modifier.padding(16.dp)
            )
        }
        VsContainer(
            type = VsContainerType.SECONDARY,
            borderType = VsContainerBorderType.Borderless
        ) {
            Text(
                text = "This is secondary borderless container",
                color = Theme.colors.text.primary,
                style = Theme.brockmann.body.s.medium,
                modifier = Modifier.padding(16.dp)
            )
        }
        VsContainer(
            type = VsContainerType.SECONDARY,
            borderType = VsContainerBorderType.Bordered(Theme.colors.alerts.info)
        ) {
            Text(
                text = "This is secondary custom border color container",
                color = Theme.colors.text.primary,
                style = Theme.brockmann.body.s.medium,
                modifier = Modifier.padding(16.dp)
            )
        }

    }

}

