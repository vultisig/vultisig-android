package com.vultisig.wallet.ui.components.selectors

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.logo
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.clickOnce
import com.vultisig.wallet.ui.theme.Theme


@Composable
internal fun ChainSelector(
    chain: Chain,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
    ) {

        Image(
            painter = painterResource(chain.logo),
            contentDescription = null,
            modifier = Modifier
                .size(16.dp),
        )

        UiSpacer(4.dp)

        Text(
            text = chain.raw,
            style = Theme.brockmann.supplementary.caption,
            color = Theme.v2.colors.text.primary,
        )

        Image(
            painter = painterResource(R.drawable.ic_chevron_down_small),
            contentDescription = null,
            modifier = Modifier
                .size(16.dp),
        )
    }
}

@Composable
internal fun ChainSelector(
    title: String,
    chain: Chain,
    onClick: () -> Unit,
    onDragStart: (Offset) -> Unit = {},
    onDrag: (Offset) -> Unit = {},
    onDragEnd: () -> Unit = {},
    onDragCancel: () -> Unit = {},
    onLongPressStarted: (Offset) -> Unit = {},
) {

    var fieldPosition by remember { mutableStateOf(Offset.Zero) }

    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .onGloballyPositioned { coordinates ->
                fieldPosition = coordinates.positionInWindow()
            }
            .clickOnce(onClick = onClick)
            .pointerInput(Unit) {

                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        val screenPosition = Offset(
                            x = fieldPosition.x + offset.x,
                            y = fieldPosition.y + offset.y
                        )
                        onDragStart(screenPosition)
                        onLongPressStarted(screenPosition)
                    },
                    onDrag = { change: PointerInputChange, _ ->
                        val localPos = change.position
                        val screenPos = Offset(
                            x = fieldPosition.x + localPos.x,
                            y = fieldPosition.y + localPos.y
                        )
                        onDrag(screenPos)
                        change.consume()
                    },
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragCancel
                )
            }
    ) {
        Text(
            text = title,
            style = Theme.brockmann.supplementary.caption,
            color = Theme.v2.colors.text.tertiary,
        )

        ChainSelector(
            chain = chain,
        )
    }
}