package com.vultisig.wallet.ui.components.v2.containers

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.ui.theme.Colors
import com.vultisig.wallet.ui.theme.Theme

internal enum class ContainerType {
    PRIMARY,
    SECONDARY,
    TERTIARY,
}

internal sealed interface ContainerBorderType {
    object Borderless : ContainerBorderType
    data class Bordered(val color: Color = Colors.Default.borders.light) : ContainerBorderType
}

internal sealed interface CornerType {
    object Circular : CornerType
    data class RoundedCornerShape(val size: Dp = 12.dp) : CornerType
}

@Composable
internal fun V2Container(
    modifier: Modifier = Modifier,
    type: ContainerType = ContainerType.PRIMARY,
    borderType: ContainerBorderType = ContainerBorderType.Borderless,
    cornerType: CornerType = CornerType.RoundedCornerShape(),
    content: @Composable () -> Unit,
) {
    val containerColor = when (type) {
        ContainerType.PRIMARY -> Theme.colors.backgrounds.primary
        ContainerType.SECONDARY -> Theme.colors.backgrounds.secondary
        ContainerType.TERTIARY -> Theme.colors.backgrounds.tertiary
    }

    val borderColor = when (borderType) {
        ContainerBorderType.Borderless -> Color.Transparent
        is ContainerBorderType.Bordered -> borderType.color
    }

    val shape = when (cornerType) {
        CornerType.Circular -> CircleShape
        is CornerType.RoundedCornerShape -> RoundedCornerShape(
            size = cornerType.size,
        )
    }

    Card(
        modifier = modifier,
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp
        ),
        border = BorderStroke(
            width = 1.dp,
            color = borderColor,
        )
    ) {
        content()
    }
}

@Preview
@Composable
private fun PreviewV2Container() {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        V2Container(
            type = ContainerType.PRIMARY,
            borderType = ContainerBorderType.Bordered()
        ) {
            Text(
                text = "This is primary bordered container",
                color = Theme.colors.text.primary,
                style = Theme.brockmann.body.s.medium,
                modifier = Modifier.padding(16.dp)
            )
        }
        V2Container(
            type = ContainerType.PRIMARY,
            borderType = ContainerBorderType.Borderless
        ) {
            Text(
                text = "This is primary borderless container",
                color = Theme.colors.text.primary,
                style = Theme.brockmann.body.s.medium,
                modifier = Modifier.padding(16.dp)
            )
        }

        V2Container(
            type = ContainerType.PRIMARY,
            borderType = ContainerBorderType.Bordered(Colors.Default.alerts.info)
        ) {
            Text(
                text = "This is primary custom border color container",
                color = Theme.colors.text.primary,
                style = Theme.brockmann.body.s.medium,
                modifier = Modifier.padding(16.dp)
            )
        }
        V2Container(
            type = ContainerType.SECONDARY,
            borderType = ContainerBorderType.Bordered()
        ) {
            Text(
                text = "This is secondary bordered container",
                color = Theme.colors.text.primary,
                style = Theme.brockmann.body.s.medium,
                modifier = Modifier.padding(16.dp)
            )
        }
        V2Container(
            type = ContainerType.SECONDARY,
            borderType = ContainerBorderType.Borderless
        ) {
            Text(
                text = "This is secondary borderless container",
                color = Theme.colors.text.primary,
                style = Theme.brockmann.body.s.medium,
                modifier = Modifier.padding(16.dp)
            )
        }
        V2Container(
            type = ContainerType.SECONDARY,
            borderType = ContainerBorderType.Bordered(Colors.Default.alerts.info)
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

