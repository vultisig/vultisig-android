package com.vultisig.wallet.ui.components.v2.bottomsheets

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.ui.theme.Theme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DottyBottomSheet(
    onExpand: () -> Unit = {},
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {

    // Nothing drives expand() here on purpose: ModalBottomSheet already animates to Expanded on
    // first composition, and skipPartiallyExpanded leaves nowhere else to settle. Expanding on the
    // same frame short-circuits that animation and the sheet lands at its target without sliding.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(sheetState.currentValue) {
        if (sheetState.currentValue != SheetValue.Hidden) {
            onExpand()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        containerColor = Color.Transparent,
        scrimColor = Color.Black.copy(alpha = 0.32f),
        shape = RectangleShape,
        content = {
            Box {
                Column(
                    modifier = Modifier.fillMaxWidth().dottySurface(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    content = content,
                )
                DragHandler(
                    modifier = Modifier.padding(top = 8.dp).align(Alignment.TopCenter),
                    color = Theme.v2.colors.vibrant.primary,
                )
            }
        },
    )
}
