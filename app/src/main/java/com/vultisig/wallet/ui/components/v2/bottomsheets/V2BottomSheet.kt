package com.vultisig.wallet.ui.components.v2.bottomsheets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.v2.buttons.VsCircleButton
import com.vultisig.wallet.ui.components.v2.buttons.VsCircleButtonSize
import com.vultisig.wallet.ui.components.v2.buttons.VsCircleButtonType
import com.vultisig.wallet.ui.theme.Theme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun V2BottomSheet(
    modifier: Modifier = Modifier,
    onDismissRequest: () -> Unit = {},
    leftAction: (@Composable () -> Unit)? = null,
    rightAction: (@Composable () -> Unit)? = null,
    title: String? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
    )
    ModalBottomSheet(
        modifier = modifier.statusBarsPadding(),
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        dragHandle = null,
        containerColor = Color.Transparent,
        shape = RectangleShape,
        content = {
            Box {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(
                            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
                        )
                        .background(Theme.v2.colors.backgrounds.primary)
                        .padding(
                            all = 16.dp
                        )
                ) {
                    TopRow(leftAction, title, rightAction)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Theme.v2.colors.backgrounds.primary),
                        content = content
                    )
                }
                DragHandler(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .align(Alignment.TopCenter)
                )
            }

        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun V2BottomSheet(
    modifier: Modifier = Modifier,
    onDismissRequest: () -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
    )
    ModalBottomSheet(
        modifier = modifier.statusBarsPadding(),
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        dragHandle = null,
        containerColor = Color.Transparent,
        shape = RectangleShape,
        content = {
            Box(
                modifier = Modifier
                    .clip(
                        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
                    )
            ) {

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Theme.v2.colors.backgrounds.primary),
                    content = content
                )
                DragHandler(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .align(Alignment.TopCenter)
                )
            }

        }
    )
}

@Composable
private fun TopRow(
    leftAction: @Composable (() -> Unit)?,
    title: String?,
    rightAction: @Composable (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 36.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {

        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart
        ) {
            leftAction?.invoke()
        }

        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            title?.let {
                Text(
                    text = it,
                    style = Theme.brockmann.headings.title3,
                    color = Theme.v2.colors.neutrals.n100,
                )
            }
        }

        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterEnd
        ) {
            rightAction?.invoke()
        }
    }
}

@Composable
internal fun DragHandler(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .width(36.dp)
            .height(5.dp)
            .clip(CircleShape)
            .background(Theme.v2.colors.vibrant.primary)

    )
}


@Composable
@Preview
internal fun V2BottomSheetPreview() {
    V2BottomSheet(
        leftAction = {
            VsCircleButton(
                onClick = {},
                icon = R.drawable.x,
                size = VsCircleButtonSize.Small,
                type = VsCircleButtonType.Tertiary,
            )
        },
        rightAction = {
            VsCircleButton(
                onClick = {},
                icon = R.drawable.x,
                size = VsCircleButtonSize.Small,
                type = VsCircleButtonType.Tertiary,
            )
        },
        title = "New Folder",
        content = {
            Text(
                text = "Add Folder",
                color = Theme.v2.colors.neutrals.n100,
                style = Theme.brockmann.headings.title3,
            )
        }
    )
}