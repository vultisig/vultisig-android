package com.vultisig.wallet.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.ui.theme.Theme


@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UiCustomContentAlertDialog(
    modifier: Modifier = Modifier,
    onDismissRequest: () -> Unit = {},
    content: @Composable () -> Unit
) {
    val appColor = Theme.v2.colors
    BasicAlertDialog(
        modifier = modifier,
        onDismissRequest = onDismissRequest
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(size = 16.dp))
                .background(appColor.backgrounds.tertiary_2)
                .border(
                    width = 1.dp,
                    color = appColor.neutrals.n50,
                    shape = RoundedCornerShape(size = 16.dp)
                )
                .padding(16.dp),
            content = { content() }
        )
    }
}


