@file:OptIn(ExperimentalMaterial3Api::class)

package com.vultisig.wallet.ui.components.bottomsheet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.buttons.VsCircleButton
import com.vultisig.wallet.ui.components.buttons.VsCircleButtonSize
import com.vultisig.wallet.ui.components.buttons.VsCircleButtonType
import com.vultisig.wallet.ui.theme.Theme
import kotlinx.coroutines.launch

object VsBottomSheet {

    @Composable
    fun DragHandle() {
        Box(
            modifier = Modifier
                .padding(
                    all = 12.dp,
                )
                .size(
                    width = 36.dp,
                    height = 6.dp,
                )
                .background(
                    color = Theme.colors.neutrals.n600,
                    shape = RoundedCornerShape(16.dp)
                )
        )
    }

}

@Composable
fun VsModalBottomSheet(
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
    )
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        sheetState = sheetState,
        scrimColor = Theme.colors.neutrals.n900.copy(alpha = 0.8f),
        dragHandle = {
            VsBottomSheet.DragHandle()
        },
        containerColor = Theme.colors.backgrounds.primary,
        modifier = Modifier
            .statusBarsPadding(),
        onDismissRequest = {
            scope.launch {
                sheetState.hide()
                onDismissRequest()
            }
        },
        content = content,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VsBottomSheet(
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
        containerColor = Color.Companion.Transparent,
        shape = RectangleShape,
        content = {
            Box {
                Column(
                    modifier = Modifier.Companion
                        .fillMaxWidth()
                        .clip(
                            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
                        )
                        .background(Theme.colors.backgrounds.primary)
                        .padding(
                            all = 16.dp
                        )
                ) {
                    TopRow(leftAction, title, rightAction)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Theme.colors.backgrounds.primary),
                        content = content
                    )
                }
                DragHandler(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .align(Alignment.Companion.TopCenter)
                )
            }

        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VsBottomSheet(
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
    ),
    onDismissRequest: () -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    ModalBottomSheet(
        modifier = modifier.statusBarsPadding(),
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        dragHandle = null,
        containerColor = Color.Companion.Transparent,
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
                        .background(Theme.colors.backgrounds.primary),
                    content = content
                )
                DragHandler(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .align(Alignment.Companion.TopCenter)
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
        verticalAlignment = Alignment.Companion.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {

        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Companion.CenterStart
        ) {
            leftAction?.invoke()
        }

        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Companion.Center,
        ) {
            title?.let {
                Text(
                    text = it,
                    style = Theme.brockmann.headings.title3,
                    color = Theme.colors.neutrals.n100,
                )
            }
        }

        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Companion.CenterEnd
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
            .background(Theme.colors.vibrant.primary)

    )
}


@Composable
@Preview
internal fun VsBottomSheetPreview() {
    VsBottomSheet(
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
                color = Theme.colors.neutrals.n100,
                style = Theme.brockmann.headings.title3,
            )
        }
    )
}