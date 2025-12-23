package com.vultisig.wallet.ui.components.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
internal fun UiCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Surface(
        color = if (checked) {
            Theme.colors.backgrounds.teal
        } else {
            Theme.colors.border.normal
        },
        shape = RoundedCornerShape(2.dp),
        modifier = Modifier
            .size(24.dp)
            .clickable(onClick = { onCheckedChange(!checked) })
    ) {
        if (checked) {
            Icon(
                painter = painterResource(id = R.drawable.check),
                contentDescription = null,
                tint = Theme.colors.neutrals.n100,
            )
        }
    }
}

@Preview
@Composable
private fun UiCheckboxPreview() {
    Row {
        UiCheckbox(
            checked = true,
            onCheckedChange = {}
        )

        UiCheckbox(
            checked = false,
            onCheckedChange = {}
        )
    }
}