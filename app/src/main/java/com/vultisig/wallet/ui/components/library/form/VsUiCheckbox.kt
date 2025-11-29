package com.vultisig.wallet.ui.components.library.form

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun VsUiCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Surface(
        color = if (checked) {
            Theme.v2.colors.backgrounds.success
        } else {
            Theme.v2.colors.backgrounds.secondary
        },
        shape = CircleShape,
        border = BorderStroke(1.dp,
            if (checked) {
                Theme.v2.colors.alerts.success
            } else {
                Theme.v2.colors.border.normal
            }
        ),
        modifier = Modifier
            .size(24.dp)
            .clickable(onClick = { onCheckedChange(!checked) })
    ) {
        if (checked) {
            Icon(
                modifier = Modifier.size(16.dp),
                painter = painterResource(id = R.drawable.ic_check),
                contentDescription = null,
                tint = Theme.v2.colors.alerts.success,
            )
        }
    }
}

@Preview
@Composable
private fun VsUiCheckboxPreview() {
    Row {
        VsUiCheckbox(
            checked = true,
            onCheckedChange = {}
        )

        VsUiCheckbox(
            checked = false,
            onCheckedChange = {}
        )
    }
}