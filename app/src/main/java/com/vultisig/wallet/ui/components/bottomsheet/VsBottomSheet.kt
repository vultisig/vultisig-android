@file:OptIn(ExperimentalMaterial3Api::class)

package com.vultisig.wallet.ui.components.bottomsheet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
                    color = Theme.v2.colors.neutrals.n600,
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
        scrimColor = Theme.v2.colors.neutrals.n900.copy(alpha = 0.8f),
        dragHandle = {
            VsBottomSheet.DragHandle()
        },
        containerColor = Theme.v2.colors.backgrounds.primary,
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